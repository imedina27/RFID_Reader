package com.csl.rfidsdk.models;

/**
 * Represents an error that occurred during RFID operations
 */
public class RfidError {
    private final String message;
    private final ErrorType type;
    private final Throwable cause;

    public enum ErrorType {
        CONNECTION_FAILED,
        CONNECTION_LOST,
        DISCONNECTED,
        NOT_CONNECTED,
        SCAN_FAILED,
        INVENTORY_FAILED,
        CONFIGURATION_FAILED,
        PERMISSION_DENIED,
        BLUETOOTH_DISABLED,
        TIMEOUT,
        UNKNOWN
    }

    public RfidError(String message, ErrorType type) {
        this(message, type, null);
    }

    public RfidError(String message, ErrorType type, Throwable cause) {
        this.message = message;
        this.type = type;
        this.cause = cause;
    }

    public String getMessage() {
        return message;
    }

    public ErrorType getType() {
        return type;
    }

    public Throwable getCause() {
        return cause;
    }

    @Override
    public String toString() {
        return "RfidError{" +
                "message='" + message + '\'' +
                ", type=" + type +
                '}';
    }
}
