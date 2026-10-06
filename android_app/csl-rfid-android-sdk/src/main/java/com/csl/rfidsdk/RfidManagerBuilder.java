package com.csl.rfidsdk;

import android.content.Context;

/**
 * Builder for creating RfidManager instances with custom configuration
 */
public class RfidManagerBuilder {
    private final Context context;
    private LoggerCallback logger;
    private boolean autoReconnect = false;

    public interface LoggerCallback {
        void log(String message);
    }

    RfidManagerBuilder(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Set a custom logger for SDK operations
     */
    public RfidManagerBuilder setLogger(LoggerCallback logger) {
        this.logger = logger;
        return this;
    }

    /**
     * Enable or disable auto-reconnect on connection loss
     */
    public RfidManagerBuilder setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
        return this;
    }

    /**
     * Build the RfidManager instance
     */
    public RfidManager build() {
        return new RfidManager(context, logger, autoReconnect);
    }
}
