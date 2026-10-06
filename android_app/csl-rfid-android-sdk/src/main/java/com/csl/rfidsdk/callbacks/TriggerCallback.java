package com.csl.rfidsdk.callbacks;

/**
 * Callback interface for hardware trigger key state changes on CS108/CS710S readers.
 * The trigger key is a physical button on the reader that can be pressed/released
 * to control RFID operations.
 */
public interface TriggerCallback {
    /**
     * Called when the trigger key state changes (pressed or released).
     * This callback is always invoked on the main thread.
     *
     * @param pressed true if trigger is pressed, false if released
     */
    void onTriggerStateChanged(boolean pressed);
}
