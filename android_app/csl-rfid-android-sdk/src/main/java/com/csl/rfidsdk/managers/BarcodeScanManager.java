package com.csl.rfidsdk.managers;

import com.csl.cslibrary4a.CsLibrary4A;
import com.csl.rfidsdk.RfidManagerBuilder;
import com.csl.rfidsdk.callbacks.BarcodeScanCallback;
import com.csl.rfidsdk.config.RfidStopReason;
import com.csl.rfidsdk.internal.SdkBridge;
import com.csl.rfidsdk.internal.ThreadManager;
import com.csl.rfidsdk.models.BarcodeData;
import com.csl.rfidsdk.models.BarcodeStats;
import com.csl.rfidsdk.models.RfidError;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages barcode scanning operations
 * Implementation based on InventoryBarcodeTask.java
 */
public class BarcodeScanManager {
    private final SdkBridge sdkBridge;
    private final ThreadManager threadManager;
    private final RfidManagerBuilder.LoggerCallback logger;

    private boolean scanning = false;
    private BarcodeScanCallback callback;

    private final Map<String, Integer> barcodeCounts = new HashMap<>();
    private int totalScans = 0;
    private long startTime = 0;
    private long lastUpdateTime = 0;
    private Runnable scanPollRunnable;

    public BarcodeScanManager(SdkBridge sdkBridge, ThreadManager threadManager,
                              RfidManagerBuilder.LoggerCallback logger) {
        this.sdkBridge = sdkBridge;
        this.threadManager = threadManager;
        this.logger = logger;
    }

