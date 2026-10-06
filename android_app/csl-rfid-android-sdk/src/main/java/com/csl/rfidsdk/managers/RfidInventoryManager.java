package com.csl.rfidsdk.managers;

import com.csl.cslibrary4a.CsLibrary4A;
import com.csl.cslibrary4a.RfidReaderChipData;
import com.csl.rfidsdk.RfidManagerBuilder;
import com.csl.rfidsdk.callbacks.RfidInventoryCallback;
import com.csl.rfidsdk.config.RfidStopReason;
import com.csl.rfidsdk.internal.SdkBridge;
import com.csl.rfidsdk.internal.ThreadManager;
import com.csl.rfidsdk.models.RfidConfiguration;
import com.csl.rfidsdk.models.RfidError;
import com.csl.rfidsdk.models.RfidInventoryStats;
import com.csl.rfidsdk.models.RfidTag;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages RFID inventory (tag reading) operations
 * Implementation based on cslibrary4a-usage.md section 3.1-3.2
 */
public class RfidInventoryManager {
    private final SdkBridge sdkBridge;
    private final ThreadManager threadManager;
    private final RfidManagerBuilder.LoggerCallback logger;

    private boolean inventorying = false;
    private RfidInventoryCallback callback;
    private RfidInventoryCallback lastInventoryCallback;  // Saved for auto-inventory mode
    private RfidConfiguration configuration;

    private final Map<String, Integer> tagCounts = new HashMap<>();
    private int totalReads = 0;
    private long startTime = 0;
    private Runnable inventoryPollRunnable;

    public RfidInventoryManager(SdkBridge sdkBridge, ThreadManager threadManager,
                                 RfidManagerBuilder.LoggerCallback logger) {
        this.sdkBridge = sdkBridge;
        this.threadManager = threadManager;
        this.logger = logger;
    }

