package kr.study.slidingwindowlog.log;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class SlidingWindowLog {

    public record Entry(long ts, boolean accepted) {}

    public record AcquireResult(boolean admitted,
                                long now,
                                int size,
                                int threshold,
                                long windowSizeMillis,
                                List<Entry> entries) {}

    public record Snapshot(long now,
                           int size,
                           int threshold,
                           long windowSizeMillis,
                           List<Entry> entries) {}

    private final int threshold;
    private final long windowSizeMillis;
    private final Clock clock;
    private final Object lock = new Object();
    private final Deque<Entry> log = new ArrayDeque<>();

    public SlidingWindowLog(int threshold, long windowSizeMillis) {
        this(threshold, windowSizeMillis, Clock.systemDefaultZone());
    }

    public SlidingWindowLog(int threshold, long windowSizeMillis, Clock clock) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("threshold must be positive: " + threshold);
        }
        if (windowSizeMillis <= 0) {
            throw new IllegalArgumentException("windowSizeMillis must be positive: " + windowSizeMillis);
        }
        this.threshold = threshold;
        this.windowSizeMillis = windowSizeMillis;
        this.clock = clock;
    }

    public AcquireResult tryAcquire() {
        synchronized (lock) {
            long now = clock.millis();
            evictExpired(now);
            boolean admitted = log.size() < threshold;
            log.addLast(new Entry(now, admitted));
            return new AcquireResult(admitted, now, log.size(), threshold,
                    windowSizeMillis, snapshotEntries());
        }
    }

    public Snapshot snapshot() {
        synchronized (lock) {
            long now = clock.millis();
            evictExpired(now);
            return new Snapshot(now, log.size(), threshold, windowSizeMillis, snapshotEntries());
        }
    }

    public int threshold() {
        return threshold;
    }

    public long windowSizeMillis() {
        return windowSizeMillis;
    }

    private void evictExpired(long now) {
        long cutoff = now - windowSizeMillis;
        while (!log.isEmpty() && log.peekFirst().ts() < cutoff) {
            log.pollFirst();
        }
    }

    private List<Entry> snapshotEntries() {
        return new ArrayList<>(log);
    }
}
