package com.csl.rfidsdk.managers;

import com.csl.cslibrary4a.CsLibrary4A;
import com.csl.cslibrary4a.RfidReader;
import com.csl.rfidsdk.RfidManagerBuilder;
import com.csl.rfidsdk.callbacks.RfidConfigurationCallback;
import com.csl.rfidsdk.config.RfidInventoryMode;
import com.csl.rfidsdk.config.RfidRegion;
import com.csl.rfidsdk.config.RfidTarget;
import com.csl.rfidsdk.internal.SdkBridge;
import com.csl.rfidsdk.internal.ThreadManager;
import com.csl.rfidsdk.models.RfidConfiguration;
import com.csl.rfidsdk.models.RfidError;

/**
 * Manages RFID reader configuration
 * Applies settings like power, session, target, region, etc.
 */
public class RfidConfigurationManager {
    private final SdkBridge sdkBridge;
    private final ThreadManager threadManager;
    private final RfidManagerBuilder.LoggerCallback logger;

    private RfidConfiguration currentConfiguration;

    public RfidConfigurationManager(SdkBridge sdkBridge, ThreadManager threadManager,
                                     RfidManagerBuilder.LoggerCallback logger) {
        this.sdkBridge = sdkBridge;
        this.threadManager = threadManager;
        this.logger = logger;

        // Default configuration
        this.currentConfiguration = new RfidConfiguration.Builder().build();
    }

    /**
     * Apply configuration asynchronously with callback
     * @param configuration The configuration to apply
     * @param callback Callback for success/failure
     */
    public void applyConfiguration(RfidConfiguration configuration, RfidConfigurationCallback callback) {
        this.currentConfiguration = configuration;

        threadManager.executeOnBackground(() -> {
            try {
                CsLibrary4A sdk = sdkBridge.getSdk();

                // Check if connected
                if (!sdk.isBleConnected()) {
                    if (callback != null) {
                        threadManager.executeOnMain(() ->
                                callback.onConfigurationFailed(new RfidError(
                                        "Not connected to reader",
                                        RfidError.ErrorType.NOT_CONNECTED
                                ))
                        );
                    }
                    return;
                }

                applyConfigurationInternal(sdk, configuration);

                log("Configuration applied successfully");

                if (callback != null) {
                    threadManager.executeOnMain(callback::onConfigured);
                }

            } catch (Exception e) {
                log("Error applying configuration: " + e.getMessage());
                if (callback != null) {
                    threadManager.executeOnMain(() ->
                            callback.onConfigurationFailed(new RfidError(
                                    "Configuration error: " + e.getMessage(),
                                    RfidError.ErrorType.CONFIGURATION_FAILED,
                                    e
                            ))
                    );
                }
            }
        });
    }

    /**
     * Apply configuration synchronously (internal use)
     * Must be called on background thread
     */
    public void applyConfigurationInternal(CsLibrary4A sdk, RfidConfiguration configuration) {
        // 1. Set power level (0-320, representing 0.0-32.0 dBm)
        // Uses setPowerLevel(long) method in CsLibrary4A
        sdk.setPowerLevel(configuration.getPowerLevel());
        log("Power level: " + configuration.getPowerLevel() + " (" +
            (configuration.getPowerLevel() / 10.0) + " dBm)");

        // 2. Set session and target together using setTagGroup(sL, session, target)
        // Parameters: sL=0 (select), session (0-3), target (0=A, 1=B, 2=AB_FLIP)
        int targetValue = convertTargetToSdkValue(configuration.getTarget());
        sdk.setTagGroup(0, configuration.getSession(), targetValue);
        log("Session: " + configuration.getSession() + ", Target: " +
            configuration.getTarget() + " (SDK value: " + targetValue + ")");

        // 3. Set inventory mode (compact for CS710S)
        boolean compactMode = configuration.getInventoryMode() == RfidInventoryMode.COMPACT;
        sdk.setInvModeCompact(compactMode);
        log("Inventory mode: " + configuration.getInventoryMode() + " (compact=" + compactMode + ")");

        // 4. Skip region setting - region/country configuration is reader-specific and complex
        // The reader's default region should be used instead of trying to set it programmatically
        // Region is determined by hardware and the countryCode, which affects the available region list
        log("Region: " + configuration.getRegion() + " (using reader default - not modified)");

        // 5. Set Q value (0-15) - method takes byte parameter
        sdk.setQValue((byte) configuration.getQValue());
        log("Q value: " + configuration.getQValue());

        // 6. Enable/disable beep on tag read
        sdk.setInventoryBeep(configuration.isEnableBeep());
        log("Beep: " + configuration.isEnableBeep());

        // 7. Enable/disable vibration on tag read
        sdk.setInventoryVibrate(configuration.isEnableVibrate());
        log("Vibrate: " + configuration.isEnableVibrate());

        log("All configuration settings applied");
    }

    /**
     * Get current configuration
     */
    public RfidConfiguration getCurrentConfiguration() {
        return currentConfiguration;
    }

    /**
     * Convert RfidTarget enum to SDK integer value
     */
    private int convertTargetToSdkValue(RfidTarget target) {
        switch (target) {
            case A:
                return 0;
            case B:
                return 1;
            case AB_FLIP:
                return 2;
            default:
                return 0; // Default to A
        }
    }

    private void log(String message) {
        // Always log to Android Log
        android.util.Log.d("ConfigurationManager", message);

        // Also log via callback if available
        if (logger != null) {
            logger.log("ConfigurationManager: " + message);
        }
    }
}
