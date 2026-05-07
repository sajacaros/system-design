package kr.study.slidingwindowcounter.ratelimit;

import kr.study.slidingwindowcounter.counter.SlidingWindowCounter;
import kr.study.slidingwindowcounter.stream.SlidingWindowCounterEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SlidingWindowCounterRateLimiter {

    private final SlidingWindowCounter counter;
    private final SlidingWindowCounterEventPublisher events;

    public SlidingWindowCounterRateLimiter(SlidingWindowCounter counter,
                                           SlidingWindowCounterEventPublisher events) {
        this.counter = counter;
        this.events = events;
    }

    public boolean tryAcquire() {
        SlidingWindowCounter.AcquireResult r = counter.tryAcquire();
        events.publish(r.admitted() ? "accepted" : "rejected",
                r.now(), r.currWindowStartMillis(), r.prevCount(), r.currCount(),
                r.weighted(), r.prevWeight(), r.threshold(), r.windowSizeMillis());
        return r.admitted();
    }

    public void tick() {
        SlidingWindowCounter.Snapshot s = counter.snapshot();
        events.publish("tick", s.now(), s.currWindowStartMillis(), s.prevCount(), s.currCount(),
                s.weighted(), s.prevWeight(), s.threshold(), s.windowSizeMillis());
    }

    public SlidingWindowCounter.Snapshot snapshot() {
        return counter.snapshot();
    }

    public int threshold() {
        return counter.threshold();
    }

    public long windowSizeMillis() {
        return counter.windowSizeMillis();
    }
}
