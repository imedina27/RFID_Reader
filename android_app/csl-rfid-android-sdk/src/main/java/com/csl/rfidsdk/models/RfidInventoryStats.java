package com.csl.rfidsdk.models;

/**
 * Statistics for RFID inventory operations
 */
public class RfidInventoryStats {
    private final int uniqueTagCount;
    private final int totalReads;
    private final long duration;
    private final double readRate;
    private final long startTime;

    public RfidInventoryStats(int uniqueTagCount, int totalReads, long duration, double readRate, long startTime) {
        this.uniqueTagCount = uniqueTagCount;
        this.totalReads = totalReads;
        this.duration = duration;
        this.readRate = readRate;
        this.startTime = startTime;
    }

    public int getUniqueTagCount() {
        return uniqueTagCount;
    }

    public int getTotalReads() {
        return totalReads;
    }

    public long getDuration() {
        return duration;
    }

    public double getReadRate() {
        return readRate;
    }

    public long getStartTime() {
        return startTime;
    }

    @Override
    public String toString() {
        return "RfidInventoryStats{" +
                "uniqueTagCount=" + uniqueTagCount +
                ", totalReads=" + totalReads +
                ", duration=" + duration +
                ", readRate=" + readRate +
                '}';
    }

    /**
     * Builder for RfidInventoryStats
     */
    public static class Builder {
        private int uniqueTagCount;
        private int totalReads;
        private long elapsedTimeMs;
        private double readsPerSecond;

        public Builder uniqueTagCount(int uniqueTagCount) {
            this.uniqueTagCount = uniqueTagCount;
            return this;
        }

        public Builder totalReads(int totalReads) {
            this.totalReads = totalReads;
            return this;
        }

        public Builder elapsedTimeMs(long elapsedTimeMs) {
            this.elapsedTimeMs = elapsedTimeMs;
            return this;
        }

        public Builder readsPerSecond(double readsPerSecond) {
            this.readsPerSecond = readsPerSecond;
            return this;
        }

        public RfidInventoryStats build() {
            return new RfidInventoryStats(uniqueTagCount, totalReads, elapsedTimeMs, readsPerSecond, System.currentTimeMillis());
        }
    }
}
