package com.csl.rfidsdk.managers;

import android.bluetooth.BluetoothDevice;

import com.csl.cslibrary4a.BluetoothGatt;
import com.csl.cslibrary4a.CsLibrary4A;
import com.csl.cslibrary4a.ReaderDevice;
import com.csl.rfidsdk.RfidManagerBuilder;
import com.csl.rfidsdk.callbacks.RfidConnectionCallback;
import com.csl.rfidsdk.callbacks.RfidScanCallback;
import com.csl.rfidsdk.internal.SdkBridge;
import com.csl.rfidsdk.internal.ThreadManager;
import com.csl.rfidsdk.models.BatteryInfo;
import com.csl.rfidsdk.models.RfidError;
import com.csl.rfidsdk.models.RfidReader;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages RFID reader scanning and connection
 * Implementation based on cslibrary4a-usage.md
 */
public class RfidConnectionManager {
    private final SdkBridge sdkBridge;
    private final ThreadManager threadManager;
    private final RfidManagerBuilder.LoggerCallback logger;

    private boolean scanning = false;
    private RfidScanCallback scanCallback;
    private RfidConnectionCallback connectionCallback;
    private RfidReader connectedReader;

    private Runnable scanPollRunnable;
    private Runnable connectionPollRunnable;
    private Map<String, RfidReader> discoveredReaders = new HashMap<>();

    public RfidConnectionManager(SdkBridge sdkBridge, ThreadManager threadManager,
                                  RfidManagerBuilder.LoggerCallback logger) {
        this.sdkBridge = sdkBridge;
        this.threadManager = threadManager;
        this.logger = logger;
    }

    /**
     * Start scanning for RFID readers
     * Uses BLE scanning as documented in cslibrary4a-usage.md section 1.2-1.3
     */
    public void startScan(RfidScanCallback callback) {
        this.scanCallback = callback;
        this.scanning = true;
        this.discoveredReaders.clear();

        threadManager.executeOnBackground(() -> {
            try {
                CsLibrary4A sdk = sdkBridge.getSdk();

                // Start BLE scanning (cslibrary4a-usage.md line 37)
                boolean started = sdk.scanLeDevice(true);

                // Debug logging
                android.util.Log.d("SCAN_DEBUG", "scanLeDevice() called, returned: " + started);
                android.util.Log.d("SCAN_DEBUG", "Bluetooth enabled: " + sdk.isBleConnected());

                if (!started) {
                    scanning = false;
                    log("Failed to start BLE scan - scanLeDevice() returned false");
                    if (callback != null) {
                        threadManager.executeOnMain(() ->
                                callback.onScanError(new RfidError(
                                        "Failed to start BLE scan - check Bluetooth and permissions",
                                        RfidError.ErrorType.SCAN_FAILED
                                ))
                        );
                    }
                    return;
                }

                log("BLE scanning started successfully");

                // Poll for discovered devices (cslibrary4a-usage.md line 54-72)
                scanPollRunnable = new Runnable() {
                    private int pollCount = 0;

                    @Override
                    public void run() {
                        if (!scanning) return;

                        try {
                            pollCount++;

                            // Poll for newly scanned devices
                            BluetoothGatt.CsScanData scanData = sdk.getNewDeviceScanned();

                            // Debug logging - log every 10th poll or when device found
                            if (scanData != null || pollCount % 10 == 0) {
                                android.util.Log.d("SCAN_DEBUG", "Poll #" + pollCount +
                                    " - getNewDeviceScanned() returned: " +
                                    (scanData != null ? "device" : "null"));
                            }

                            if (scanData != null && scanData.device != null) {
                                String address = scanData.device.getAddress();
                                String name = scanData.getName();
                                int rssi = scanData.rssi;
                                int serviceUUID = scanData.serviceUUID2p2;

                                android.util.Log.d("SCAN_DEBUG", "Device found: " + name +
                                    " (" + address + ") RSSI: " + rssi + " serviceUUID: " + serviceUUID);

                                RfidReader reader = new RfidReader(
                                        name != null ? name : "Unknown",
                                        address,
                                        rssi,
                                        scanData.device,
                                        serviceUUID  // Pass serviceUUID from scan data
                                );

                                // Check if this is a new device or an update
                                RfidReader existing = discoveredReaders.get(address);
                                discoveredReaders.put(address, reader);

                                if (scanCallback != null) {
                                    if (existing == null) {
                                        threadManager.executeOnMain(() ->
                                                scanCallback.onReaderDiscovered(reader)
                                        );
                                    } else {
                                        threadManager.executeOnMain(() ->
                                                scanCallback.onReaderUpdated(reader)
                                        );
                                    }
                                }

                                log("Device found: " + name + " (" + address + ") RSSI: " + rssi);
                            }

                            // Continue polling if still scanning
                            // Add 100ms delay between polls to give SDK time to populate results
                            if (scanning) {
                                threadManager.executeOnMainDelayed(() -> {
                                    if (scanning) {
                                        threadManager.executeOnBackground(this);
                                    }
                                }, 100);
                            }
                        } catch (Exception e) {
                            log("Error during scan polling: " + e.getMessage());
                            android.util.Log.e("SCAN_DEBUG", "Scan polling error", e);
                        }
                    }
                };

                // Start polling after a short delay
                threadManager.executeOnBackground(scanPollRunnable);

            } catch (Exception e) {
                scanning = false;
                if (callback != null) {
                    threadManager.executeOnMain(() ->
                            callback.onScanError(new RfidError(
                                    "Scan error: " + e.getMessage(),
                                    RfidError.ErrorType.SCAN_FAILED,
                                    e
                            ))
                    );
                }
            }
        });
    }