    /**
     * Start barcode scanning operation
     * Implementation based on InventoryBarcodeTask.onPreExecute() and doInBackground()
     */
    public void startScan(BarcodeScanCallback callback) {
        this.callback = callback;
        this.scanning = true;
        this.startTime = System.currentTimeMillis();
        this.lastUpdateTime = this.startTime;
        this.totalScans = 0;
        this.barcodeCounts.clear();

        threadManager.executeOnBackground(() -> {
            try {
                CsLibrary4A sdk = sdkBridge.getSdk();

                // Get barcode prefix/suffix configuration (InventoryBarcodeFragment line 140)
                sdk.getBarcodePreSuffix();

                // Start barcode scanning (InventoryBarcodeTask line 50)
                boolean started = sdk.barcodeInventory(true);

                if (!started) {
                    scanning = false;
                    if (callback != null) {
                        threadManager.executeOnMain(() ->
                                callback.onScanError(new RfidError(
                                        "Failed to start barcode scanning",
                                        RfidError.ErrorType.SCAN_FAILED
                                ))
                        );
                    }
                    return;
                }

                log("Barcode scanning started");

                // Poll for barcode data (InventoryBarcodeTask line 58-98)
                scanPollRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (!scanning) return;

                        try {
                            // Check connection
                            if (!sdk.isBleConnected()) {
                                scanning = false;
                                sdk.barcodeInventory(false);
                                if (callback != null) {
                                    threadManager.executeOnMain(() ->
                                            callback.onScanError(new RfidError(
                                                    "Connection lost during scanning",
                                                    RfidError.ErrorType.CONNECTION_LOST
                                            ))
                                    );
                                }
                                return;
                            }

                            // Poll for barcode event data (InventoryBarcodeTask line 72)
                            byte[] barcodeBytes = sdk.onBarcodeEvent();

                            if (barcodeBytes != null && barcodeBytes.length > 0) {
                                // Convert bytes to string (InventoryBarcodeTask line 76)
                                String barcodeString = new String(barcodeBytes).trim();

                                if (barcodeString.length() > 0) {
                                    log("Barcode scanned: " + barcodeString);
                                    processBarcodeData(barcodeString);
                                }
                            }

                            // Send periodic updates every second
                            if (System.currentTimeMillis() - lastUpdateTime > 1000) {
                                lastUpdateTime = System.currentTimeMillis();
                                sendStatsUpdate();
                            }

                            // Continue polling if still scanning
                            if (scanning) {
                                threadManager.executeOnBackground(this);
                            }

                            // Small delay to reduce CPU usage (InventoryBarcodeTask uses tight loop)
                            Thread.sleep(10);
                        } catch (Exception e) {
                            log("Error during barcode scanning: " + e.getMessage());
                            if (scanning) {
                                threadManager.executeOnBackground(this);
                            }
                        }
                    }
                };

                // Start polling
                threadManager.executeOnBackground(scanPollRunnable);

            } catch (Exception e) {
                scanning = false;
                if (callback != null) {
                    threadManager.executeOnMain(() ->
                            callback.onScanError(new RfidError(
                                    "Barcode scanning error: " + e.getMessage(),
                                    RfidError.ErrorType.SCAN_FAILED,
                                    e
                            ))
                    );
                }
            }
        });
    }

    /**
     * Process scanned barcode data
     * Implementation based on InventoryBarcodeTask.onProgressUpdate()
     */
    private void processBarcodeData(String barcode) {
        // Check if barcode already exists (InventoryBarcodeTask line 118-132)
        Integer existingCount = barcodeCounts.get(barcode);

        if (existingCount != null) {
            // Update existing barcode count
            int newCount = existingCount + 1;
            barcodeCounts.put(barcode, newCount);
            totalScans++;

            // Callback with updated barcode
            if (callback != null) {
                BarcodeData barcodeData = new BarcodeData.Builder(barcode)
                        .count(newCount)
                        .timestamp(System.currentTimeMillis())
                        .build();

                threadManager.executeOnMain(() -> callback.onBarcodeScanned(barcodeData));
            }
        } else {
            // New barcode (InventoryBarcodeTask line 134-146)
            barcodeCounts.put(barcode, 1);
            totalScans++;

            // Callback with new barcode
            if (callback != null) {
                BarcodeData barcodeData = new BarcodeData.Builder(barcode)
                        .count(1)
                        .timestamp(System.currentTimeMillis())
                        .build();

                threadManager.executeOnMain(() -> callback.onBarcodeScanned(barcodeData));
            }
        }
    }

    /**
     * Send statistics update to callback
     */
    private void sendStatsUpdate() {
        if (callback != null) {
            BarcodeStats stats = new BarcodeStats.Builder()
                    .uniqueBarcodes(barcodeCounts.size())
                    .totalScans(totalScans)
                    .elapsedTimeMs(System.currentTimeMillis() - startTime)
                    .build();

            threadManager.executeOnMain(() -> callback.onScanUpdate(stats));
        }
    }

    /**
     * Stop barcode scanning operation
     * Implementation based on InventoryBarcodeTask.DeviceConnectTask4InventoryEnding()
     */
    public void stopScan() {
        if (!scanning) return;

        scanning = false;

        // Check if thread manager is still running before submitting task
        if (threadManager.isShutdown()) {
            log("Cannot stop scanning - ThreadManager is shut down");
            return;
        }

        threadManager.executeOnBackground(() -> {
            try {
                CsLibrary4A sdk = sdkBridge.getSdk();

                // Stop barcode scanning (InventoryBarcodeTask line 262)
                sdk.barcodeInventory(false);

                log("Barcode scanning stopped");

                // Send final stats update
                sendStatsUpdate();

                // Callback that scanning stopped
                if (callback != null) {
                    threadManager.executeOnMain(() ->
                            callback.onScanStopped(RfidStopReason.USER_STOPPED)
                    );
                }
            } catch (Exception e) {
                log("Error stopping barcode scanning: " + e.getMessage());
            }
        });
    }

    /**
     * Check if barcode module is available/enabled
     */
    public boolean isBarcodeAvailable() {
        try {
            CsLibrary4A sdk = sdkBridge.getSdk();
            return !sdk.isBarcodeFailure();
        } catch (Exception e) {
            log("Error checking barcode availability: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cleanup resources - stops scanning and clears callbacks
     * Call this before shutting down ThreadManager
     */
    public void cleanup() {
        this.scanning = false;
        this.callback = null;
        this.barcodeCounts.clear();
        log("Cleanup completed");
    }

    public boolean isScanning() {
        return scanning;
    }

    private void log(String message) {
        if (logger != null) {
            logger.log("BarcodeScanManager: " + message);
        }
    }
}
