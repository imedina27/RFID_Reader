package com.csl.rfidsdk.models;

/**
 * Statistics for barcode scanning operations
 */
public class BarcodeStats {
    private final int uniqueBarcodes;
    private final int totalScans;
    private final long elapsedTimeMs;

    private BarcodeStats(Builder builder) {
        this.uniqueBarcodes = builder.uniqueBarcodes;
        this.totalScans = builder.totalScans;
        this.elapsedTimeMs = builder.elapsedTimeMs;
    }

    public int getUniqueBarcodes() {
        return uniqueBarcodes;
    }

    public int getTotalScans() {
        return totalScans;
    }

    public long getElapsedTimeMs() {
        return elapsedTimeMs;
    }

    public long getElapsedTimeSec() {
        return elapsedTimeMs / 1000;
    }

    @Override
    public String toString() {
        return "BarcodeStats{" +
                "uniqueBarcodes=" + uniqueBarcodes +
                ", totalScans=" + totalScans +
                ", elapsedTimeSec=" + getElapsedTimeSec() +
                '}';
    }

    public static class Builder {
        private int uniqueBarcodes = 0;
        private int totalScans = 0;
        private long elapsedTimeMs = 0;

        public Builder uniqueBarcodes(int uniqueBarcodes) {
            this.uniqueBarcodes = uniqueBarcodes;
            return this;
        }

        public Builder totalScans(int totalScans) {
            this.totalScans = totalScans;
            return this;
        }

        public Builder elapsedTimeMs(long elapsedTimeMs) {
            this.elapsedTimeMs = elapsedTimeMs;
            return this;
        }

        public BarcodeStats build() {
            return new BarcodeStats(this);
        }
    }
}