    /**
     * Stop scanning for RFID readers
     * As documented in cslibrary4a-usage.md line 91
     */
    public void stopScan() {
        this.scanning = false;

        // Check if thread manager is still running before submitting task
        if (threadManager.isShutdown()) {
            log("Cannot stop scan - ThreadManager is shut down");
            return;
        }

        threadManager.executeOnBackground(() -> {
            try {
                CsLibrary4A sdk = sdkBridge.getSdk();
                sdk.scanLeDevice(false);
                log("BLE scanning stopped");
            } catch (Exception e) {
                log("Error stopping scan: " + e.getMessage());
            }
        });
    }

    public boolean isScanning() {
        return scanning;
    }

    /**
     * Connect to an RFID reader
     * Implementation based on cslibrary4a-usage.md section 1.5
     */
    public void connect(RfidReader reader, RfidConnectionCallback callback) {
        this.connectionCallback = callback;
        this.connectedReader = reader;

        if (callback != null) {
            threadManager.executeOnMain(callback::onConnecting);
        }

        // Validate device first (can do on any thread)
        BluetoothDevice device = reader.getBluetoothDevice();
        if (device == null) {
            connectedReader = null;
            if (callback != null) {
                threadManager.executeOnMain(() ->
                        callback.onConnectionFailed(new RfidError(
                                "Invalid Bluetooth device",
                                RfidError.ErrorType.CONNECTION_FAILED
                        ))
                );
            }
            return;
        }

        // Create ReaderDevice for SDK with correct serviceUUID
        ReaderDevice readerDevice = new ReaderDevice(
                reader.getName(),
                reader.getAddress(),
                false,
                "",
                0,
                reader.getRssi(),
                reader.getServiceUUID()  // Use serviceUUID from reader to route to correct connector
        );

        log("Connecting to " + reader.getName() + "...");

        // IMPORTANT: Call connect() on MAIN thread because SDK creates Handlers
        threadManager.executeOnMain(() -> {
            try {
                CsLibrary4A sdk = sdkBridge.getSdk();

                // Debug: Check SDK Bluetooth state before connecting
                android.util.Log.d("CONNECTION_DEBUG", "=== PRE-CONNECTION STATE ===");
                android.util.Log.d("CONNECTION_DEBUG", "Reader: " + reader.getName() + " (" + reader.getAddress() + ")");
                android.util.Log.d("CONNECTION_DEBUG", "ServiceUUID: " + readerDevice.getServiceUUID2p1());
                android.util.Log.d("CONNECTION_DEBUG", "Thread: " + Thread.currentThread().getName());

                // Check if SDK thinks it's already connected
                boolean alreadyConnected = sdk.isBleConnected();
                android.util.Log.d("CONNECTION_DEBUG", "SDK isBleConnected() before connect: " + alreadyConnected);

                // Initiate connection (cslibrary4a-usage.md line 98)
                // Note: connect() returns void - it's asynchronous
                log("Calling sdk.connect() on thread: " + Thread.currentThread().getName());
                android.util.Log.d("CONNECTION_DEBUG", "About to call sdk.connect(readerDevice)");
                sdk.connect(readerDevice);
                android.util.Log.d("CONNECTION_DEBUG", "sdk.connect() returned (void)");

                // Poll for connection on background thread (can use Thread.sleep)
                threadManager.executeOnBackground(() -> {
                    int waitCount = 40; // 20 seconds (40 * 500ms)

                    while (waitCount > 0) {
                        try {
                            Thread.sleep(500);

                            // Check connection status
                            boolean bleConnected = sdk.isBleConnected();

                            // Log status every 5 checks or when connected
                            if ((40 - waitCount) % 5 == 0 || bleConnected) {
                                android.util.Log.d("CONNECTION_DEBUG",
                                    "BLE Status check #" + (40 - waitCount) + "/40: " +
                                    "isBleConnected=" + bleConnected);
                            }

                            if (bleConnected) {
                                // Connection successful
                                log("Connected to " + reader.getName());
                                if (callback != null) {
                                    threadManager.executeOnMain(() ->
                                            callback.onConnected(connectedReader)
                                    );
                                }

                                // Wait for reader to be fully initialized (battery data available)
                                waitForReaderReady(connectedReader, callback);

                                return; // Exit polling loop
                            }

                            waitCount--;
                            log("Connection check " + (40 - waitCount) + "/40 - still waiting...");

                        } catch (InterruptedException e) {
                            log("Connection polling interrupted");
                            break;
                        }
                    }

                    // Timeout reached
                    connectedReader = null;
                    log("Connection timeout for " + reader.getName());
                    if (callback != null) {
                        threadManager.executeOnMain(() ->
                                callback.onConnectionFailed(new RfidError(
                                        "Connection timeout - reader did not respond",
                                        RfidError.ErrorType.TIMEOUT
                                ))
                        );
                    }
                });

            } catch (Exception e) {
                connectedReader = null;
                log("Error initiating connection: " + e.getMessage());
                if (callback != null) {
                    threadManager.executeOnMain(() ->
                            callback.onConnectionFailed(new RfidError(
                                    "Connection error: " + e.getMessage(),
                                    RfidError.ErrorType.CONNECTION_FAILED,
                                    e
                            ))
                    );
                }
            }
        });
    }

