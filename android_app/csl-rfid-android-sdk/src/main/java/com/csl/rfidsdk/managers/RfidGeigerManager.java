package com.csl.rfidsdk.managers;

import com.csl.cslibrary4a.CsLibrary4A;
import com.csl.cslibrary4a.RfidReaderChipData;
import com.csl.rfidsdk.RfidManagerBuilder;
import com.csl.rfidsdk.callbacks.RfidGeigerCallback;
import com.csl.rfidsdk.config.RfidStopReason;
import com.csl.rfidsdk.internal.SdkBridge;
import com.csl.rfidsdk.internal.ThreadManager;
import com.csl.rfidsdk.models.RfidConfiguration;
import com.csl.rfidsdk.models.RfidError;
import com.csl.rfidsdk.models.RfidGeigerStats;

/**
 * Manages RFID Geiger search (tag locating) operations
 * Implementation based on InventoryRfidSearchFragment.java
 */
public class RfidGeigerManager {
    private final SdkBridge sdkBridge;
    private final ThreadManager threadManager;
    private final RfidManagerBuilder.LoggerCallback logger;

    private boolean searching = false;
    private RfidGeigerCallback callback;
    private RfidConfiguration configuration;

    private String targetEpc;
    private int memoryBank;
    private double currentRssi = -90.0;
    private double peakRssi = -90.0;
    private int readCount = 0;
    private long startTime = 0;
    private Runnable searchPollRunnable;

    public RfidGeigerManager(SdkBridge sdkBridge, ThreadManager threadManager,
                             RfidManagerBuilder.LoggerCallback logger) {
        this.sdkBridge = sdkBridge;
        this.threadManager = threadManager;
        this.logger = logger;
    }

