package kr.study.fixedwindow.counter;

import java.time.Clock;

public class FixedWindowCounter {

    public record AcquireResult(boolean newWindow,
                                boolean admitted,
                                long windowStartMillis,
                                int count,
                                int threshold) {}

    public record Snapshot(long windowStartMillis, int count, int threshold) {}

    private final int threshold;
    private final long windowSizeMillis;
    private final Clock clock;
    private final Object lock = new Object();
    private long windowStartMillis;
    private int count;

    public FixedWindowCounter(int threshold, long windowSizeMillis) {
        this(threshold, windowSizeMillis, Clock.systemDefaultZone());
    }

    public FixedWindowCounter(int threshold, long windowSizeMillis, Clock clock) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("threshold must be positive: " + threshold);
        }
        if (windowSizeMillis <= 0) {
            throw new IllegalArgumentException("windowSizeMillis must be positive: " + windowSizeMillis);
        }
        this.threshold = threshold;
        this.windowSizeMillis = windowSizeMillis;
        this.clock = clock;
        this.windowStartMillis = alignedStart(clock.millis());
        this.count = 0;
    }

    public AcquireResult tryAcquire() {
        synchronized (lock) {
            boolean newWindow = rollIfExpired();
            boolean admitted = count < threshold;
            if (admitted) {
                count++;
            }
            return new AcquireResult(newWindow, admitted, windowStartMillis, count, threshold);
        }
    }

    public AcquireResult tick() {
        synchronized (lock) {
            boolean newWindow = rollIfExpired();
            return new AcquireResult(newWindow, false, windowStartMillis, count, threshold);
        }
    }

    public Snapshot snapshot() {
        synchronized (lock) {
            return new Snapshot(windowStartMillis, count, threshold);
        }
    }

    public int threshold() {
        return threshold;
    }

    public long windowSizeMillis() {
        return windowSizeMillis;
    }

    private boolean rollIfExpired() {
        long aligned = alignedStart(clock.millis());
        if (aligned != windowStartMillis) {
            windowStartMillis = aligned;
            count = 0;
            return true;
        }
        return false;
    }

    private long alignedStart(long nowMillis) {
        return (nowMillis / windowSizeMillis) * windowSizeMillis;
    }
}
