package kr.study.fixedwindow.ratelimit;

import kr.study.fixedwindow.counter.FixedWindowCounter;
import kr.study.fixedwindow.stream.FixedWindowEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class FixedWindowRateLimiter {

    private final FixedWindowCounter counter;
    private final FixedWindowEventPublisher events;

    public FixedWindowRateLimiter(FixedWindowCounter counter, FixedWindowEventPublisher events) {
        this.counter = counter;
        this.events = events;
    }

    public boolean tryAcquire() {
        FixedWindowCounter.AcquireResult r = counter.tryAcquire();
        if (r.newWindow()) {
            events.publish("window_open", r.windowStartMillis(), 0, r.threshold());
        }
        events.publish(r.admitted() ? "accepted" : "rejected",
                r.windowStartMillis(), r.count(), r.threshold());
        return r.admitted();
    }

    public void tick() {
        FixedWindowCounter.AcquireResult r = counter.tick();
        if (r.newWindow()) {
            events.publish("window_open", r.windowStartMillis(), r.count(), r.threshold());
        }
    }

    public FixedWindowCounter.Snapshot snapshot() {
        return counter.snapshot();
    }

    public int threshold() {
        return counter.threshold();
    }

    public long windowSizeMillis() {
        return counter.windowSizeMillis();
    }
}