    /**
     * Start RFID inventory operation
     * Uses compact inventory mode as documented in cslibrary4a-usage.md line 381-383
     */
    public void startInventory(RfidInventoryCallback callback) {
        this.callback = callback;
        this.lastInventoryCallback = callback;  // Save for auto-inventory mode
        this.inventorying = true;
        this.startTime = System.currentTimeMillis();
        this.totalReads = 0;
        this.tagCounts.clear();

        threadManager.executeOnBackground(() -> {
            try {
                CsLibrary4A sdk = sdkBridge.getSdk();

                // Apply configuration if available
                if (configuration != null) {
                    applyConfigurationInternal(sdk);
                }

                // Start inventory operation (cslibrary4a-usage.md line 381-383)
                boolean started = sdk.startOperation(
                        RfidReaderChipData.OperationTypes.TAG_INVENTORY_COMPACT
                );

                if (!started) {
                    inventorying = false;
                    if (callback != null) {
                        threadManager.executeOnMain(() ->
                                callback.onInventoryError(new RfidError(
                                        "Failed to start inventory",
                                        RfidError.ErrorType.INVENTORY_FAILED
                                ))
                        );
                    }
                    return;
                }

                log("Inventory started");

                // Poll for tag data (cslibrary4a-usage.md line 422-487)
                inventoryPollRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (!inventorying) return;

                        try {
                            // Check connection
                            if (!sdk.isBleConnected()) {
                                inventorying = false;
                                if (callback != null) {
                                    threadManager.executeOnMain(() ->
                                            callback.onInventoryError(new RfidError(
                                                    "Connection lost during inventory",
                                                    RfidError.ErrorType.CONNECTION_LOST
                                            ))
                                    );
                                }
                                return;
                            }

                            // Poll for RFID event data
                            RfidReaderChipData.Rx000pkgData tagData = sdk.onRFIDEvent();

                            if (tagData != null && sdk.mrfidToWriteSize() == 0) {
                                processTagData(tagData);
                            }

                            // Continue polling if still inventorying
                            if (inventorying) {
                                threadManager.executeOnBackground(this);
                            }

                            // Small delay to reduce CPU usage
                            Thread.sleep(10);
                        } catch (Exception e) {
                            log("Error during inventory polling: " + e.getMessage());
                            if (inventorying) {
                                threadManager.executeOnBackground(this);
                            }
                        }
                    }
                };

                // Start polling
                threadManager.executeOnBackground(inventoryPollRunnable);

            } catch (Exception e) {
                inventorying = false;
                if (callback != null) {
                    threadManager.executeOnMain(() ->
                            callback.onInventoryError(new RfidError(
                                    "Inventory error: " + e.getMessage(),
                                    RfidError.ErrorType.INVENTORY_FAILED,
                                    e
                            ))
                    );
                }
            }
        });
    }

    /**
     * Process tag data from SDK
     * Based on cslibrary4a-usage.md line 429-481
     */
    private void processTagData(RfidReaderChipData.Rx000pkgData tagData) {
        switch (tagData.responseType) {
            case TYPE_18K6C_INVENTORY:
            case TYPE_18K6C_INVENTORY_COMPACT:
                // New tag data received (cslibrary4a-usage.md line 430-455)
                if (tagData.decodedError == null) {
                    // Successfully read tag
                    CsLibrary4A sdk = sdkBridge.getSdk();
                    String epc = sdk.byteArrayToString(tagData.decodedEpc);
                    double rssi = tagData.decodedRssi;
                    int phase = tagData.decodedPhase;
                    int channel = tagData.decodedChidx;
                    long timestamp = tagData.decodedTime;

                    // Track tag count
                    int count = tagCounts.getOrDefault(epc, 0) + 1;
                    tagCounts.put(epc, count);
                    totalReads++;

                    // Create RfidTag
                    RfidTag tag = new RfidTag.Builder(epc)
                            .rssi(rssi)
                            .count(count)
                            .phase(phase)
                            .channel(channel)
                            .timestamp(timestamp)
                            .build();

                    // Callback with tag
                    if (callback != null) {
                        threadManager.executeOnMain(() -> callback.onTagRead(tag));
                    }

                    // Callback with stats
                    if (callback != null) {
                        RfidInventoryStats stats = new RfidInventoryStats.Builder()
                                .uniqueTagCount(tagCounts.size())
                                .totalReads(totalReads)
                                .readsPerSecond(calculateReadsPerSecond())
                                .elapsedTimeMs(System.currentTimeMillis() - startTime)
                                .build();

                        threadManager.executeOnMain(() ->
                                callback.onInventoryRound(stats)
                        );
                    }

                    log("Tag read: " + epc + " RSSI: " + rssi + " Count: " + count);
                } else {
                    // Tag read error
                    log("Tag read error: " + tagData.decodedError);
                }
                break;

            case TYPE_ANTENNA_CYCLE_END:
                // Antenna cycle completed (cslibrary4a-usage.md line 457-461)
                log("Antenna cycle completed");
                break;

            case TYPE_COMMAND_END:
                // Inventory operation ended (cslibrary4a-usage.md line 463-470)
                inventorying = false;
                if (tagData.decodedError != null) {
                    log("Inventory ended with error: " + tagData.decodedError);
                    if (callback != null) {
                        threadManager.executeOnMain(() ->
                                callback.onInventoryError(new RfidError(
                                        "Inventory ended with error: " + tagData.decodedError,
                                        RfidError.ErrorType.INVENTORY_FAILED
                                ))
                        );
                    }
                } else {
                    log("Inventory completed successfully");
                    if (callback != null) {
                        threadManager.executeOnMain(() ->
                                callback.onInventoryStopped(RfidStopReason.COMPLETED)
                        );
                    }
                }
                break;

            case TYPE_COMMAND_ABORT_RETURN:
                // Operation was aborted (cslibrary4a-usage.md line 472-475)
                inventorying = false;
                log("Inventory operation aborted");
                if (callback != null) {
                    threadManager.executeOnMain(() ->
                            callback.onInventoryStopped(RfidStopReason.USER_STOPPED)
                    );
                }
                break;

            default:
                log("Received response type: " + tagData.responseType);
                break;
        }
    }

    /**
     * Stop inventory operation
     */
    public void stopInventory() {
        if (!inventorying) return;

        inventorying = false;

        // Check if thread manager is still running before submitting task
        if (threadManager.isShutdown()) {
            log("Cannot stop inventory - ThreadManager is shut down");
            return;
        }

        threadManager.executeOnBackground(() -> {
            try {
                CsLibrary4A sdk = sdkBridge.getSdk();
                sdk.abortOperation();
                log("Inventory stopped");
            } catch (Exception e) {
                log("Error stopping inventory: " + e.getMessage());
            }

            if (callback != null) {
                threadManager.executeOnMain(() ->
                        callback.onInventoryStopped(RfidStopReason.USER_STOPPED)
                );
            }
        });
    }

    /**
     * Cleanup resources - stops inventory and clears callbacks
     * Call this before shutting down ThreadManager
     */
    public void cleanup() {
        this.inventorying = false;
        this.callback = null;
        this.tagCounts.clear();
        log("Cleanup completed");
    }

    public boolean isInventorying() {
        return inventorying;
    }

    /**
     * Get the last inventory callback that was used.
     * Used by auto-inventory mode to restart inventory with the same callback.
     *
     * @return The last RfidInventoryCallback, or null if no inventory has been started
     */
    public RfidInventoryCallback getLastInventoryCallback() {
        return lastInventoryCallback;
    }

    public void applyConfiguration(RfidConfiguration configuration) {
        this.configuration = configuration;
        log("Configuration set for next inventory");
    }

    /**
     * Apply configuration to SDK
     * Configuration is applied before starting inventory
     */
    private void applyConfigurationInternal(CsLibrary4A sdk) {
        if (configuration == null) return;

        try {
            // Apply power setting
            if (configuration.getPowerLevel() >= 0) {
                sdk.setPowerLevel(configuration.getPowerLevel());
                log("Power level set to: " + configuration.getPowerLevel());
            }

            // Note: Session and Target configuration requires more complex SDK calls
            // involving multiple parameters. For now, these are left at SDK defaults.
            // Advanced configuration can be added by calling SDK methods directly.

            // Apply region/channel
            if (configuration.getRegion() != null) {
                // Region configuration requires specific SDK calls
                // This is typically set during initial setup
                log("Region: " + configuration.getRegion());
            }

        } catch (Exception e) {
            log("Error applying configuration: " + e.getMessage());
        }
    }

    private double calculateReadsPerSecond() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > 0) {
            return (totalReads * 1000.0) / elapsed;
        }
        return 0.0;
    }

    private void log(String message) {
        if (logger != null) {
            logger.log("InventoryManager: " + message);
        }
    }
}
