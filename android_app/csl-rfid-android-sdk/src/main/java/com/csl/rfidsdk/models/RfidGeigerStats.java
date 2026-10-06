package com.csl.rfidsdk.models;

/**
 * Statistics for RFID Geiger search operations
 */
public class RfidGeigerStats {
    private final double currentRssi;
    private final double peakRssi;
    private final int readCount;
    private final long duration;
    private final double readRate;

    public RfidGeigerStats(double currentRssi, double peakRssi, int readCount, long duration, double readRate) {
        this.currentRssi = currentRssi;
        this.peakRssi = peakRssi;
        this.readCount = readCount;
        this.duration = duration;
        this.readRate = readRate;
    }

    public double getCurrentRssi() {
        return currentRssi;
    }

    public double getPeakRssi() {
        return peakRssi;
    }

    public int getReadCount() {
        return readCount;
    }

    public long getDuration() {
        return duration;
    }

    public double getReadRate() {
        return readRate;
    }

    /**
     * Get proximity level (0.0 - 1.0)
     * Based on RSSI threshold mapping
     * 30 dBuV = 0.0 (far)
     * 70 dBuV = 1.0 (very close)
     */
    public double getProximityLevel() {
        double min = 30.0;
        double max = 70.0;
        double proximity = (currentRssi - min) / (max - min);
        return Math.max(0.0, Math.min(1.0, proximity)) * 100;
    }

    @Override
    public String toString() {
        return "RfidGeigerStats{" +
                "currentRssi=" + currentRssi +
                ", peakRssi=" + peakRssi +
                ", readCount=" + readCount +
                ", duration=" + duration +
                ", readRate=" + readRate +
                ", proximityLevel=" + getProximityLevel() +
                '}';
    }

    /**
     * Builder for RfidGeigerStats
     */
    public static class Builder {
        private String targetEpc;
        private double currentRssi;
        private double peakRssi;
        private int readCount;
        private int proximity;
        private long elapsedTimeMs;

        public Builder targetEpc(String targetEpc) {
            this.targetEpc = targetEpc;
            return this;
        }

        public Builder currentRssi(double currentRssi) {
            this.currentRssi = currentRssi;
            return this;
        }

        public Builder peakRssi(double peakRssi) {
            this.peakRssi = peakRssi;
            return this;
        }

        public Builder readCount(int readCount) {
            this.readCount = readCount;
            return this;
        }

        public Builder proximity(int proximity) {
            this.proximity = proximity;
            return this;
        }

        public Builder elapsedTimeMs(long elapsedTimeMs) {
            this.elapsedTimeMs = elapsedTimeMs;
            return this;
        }

        public RfidGeigerStats build() {
            double readRate = 0.0;
            if (elapsedTimeMs > 0) {
                readRate = (readCount * 1000.0) / elapsedTimeMs;
            }
            return new RfidGeigerStats(currentRssi, peakRssi, readCount, elapsedTimeMs, readRate);
        }
    }
}
