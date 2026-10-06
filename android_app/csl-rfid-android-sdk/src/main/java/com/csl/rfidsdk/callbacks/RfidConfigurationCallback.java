package com.csl.rfidsdk.callbacks;

import com.csl.rfidsdk.models.RfidError;

/**
 * Callback interface for configuration operations
 */
public interface RfidConfigurationCallback {
    /**
     * Called when configuration is successfully applied
     */
    void onConfigured();

    /**
     * Called when configuration fails
     * @param error The error that occurred
     */
    void onConfigurationFailed(RfidError error);
}
