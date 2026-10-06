package com.csl.rfidsdk.models;

import com.csl.rfidsdk.config.RfidInventoryMode;
import com.csl.rfidsdk.config.RfidRegion;
import com.csl.rfidsdk.config.RfidTarget;

/**
 * Configuration settings for RFID operations
 */
public class RfidConfiguration {
    private final int powerLevel;
    private final int session;
    private final int qValue;
    private final RfidTarget target;
    private final RfidInventoryMode inventoryMode;
    private final RfidRegion region;
    private final boolean populateRssi;
    private final boolean populatePhase;
    private final boolean populateChannel;
    private final boolean enableBeep;
    private final boolean enableVibrate;

    private RfidConfiguration(Builder builder) {
        this.powerLevel = builder.powerLevel;
        this.session = builder.session;
        this.qValue = builder.qValue;
        this.target = builder.target;
        this.inventoryMode = builder.inventoryMode;
        this.region = builder.region;
        this.populateRssi = builder.populateRssi;
        this.populatePhase = builder.populatePhase;
        this.populateChannel = builder.populateChannel;
        this.enableBeep = builder.enableBeep;
        this.enableVibrate = builder.enableVibrate;
    }

    public int getPowerLevel() {
        return powerLevel;
    }

    public int getSession() {
        return session;
    }

    public int getQValue() {
        return qValue;
    }

    public RfidTarget getTarget() {
        return target;
    }

    public RfidInventoryMode getInventoryMode() {
        return inventoryMode;
    }

    public RfidRegion getRegion() {
        return region;
    }

    public boolean isPopulateRssi() {
        return populateRssi;
    }

    public boolean isPopulatePhase() {
        return populatePhase;
    }

    public boolean isPopulateChannel() {
        return populateChannel;
    }

    public boolean isEnableBeep() {
        return enableBeep;
    }

    public boolean isEnableVibrate() {
        return enableVibrate;
    }

    public static class Builder {
        private int powerLevel = 300; // Default 30.0 dBm
        private int session = 1;
        private int qValue = 4;
        private RfidTarget target = RfidTarget.A;
        private RfidInventoryMode inventoryMode = RfidInventoryMode.COMPACT;
        private RfidRegion region = RfidRegion.FCC;
        private boolean populateRssi = true;
        private boolean populatePhase = false;
        private boolean populateChannel = false;
        private boolean enableBeep = true;
        private boolean enableVibrate = true;

        public Builder powerLevel(int powerLevel) {
            this.powerLevel = powerLevel;
            return this;
        }

        public Builder session(int session) {
            this.session = session;
            return this;
        }

        public Builder qValue(int qValue) {
            this.qValue = qValue;
            return this;
        }

        public Builder target(RfidTarget target) {
            this.target = target;
            return this;
        }

        public Builder inventoryMode(RfidInventoryMode inventoryMode) {
            this.inventoryMode = inventoryMode;
            return this;
        }

        public Builder region(RfidRegion region) {
            this.region = region;
            return this;
        }

        public Builder populateRssi(boolean populateRssi) {
            this.populateRssi = populateRssi;
            return this;
        }

        public Builder populatePhase(boolean populatePhase) {
            this.populatePhase = populatePhase;
            return this;
        }

        public Builder populateChannel(boolean populateChannel) {
            this.populateChannel = populateChannel;
            return this;
        }

        public Builder enableBeep(boolean enableBeep) {
            this.enableBeep = enableBeep;
            return this;
        }

        public Builder enableVibrate(boolean enableVibrate) {
            this.enableVibrate = enableVibrate;
            return this;
        }

        public RfidConfiguration build() {
            return new RfidConfiguration(this);
        }
    }
}
