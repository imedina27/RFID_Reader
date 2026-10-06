package com.csl.rfidsdk.internal;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Manages threading for SDK operations
 * Ensures callbacks are executed on the main thread
 */
public class ThreadManager {
    private static final String TAG = "ThreadManager";
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private volatile boolean isShutdown = false;

    public ThreadManager() {
        this.executorService = Executors.newSingleThreadExecutor();

        // Ensure Handler is created with main looper, even if ThreadManager
        // is constructed on a background thread
        try {
            this.mainHandler = new Handler(Looper.getMainLooper());
        } catch (Exception e) {
            Log.e(TAG, "Failed to create main handler", e);
            throw new RuntimeException("Failed to initialize ThreadManager - could not create main thread handler", e);
        }

        Log.d(TAG, "ThreadManager initialized on thread: " + Thread.currentThread().getName());
    }

    /**
     * Execute a task on a background thread
     * Gracefully handles execution if executor is shut down
     */
    public void executeOnBackground(Runnable task) {
        if (isShutdown) {
            Log.d(TAG, "Ignoring background task - ThreadManager is shut down");
            return;
        }

        try {
            executorService.execute(task);
        } catch (RejectedExecutionException e) {
            Log.d(TAG, "Task rejected - executor shutting down: " + e.getMessage());
            // Gracefully ignore - this is expected during shutdown
        }
    }

    /**
     * Execute a task on the main thread
     */
    public void executeOnMain(Runnable task) {
        if (isShutdown) {
            Log.d(TAG, "Ignoring main thread task - ThreadManager is shut down");
            return;
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            mainHandler.post(task);
        }
    }

    /**
     * Execute a task on the main thread with delay
     */
    public void executeOnMainDelayed(Runnable task, long delayMillis) {
        if (isShutdown) {
            Log.d(TAG, "Ignoring delayed task - ThreadManager is shut down");
            return;
        }

        mainHandler.postDelayed(task, delayMillis);
    }

    /**
     * Shutdown the thread manager
     */
    public void shutdown() {
        isShutdown = true;
        executorService.shutdown();
        mainHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Check if the thread manager is shut down
     */
    public boolean isShutdown() {
        return isShutdown;
    }
}
