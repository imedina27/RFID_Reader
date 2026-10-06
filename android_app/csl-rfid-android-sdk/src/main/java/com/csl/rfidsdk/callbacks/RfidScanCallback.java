package com.csl.rfidsdk.callbacks;

import com.csl.rfidsdk.models.RfidError;
import com.csl.rfidsdk.models.RfidReader;

/**
 * Callback interface for RFID reader scanning operations
 */
public interface RfidScanCallback {
    /**
     * Called when a new reader is discovered during scanning
     * @param reader The discovered RFID reader
     */
    void onReaderDiscovered(RfidReader reader);

    /**
     * Called when an existing reader's information is updated (e.g., RSSI change)
     * @param reader The updated RFID reader
     */
    void onReaderUpdated(RfidReader reader);

    /**
     * Called when an error occurs during scanning
     * @param error The error that occurred
     */
    void onScanError(RfidError error);
}
