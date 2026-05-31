package kr.study.urlshortener.domain;

import java.time.Instant;

public class SnowflakeIdGenerator {
    public static final long CUSTOM_EPOCH_MILLIS = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli();
    public static final int UNUSED_SIGN_BITS = 1;
    public static final int TIMESTAMP_BITS = 41;
    public static final int DATACENTER_BITS = 5;
    public static final int WORKER_BITS = 5;
    public static final int SEQUENCE_BITS = 12;

    private static final long MAX_DATACENTER_ID = (1L << DATACENTER_BITS) - 1;
    private static final long MAX_WORKER_ID = (1L << WORKER_BITS) - 1;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
    private static final int WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final int DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_BITS;
    private static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS + DATACENTER_BITS;

    private final long datacenterId;
    private final long workerId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long datacenterId, long workerId) {
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException("datacenterId must be between 0 and " + MAX_DATACENTER_ID);
        }
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be between 0 and " + MAX_WORKER_ID);
        }
        this.datacenterId = datacenterId;
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long timestamp = currentTimeMillis();

        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards. Refusing to generate id for "
                + (lastTimestamp - timestamp) + " ms");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitUntilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - CUSTOM_EPOCH_MILLIS) << TIMESTAMP_SHIFT)
            | (datacenterId << DATACENTER_ID_SHIFT)
            | (workerId << WORKER_ID_SHIFT)
            | sequence;
    }

    public DecodedId decode(long id) {
        long sequence = id & SEQUENCE_MASK;
        long workerId = (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
        long datacenterId = (id >> DATACENTER_ID_SHIFT) & MAX_DATACENTER_ID;
        long timestamp = (id >> TIMESTAMP_SHIFT) + CUSTOM_EPOCH_MILLIS;
        return new DecodedId(timestamp, datacenterId, workerId, sequence);
    }

    private long waitUntilNextMillis(long lastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            Thread.onSpinWait();
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    public record DecodedId(
        long timestampMillis,
        long datacenterId,
        long workerId,
        long sequence
    ) {
        public Instant instant() {
            return Instant.ofEpochMilli(timestampMillis);
        }
    }
}
