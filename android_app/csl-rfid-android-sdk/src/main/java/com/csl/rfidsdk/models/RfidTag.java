package com.csl.rfidsdk.models;

/**
 * Represents an RFID tag read from the reader
 */
public class RfidTag {
    private final String epc;
    private final double rssi;
    private final int count;
    private final long timestamp;
    private final String tid;
    private final String userData;
    private final int pc;
    private final int phase;
    private final int channel;

    private RfidTag(Builder builder) {
        this.epc = builder.epc;
        this.rssi = builder.rssi;
        this.count = builder.count;
        this.timestamp = builder.timestamp;
        this.tid = builder.tid;
        this.userData = builder.userData;
        this.pc = builder.pc;
        this.phase = builder.phase;
        this.channel = builder.channel;
    }

    public String getEpc() {
        return epc;
    }

    public double getRssi() {
        return rssi;
    }

    public int getCount() {
        return count;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getTid() {
        return tid;
    }

    public String getUserData() {
        return userData;
    }

    public int getPc() {
        return pc;
    }

    public int getPhase() {
        return phase;
    }

    public int getChannel() {
        return channel;
    }

    /**
     * Create a new RfidTag with incremented count
     */
    public RfidTag withCount(int newCount) {
        return new Builder(this.epc)
                .rssi(this.rssi)
                .count(newCount)
                .timestamp(this.timestamp)
                .tid(this.tid)
                .userData(this.userData)
                .pc(this.pc)
                .phase(this.phase)
                .channel(this.channel)
                .build();
    }

    /**
     * Create a new RfidTag with updated RSSI
     */
    public RfidTag withRssi(double newRssi) {
        return new Builder(this.epc)
                .rssi(newRssi)
                .count(this.count)
                .timestamp(this.timestamp)
                .tid(this.tid)
                .userData(this.userData)
                .pc(this.pc)
                .phase(this.phase)
                .channel(this.channel)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RfidTag rfidTag = (RfidTag) o;
        return epc != null ? epc.equals(rfidTag.epc) : rfidTag.epc == null;
    }

    @Override
    public int hashCode() {
        return epc != null ? epc.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "RfidTag{" +
                "epc='" + epc + '\'' +
                ", rssi=" + rssi +
                ", count=" + count +
                '}';
    }

    public static class Builder {
        private final String epc;
        private double rssi = -90.0;
        private int count = 1;
        private long timestamp = System.currentTimeMillis();
        private String tid = "";
        private String userData = "";
        private int pc = 0;
        private int phase = 0;
        private int channel = 0;

        public Builder(String epc) {
            this.epc = epc;
        }

        public Builder rssi(double rssi) {
            this.rssi = rssi;
            return this;
        }

        public Builder count(int count) {
            this.count = count;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder tid(String tid) {
            this.tid = tid;
            return this;
        }

        public Builder userData(String userData) {
            this.userData = userData;
            return this;
        }

        public Builder pc(int pc) {
            this.pc = pc;
            return this;
        }

        public Builder phase(int phase) {
            this.phase = phase;
            return this;
        }

        public Builder channel(int channel) {
            this.channel = channel;
            return this;
        }

        public RfidTag build() {
            return new RfidTag(this);
        }
    }
}
