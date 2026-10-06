package com.csl.rfidsdk.callbacks;

import com.csl.rfidsdk.config.RfidStopReason;
import com.csl.rfidsdk.models.BatteryInfo;
import com.csl.rfidsdk.models.RfidError;
import com.csl.rfidsdk.models.RfidInventoryStats;
import com.csl.rfidsdk.models.RfidTag;

/**
 * Callback interface for RFID inventory operations
 */
public interface RfidInventoryCallback {
    /**
     * Called when a tag is read during inventory
     * @param tag The RFID tag that was read
     */
    void onTagRead(RfidTag tag);

    /**
     * Called periodically with inventory statistics
     * @param stats Current inventory statistics
     */
    void onInventoryRound(RfidInventoryStats stats);

    /**
     * Called when inventory operation stops
     * @param reason The reason inventory stopped
     */
    void onInventoryStopped(RfidStopReason reason);

    /**
     * Called when an error occurs during inventory
     * @param error The error that occurred
     */
    void onInventoryError(RfidError error);

    /**
     * Called when battery information is updated during inventory
     * Optional callback - default implementation does nothing for backward compatibility
     * @param batteryInfo Current battery information
     */
    default void onBatteryUpdate(BatteryInfo batteryInfo) {
        // Default empty implementation for backward compatibility
    }
}