    /**
     * Disconnect from the current reader
     * As documented in cslibrary4a-usage.md line 136
     */
    public void disconnect() {
        // Check if thread manager is still running before submitting task
        if (threadManager.isShutdown()) {
            log("Cannot disconnect - ThreadManager is shut down");
            connectedReader = null;
            return;
        }

        threadManager.executeOnBackground(() -> {
            try {
                CsLibrary4A sdk = sdkBridge.getSdk();
                sdk.disconnect(false);

                RfidReader disconnectedReader = connectedReader;
                connectedReader = null;

                log("Disconnected from reader");

                if (connectionCallback != null && disconnectedReader != null) {
                    threadManager.executeOnMain(() ->
                            connectionCallback.onDisconnected(disconnectedReader, null)
                    );
                }
            } catch (Exception e) {
                log("Error disconnecting: " + e.getMessage());
            }
        });
    }

    /**
     * Cleanup resources - stops scanning and clears callbacks
     * Call this before shutting down ThreadManager
     */
    public void cleanup() {
        this.scanning = false;
        this.scanCallback = null;
        this.connectionCallback = null;
        this.discoveredReaders.clear();
        log("Cleanup completed");
    }

    public RfidReader getConnectedReader() {
        return connectedReader;
    }

    /**
     * Wait for reader to be fully initialized and ready for operations.
     * Polls battery data every 200ms for up to 15 seconds.
     * Fires onReaderReady() callback when battery data is available or on timeout.
     *
     * @param reader The connected reader
     * @param callback The connection callback to fire onReaderReady()
     */
    private void waitForReaderReady(RfidReader reader, RfidConnectionCallback callback) {
        int maxWaitMs = 15000; // 15 second timeout
        int pollIntervalMs = 200;
        int maxAttempts = maxWaitMs / pollIntervalMs; // 75 attempts

        log("Waiting for reader to be ready (battery data available)...");

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // Query battery to check if reader is ready
                BatteryInfo batteryInfo = BatteryInfo.fromSdk(sdkBridge.getSdk());

                if (batteryInfo != null && batteryInfo.isValid()) {
                    // Battery data available - reader is ready
                    log("Reader ready after " + (attempt * pollIntervalMs) + "ms - battery data available");
                    if (callback != null) {
                        threadManager.executeOnMain(() ->
                                callback.onReaderReady(reader)
                        );
                    }
                    return;
                }

                // Wait before next poll
                Thread.sleep(pollIntervalMs);

            } catch (InterruptedException e) {
                log("Reader ready check interrupted");
                break;
            } catch (Exception e) {
                log("Error checking battery: " + e.getMessage());
                // Continue polling despite errors
            }
        }

        // Timeout reached - proceed anyway
        log("Reader ready timeout after " + maxWaitMs + "ms - proceeding without battery data");
        if (callback != null) {
            threadManager.executeOnMain(() ->
                    callback.onReaderReady(reader)
            );
        }
    }

    private void log(String message) {
        // Always log to Android Log for debugging
        android.util.Log.d("ConnectionManager", message);

        // Also log via callback if available
        if (logger != null) {
            logger.log("ConnectionManager: " + message);
        }
    }
}
