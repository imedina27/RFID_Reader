package com.csl.rfidsdk.internal;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import com.csl.cslibrary4a.CsLibrary4A;

/**
 * Bridge to the underlying cslibrary4a SDK
 * Provides a clean interface to SDK operations
 */
public class SdkBridge {
    private static final String TAG = "SdkBridge";
    private final CsLibrary4A sdk;
    private final Context context;

    public SdkBridge(Context context) {
        this.context = context.getApplicationContext();

        Log.d(TAG, "Creating SdkBridge on thread: " + Thread.currentThread().getName());

        try {
            Log.d(TAG, "Initializing CsLibrary4A on thread: " + Thread.currentThread().getName());
            this.sdk = new CsLibrary4A(this.context, null);
            Log.d(TAG, "CsLibrary4A initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize CsLibrary4A on thread: " + Thread.currentThread().getName(), e);
            throw new RuntimeException("Failed to initialize RFID SDK", e);
        }
    }

    public CsLibrary4A getSdk() {
        return sdk;
    }

    public Context getContext() {
        return context;
    }

    /**
     * Check if connected to a reader
     */
    public boolean isConnected() {
        return sdk.isBleConnected();
    }

    /**
     * Release SDK resources
     */
    public void release() {
        if (sdk != null) {
            sdk.disconnect(false);
        }
    }
}
