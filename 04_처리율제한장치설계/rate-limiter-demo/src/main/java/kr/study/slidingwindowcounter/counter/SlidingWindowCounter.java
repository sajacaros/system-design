package kr.study.slidingwindowcounter.counter;

import java.time.Clock;

public class SlidingWindowCounter {

    public record AcquireResult(boolean admitted,
                                long now,
                                long currWindowStartMillis,
                                int prevCount,
                                int currCount,
                                double weighted,
                                double prevWeight,
                                int threshold,
                                long windowSizeMillis) {}

    public record Snapshot(long now,
                           long currWindowStartMillis,
                           int prevCount,
                           int currCount,
                           double weighted,
                           double prevWeight,
                           int threshold,
                           long windowSizeMillis) {}

    private final int threshold;
    private final long windowSizeMillis;
    private final Clock clock;
    private final Object lock = new Object();
    private long currWindowStartMillis;
    private int prevCount;
    private int currCount;

    public SlidingWindowCounter(int threshold, long windowSizeMillis) {
        this(threshold, windowSizeMillis, Clock.systemDefaultZone());
    }

    public SlidingWindowCounter(int threshold, long windowSizeMillis, Clock clock) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("threshold must be positive: " + threshold);
        }
        if (windowSizeMillis <= 0) {
            throw new IllegalArgumentException("windowSizeMillis must be positive: " + windowSizeMillis);
        }
        this.threshold = threshold;
        this.windowSizeMillis = windowSizeMillis;
        this.clock = clock;
        this.currWindowStartMillis = alignedStart(clock.millis());
        this.prevCount = 0;
        this.currCount = 0;
    }

    public AcquireResult tryAcquire() {
        synchronized (lock) {
            long now = clock.millis();
            rollIfExpired(now);
            double prevWeight = computePrevWeight(now);
            double weighted = currCount + prevCount * prevWeight;
            boolean admitted = weighted < threshold;
            if (admitted) {
                currCount++;
                weighted = currCount + prevCount * prevWeight;
            }
            return new AcquireResult(admitted, now, currWindowStartMillis,
                    prevCount, currCount, weighted, prevWeight, threshold, windowSizeMillis);
        }
    }

    public Snapshot snapshot() {
        synchronized (lock) {
            long now = clock.millis();
            rollIfExpired(now);
            double prevWeight = computePrevWeight(now);
            double weighted = currCount + prevCount * prevWeight;
            return new Snapshot(now, currWindowStartMillis, prevCount, currCount,
                    weighted, prevWeight, threshold, windowSizeMillis);
        }
    }

    public int threshold() {
        return threshold;
    }

    public long windowSizeMillis() {
        return windowSizeMillis;
    }

    private void rollIfExpired(long now) {
        long aligned = alignedStart(now);
        if (aligned == currWindowStartMillis) {
            return;
        }
        long diff = aligned - currWindowStartMillis;
        if (diff == windowSizeMillis) {
            prevCount = currCount;
        } else {
            prevCount = 0;
        }
        currWindowStartMillis = aligned;
        currCount = 0;
    }

    private double computePrevWeight(long now) {
        long elapsed = now - currWindowStartMillis;
        if (elapsed >= windowSizeMillis) return 0.0;
        if (elapsed <= 0) return 1.0;
        return 1.0 - ((double) elapsed / windowSizeMillis);
    }

    private long alignedStart(long nowMillis) {
        return (nowMillis / windowSizeMillis) * windowSizeMillis;
    }
}
