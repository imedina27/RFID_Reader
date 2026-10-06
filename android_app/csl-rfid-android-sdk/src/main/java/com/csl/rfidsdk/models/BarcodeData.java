package com.csl.rfidsdk.models;

/**
 * Represents barcode data scanned from the reader
 */
public class BarcodeData {
    private final String barcode;
    private final int count;
    private final long timestamp;

    private BarcodeData(Builder builder) {
        this.barcode = builder.barcode;
        this.count = builder.count;
        this.timestamp = builder.timestamp;
    }

    public String getBarcode() {
        return barcode;
    }

    public int getCount() {
        return count;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Create a new BarcodeData with incremented count
     */
    public BarcodeData withCount(int newCount) {
        return new Builder(this.barcode)
                .count(newCount)
                .timestamp(this.timestamp)
                .build();
    }

    /**
     * Create a new BarcodeData with updated timestamp
     */
    public BarcodeData withTimestamp(long newTimestamp) {
        return new Builder(this.barcode)
                .count(this.count)
                .timestamp(newTimestamp)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BarcodeData that = (BarcodeData) o;
        return barcode != null ? barcode.equals(that.barcode) : that.barcode == null;
    }

    @Override
    public int hashCode() {
        return barcode != null ? barcode.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "BarcodeData{" +
                "barcode='" + barcode + '\'' +
                ", count=" + count +
                ", timestamp=" + timestamp +
                '}';
    }

    public static class Builder {
        private final String barcode;
        private int count = 1;
        private long timestamp = System.currentTimeMillis();

        public Builder(String barcode) {
            this.barcode = barcode;
        }

        public Builder count(int count) {
            this.count = count;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public BarcodeData build() {
            return new BarcodeData(this);
        }
    }
}