    /**
     * Start Geiger search for a specific tag
     * Implementation based on InventoryRfidSearchFragment.java line 351-356
     *
     * @param targetEpc The EPC/TID/User data to search for
     * @param memoryBank The memory bank (1=EPC, 2=TID, 3=User)
     * @param callback Callback for search updates
     */
    public void startGeigerSearch(String targetEpc, int memoryBank, RfidGeigerCallback callback) {
        this.callback = callback;
        this.targetEpc = targetEpc;
        this.memoryBank = memoryBank;
        this.searching = true;
        this.startTime = System.currentTimeMillis();
        this.readCount = 0;
        this.peakRssi = -90.0;
        this.currentRssi = -90.0;

        if (callback != null) {
            threadManager.executeOnMain(callback::onSearchStarted);
        }

        threadManager.executeOnBackground(() -> {
            try {
                CsLibrary4A sdk = sdkBridge.getSdk();

                // Get power level from configuration (default 300)
                int powerLevel = 300;
                if (configuration != null && configuration.getPowerLevel() >= 0) {
                    powerLevel = configuration.getPowerLevel();
                }

                // Set selected tag for search (InventoryRfidSearchFragment.java line 351)
                boolean selectSuccess = sdk.setSelectedTag(targetEpc, memoryBank, powerLevel);

                if (!selectSuccess) {
                    searching = false;
                    if (callback != null) {
                        threadManager.executeOnMain(() ->
                                callback.onSearchError(new RfidError(
                                        "Failed to set selected tag for search",
                                        RfidError.ErrorType.INVENTORY_FAILED
                                ))
                        );
                    }
                    return;
                }

                log("Selected tag set: " + targetEpc + " (bank: " + memoryBank + ", power: " + powerLevel + ")");

                // Start search operation (InventoryRfidSearchFragment.java line 355)
                boolean started = sdk.startOperation(
                        RfidReaderChipData.OperationTypes.TAG_SEARCHING
                );

                if (!started) {
                    searching = false;
                    sdk.restoreAfterTagSelect(); // Restore settings
                    if (callback != null) {
                        threadManager.executeOnMain(() ->
                                callback.onSearchError(new RfidError(
                                        "Failed to start search operation",
                                        RfidError.ErrorType.INVENTORY_FAILED
                                ))
                        );
                    }
                    return;
                }

                log("Geiger search started for: " + targetEpc);

                // Poll for tag reads (similar to inventory polling)
                searchPollRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (!searching) return;

                        try {
                            // Check connection
                            if (!sdk.isBleConnected()) {
                                searching = false;
                                sdk.restoreAfterTagSelect();
                                if (callback != null) {
                                    threadManager.executeOnMain(() ->
                                            callback.onSearchError(new RfidError(
                                                    "Connection lost during search",
                                                    RfidError.ErrorType.CONNECTION_LOST
                                            ))
                                    );
                                }
                                return;
                            }

                            // Poll for RFID event data
                            RfidReaderChipData.Rx000pkgData tagData = sdk.onRFIDEvent();

                            if (tagData != null && sdk.mrfidToWriteSize() == 0) {
                                processSearchData(tagData);
                            }

                            // Continue polling if still searching
                            if (searching) {
                                threadManager.executeOnBackground(this);
                            }

                            // Small delay to reduce CPU usage
                            Thread.sleep(10);
                        } catch (Exception e) {
                            log("Error during search polling: " + e.getMessage());
                            if (searching) {
                                threadManager.executeOnBackground(this);
                            }
                        }
                    }
                };

                // Start polling
                threadManager.executeOnBackground(searchPollRunnable);

            } catch (Exception e) {
                searching = false;
                if (callback != null) {
                    threadManager.executeOnMain(() ->
                            callback.onSearchError(new RfidError(
                                    "Search error: " + e.getMessage(),
                                    RfidError.ErrorType.INVENTORY_FAILED,
                                    e
                            ))
                    );
                }
            }
        });
    }

    /**
     * Process search data from SDK
     * Extracts RSSI and calculates proximity stats
     */
    private void processSearchData(RfidReaderChipData.Rx000pkgData tagData) {
        switch (tagData.responseType) {
            case TYPE_18K6C_INVENTORY:
            case TYPE_18K6C_INVENTORY_COMPACT:
                // Target tag found (only the selected tag will be returned during search)
                if (tagData.decodedError == null) {
                    readCount++;
                    currentRssi = tagData.decodedRssi;
                    // Update peak RSSI (highest = least negative = closest)
                    if (currentRssi > peakRssi) {
                        peakRssi = currentRssi;
                    }

                    // Calculate proximity percentage (0-100)
                    // RSSI range depends on reader type:
                    // - CS108 (get98XX=0): 30-70 dBuV (40 dBuV span)
                    // - CS710S (get98XX=2): 10-60 dBuV (50 dBuV span)
                    CsLibrary4A sdk = sdkBridge.getSdk();
                    int readerType = sdk.get98XX();
                    double minRssi, maxRssi;
                    if (readerType == 2) {
                        // CS710S reader (Atmel)
                        minRssi = 10.0;
                        maxRssi = 60.0;
                    } else {
                        // CS108 reader (SiliconLab) or default
                        minRssi = 30.0;
                        maxRssi = 70.0;
                    }
                    double normalizedRssi = (currentRssi - minRssi) / (maxRssi - minRssi);
                    int proximity = (int) Math.max(0, Math.min(100, normalizedRssi * 100));

                    // Build stats
                    RfidGeigerStats stats = new RfidGeigerStats.Builder()
                            .targetEpc(targetEpc)
                            .currentRssi(currentRssi)
                            .peakRssi(peakRssi)
                            .readCount(readCount)
                            .proximity(proximity)
                            .elapsedTimeMs(System.currentTimeMillis() - startTime)
                            .build();

                    // Callback with stats
                    if (callback != null) {
                        threadManager.executeOnMain(() -> callback.onProximityUpdate(stats));
                    }

                    log(String.format("Target found: RSSI=%.1f dBm, Peak=%.1f dBm, Proximity=%d%%, Count=%d",
                            currentRssi, peakRssi, proximity, readCount));
                } else {
                    log("Tag read error: " + tagData.decodedError);
                }
                break;

            case TYPE_ANTENNA_CYCLE_END:
                // Antenna cycle completed
                log("Antenna cycle completed");
                break;

            case TYPE_COMMAND_END:
                // Search operation ended
                searching = false;
                CsLibrary4A sdk = sdkBridge.getSdk();
                sdk.restoreAfterTagSelect(); // Restore settings

                if (tagData.decodedError != null) {
                    log("Search ended with error: " + tagData.decodedError);
                    if (callback != null) {
                        threadManager.executeOnMain(() ->
                                callback.onSearchError(new RfidError(
                                        "Search ended with error: " + tagData.decodedError,
                                        RfidError.ErrorType.INVENTORY_FAILED
                                ))
                        );
                    }
                } else {
                    log("Search completed successfully");
                    if (callback != null) {
                        threadManager.executeOnMain(() ->
                                callback.onSearchStopped(RfidStopReason.COMPLETED)
                        );
                    }
                }
                break;

            case TYPE_COMMAND_ABORT_RETURN:
                // Operation was aborted
                searching = false;
                sdk = sdkBridge.getSdk();
                sdk.restoreAfterTagSelect(); // Restore settings
                log("Search operation aborted");
                if (callback != null) {
                    threadManager.executeOnMain(() ->
                            callback.onSearchStopped(RfidStopReason.USER_STOPPED)
                    );
                }
                break;

            default:
                log("Received response type: " + tagData.responseType);
                break;
        }
    }

    /**
     * Stop Geiger search operation
     */
    public void stopGeigerSearch() {
        if (!searching) return;

        searching = false;

        // Check if thread manager is still running before submitting task
        if (threadManager.isShutdown()) {
            log("Cannot stop search - ThreadManager is shut down");
            return;
        }

        threadManager.executeOnBackground(() -> {
            try {
                CsLibrary4A sdk = sdkBridge.getSdk();
                sdk.abortOperation();
                sdk.restoreAfterTagSelect(); // Restore settings
                log("Geiger search stopped");
            } catch (Exception e) {
                log("Error stopping search: " + e.getMessage());
            }

            if (callback != null) {
                threadManager.executeOnMain(() ->
                        callback.onSearchStopped(RfidStopReason.USER_STOPPED)
                );
            }
        });
    }

    /**
     * Cleanup resources - stops search and clears callbacks
     * Call this before shutting down ThreadManager
     */
    public void cleanup() {
        this.searching = false;
        this.callback = null;
        this.targetEpc = null;
        log("Cleanup completed");
    }

    public boolean isSearching() {
        return searching;
    }

    public void applyConfiguration(RfidConfiguration configuration) {
        this.configuration = configuration;
        log("Configuration set for next search");
    }

    private void log(String message) {
        if (logger != null) {
            logger.log("GeigerManager: " + message);
        }
    }
}
