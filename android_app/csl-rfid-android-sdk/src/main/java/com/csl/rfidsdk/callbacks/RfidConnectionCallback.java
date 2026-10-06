package com.csl.rfidsdk.callbacks;

import com.csl.rfidsdk.models.RfidError;
import com.csl.rfidsdk.models.RfidReader;

/**
 * Callback interface for RFID reader connection operations
 */
public interface RfidConnectionCallback {
    /**
     * Called when connection attempt begins
     */
    void onConnecting();

    /**
     * Called when successfully connected to the reader
     * @param reader The connected RFID reader
     */
    void onConnected(RfidReader reader);

    /**
     * Called when reader is fully initialized and ready for operations.
     * This fires after onConnected() when battery data is available.
     * @param reader The ready RFID reader
     */
    void onReaderReady(RfidReader reader);

    /**
     * Called when disconnected from the reader
     * @param reader The disconnected reader
     * @param error The error that caused disconnection, or null if intentional
     */
    void onDisconnected(RfidReader reader, RfidError error);

    /**
     * Called when connection attempt fails
     * @param error The error that occurred
     */
    void onConnectionFailed(RfidError error);
}
