package com.csl.rfidsdk.callbacks;

import com.csl.rfidsdk.config.RfidStopReason;
import com.csl.rfidsdk.models.BarcodeData;
import com.csl.rfidsdk.models.BarcodeStats;
import com.csl.rfidsdk.models.BatteryInfo;
import com.csl.rfidsdk.models.RfidError;

/**
 * Callback interface for barcode scanning operations
 */
public interface BarcodeScanCallback {
    /**
     * Called when a barcode is scanned
     * @param barcode The barcode data that was scanned
     */
    void onBarcodeScanned(BarcodeData barcode);

    /**
     * Called periodically with barcode scanning statistics
     * @param stats Current scanning statistics
     */
    void onScanUpdate(BarcodeStats stats);

    /**
     * Called when barcode scanning stops
     * @param reason The reason scanning stopped
     */
    void onScanStopped(RfidStopReason reason);

    /**
     * Called when an error occurs during scanning
     * @param error The error that occurred
     */
    void onScanError(RfidError error);

    /**
     * Called when battery information is updated during scanning
     * Optional callback - default implementation does nothing for backward compatibility
     * @param batteryInfo Current battery information
     */
    default void onBatteryUpdate(BatteryInfo batteryInfo) {
        // Default empty implementation for backward compatibility
    }
}
