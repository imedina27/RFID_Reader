package com.csl.rfidsdk.callbacks;

import com.csl.rfidsdk.models.BatteryInfo;

/**
 * Callback for battery level monitoring
 * Fires periodically (every 5 seconds) when reader is connected
 */
public interface BatteryCallback {
    /**
     * Called when battery level is updated
     * @param batteryInfo Battery information including voltage and percentage
     */
    void onBatteryUpdate(BatteryInfo batteryInfo);
}
