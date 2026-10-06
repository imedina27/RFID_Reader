package com.csl.rfidsdk.callbacks;

import com.csl.rfidsdk.config.RfidStopReason;
import com.csl.rfidsdk.models.BatteryInfo;
import com.csl.rfidsdk.models.RfidError;
import com.csl.rfidsdk.models.RfidGeigerStats;

/**
 * Callback interface for RFID Geiger search operations (tag locating)
 */
public interface RfidGeigerCallback {
    /**
     * Called when target tag is read with RSSI update
     * @param rssi Signal strength of the target tag
     * @param stats Current Geiger search statistics
     */
    void onRssiUpdate(double rssi, RfidGeigerStats stats);

    /**
     * Called when proximity update is available
     * @param stats Current Geiger search statistics with proximity info
     */
    void onProximityUpdate(RfidGeigerStats stats);

    /**
     * Called when search operation starts
     */
    void onSearchStarted();

    /**
     * Called when search operation stops
     * @param reason The reason search stopped
     */
    void onSearchStopped(RfidStopReason reason);

    /**
     * Called when an error occurs during search
     * @param error The error that occurred
     */
    void onSearchError(RfidError error);

    /**
     * Called when battery information is updated during search
     * Optional callback - default implementation does nothing for backward compatibility
     * @param batteryInfo Current battery information
     */
    default void onBatteryUpdate(BatteryInfo batteryInfo) {
        // Default empty implementation for backward compatibility
    }
}
