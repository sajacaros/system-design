package kr.study.slidingwindowlog.ratelimit;

import kr.study.slidingwindowlog.log.SlidingWindowLog;
import kr.study.slidingwindowlog.stream.SlidingWindowLogEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SlidingWindowLogRateLimiter {

    private final SlidingWindowLog log;
    private final SlidingWindowLogEventPublisher events;

    public SlidingWindowLogRateLimiter(SlidingWindowLog log, SlidingWindowLogEventPublisher events) {
        this.log = log;
        this.events = events;
    }

    public boolean tryAcquire() {
        SlidingWindowLog.AcquireResult r = log.tryAcquire();
        events.publish(r.admitted() ? "accepted" : "rejected",
                r.now(), r.size(), r.threshold(), r.windowSizeMillis(), r.entries());
        return r.admitted();
    }

    public SlidingWindowLog.Snapshot snapshot() {
        return log.snapshot();
    }

    public int threshold() {
        return log.threshold();
    }

    public long windowSizeMillis() {
        return log.windowSizeMillis();
    }
}
