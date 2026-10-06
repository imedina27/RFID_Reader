package com.csl.rfidsdk.models;

import com.csl.cslibrary4a.CsLibrary4A;

/**
 * Battery information from RFID reader
 * Provides voltage, percentage, and formatted display strings
 */
public class BatteryInfo {
    private final int rawLevel;        // Battery level in millivolts (e.g., 3750 = 3.750V)
    private final float voltage;       // Battery voltage in volts (e.g., 3.750)
    private final int percentage;      // Battery percentage 0-100
    private final long timestamp;      // Timestamp when reading was taken
    private final String displayString; // Formatted display string from SDK

    private BatteryInfo(int rawLevel, float voltage, int percentage, long timestamp, String displayString) {
        this.rawLevel = rawLevel;
        this.voltage = voltage;
        this.percentage = percentage;
        this.timestamp = timestamp;
        this.displayString = displayString;
    }

    /**
     * Create BatteryInfo from SDK instance
     * @param sdk The CsLibrary4A SDK instance
     * @return BatteryInfo object with current battery state
     */
    public static BatteryInfo fromSdk(CsLibrary4A sdk) {
        if (sdk == null) {
            return new BatteryInfo(0, 0.0f, 0, System.currentTimeMillis(), "");
        }

        int rawLevel = sdk.getBatteryLevel();
        float voltage = (float) rawLevel / 1000.0f;

        // Get formatted display string (includes voltage and percentage)
        String displayString = sdk.getBatteryDisplay(true);
        if (displayString == null) displayString = "";

        // Calculate percentage using SDK's battery curve algorithm
        // The SDK's getBatteryDisplay(false) returns "XX%\r\n P=YY" format
        // We'll extract percentage by calling it and parsing
        int percentage = 0;
        try {
            String percentDisplay = sdk.getBatteryDisplay(false);
            if (percentDisplay != null && !percentDisplay.trim().isEmpty()) {
                // Format is "85%\r\n P=300" - extract the percentage part
                int percentIndex = percentDisplay.indexOf("%");
                if (percentIndex > 0) {
                    String percentStr = percentDisplay.substring(0, percentIndex).trim();
                    percentage = Integer.parseInt(percentStr);
                }
            }
        } catch (Exception e) {
            // If parsing fails, percentage remains 0
        }

        return new BatteryInfo(rawLevel, voltage, percentage, System.currentTimeMillis(), displayString);
    }

    /**
     * Get raw battery level in millivolts
     * @return Battery level in mV (e.g., 3750 for 3.750V)
     */
    public int getRawLevel() {
        return rawLevel;
    }

    /**
     * Get battery voltage in volts
     * @return Voltage in V (e.g., 3.750)
     */
    public float getVoltage() {
        return voltage;
    }

    /**
     * Get battery percentage
     * @return Percentage 0-100
     */
    public int getPercentage() {
        return percentage;
    }

    /**
     * Get timestamp when this reading was taken
     * @return Timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Get formatted voltage string
     * @return Formatted string like "3.750 V"
     */
    public String getFormattedVoltage() {
        if (rawLevel == 0) return "";
        return String.format("%.3f V", voltage);
    }

    /**
     * Get formatted percentage string
     * @return Formatted string like "85%"
     */
    public String getFormattedPercentage() {
        if (rawLevel == 0) return "";
        return String.format("%d%%", percentage);
    }

    /**
     * Get display string from SDK
     * This is the formatted string directly from the SDK's getBatteryDisplay() method
     * @return Display string (e.g., "3.750 V")
     */
    public String getDisplayString() {
        return displayString;
    }

    /**
     * Check if battery information is valid
     * @return true if battery level is > 0
     */
    public boolean isValid() {
        return rawLevel > 0;
    }

    @Override
    public String toString() {
        return "BatteryInfo{" +
                "voltage=" + voltage + "V" +
                ", percentage=" + percentage + "%" +
                ", rawLevel=" + rawLevel + "mV" +
                '}';
    }

    /**
     * Builder for BatteryInfo
     */
    public static class Builder {
        private int rawLevel;
        private float voltage;
        private int percentage;
        private String displayString = "";

        public Builder rawLevel(int rawLevel) {
            this.rawLevel = rawLevel;
            this.voltage = (float) rawLevel / 1000.0f;
            return this;
        }

        public Builder voltage(float voltage) {
            this.voltage = voltage;
            this.rawLevel = (int) (voltage * 1000);
            return this;
        }

        public Builder percentage(int percentage) {
            this.percentage = percentage;
            return this;
        }

        public Builder displayString(String displayString) {
            this.displayString = displayString;
            return this;
        }

        public BatteryInfo build() {
            return new BatteryInfo(rawLevel, voltage, percentage, System.currentTimeMillis(), displayString);
        }
    }
}
