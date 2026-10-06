package com.csl.rfidsdk.config;

/**
 * Reason for stopping an RFID operation
 */
public enum RfidStopReason {
    /**
     * User manually stopped the operation
     */
    USER_STOPPED,

    /**
     * Operation completed successfully
     */
    COMPLETED,

    /**
     * Operation stopped due to error
     */
    ERROR,

    /**
     * Operation stopped due to timeout
     */
    TIMEOUT,

    /**
     * Connection lost during operation
     */
    DISCONNECTED
}
