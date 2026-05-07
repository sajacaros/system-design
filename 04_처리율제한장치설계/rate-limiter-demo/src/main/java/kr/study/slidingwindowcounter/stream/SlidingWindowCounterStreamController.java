package kr.study.slidingwindowcounter.stream;

import kr.study.slidingwindowcounter.counter.SlidingWindowCounter;
import kr.study.slidingwindowcounter.ratelimit.SlidingWindowCounterRateLimiter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class SlidingWindowCounterStreamController {

    private final SlidingWindowCounterEventPublisher publisher;
    private final SlidingWindowCounterRateLimiter limiter;

    public SlidingWindowCounterStreamController(SlidingWindowCounterEventPublisher publisher,
                                                SlidingWindowCounterRateLimiter limiter) {
        this.publisher = publisher;
        this.limiter = limiter;
    }

    @GetMapping(path = "/api/sliding-counter/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = publisher.subscribe();
        SlidingWindowCounter.Snapshot s = limiter.snapshot();
        publisher.sendInitial(emitter, s.now(), s.currWindowStartMillis(),
                s.prevCount(), s.currCount(), s.weighted(), s.prevWeight(),
                s.threshold(), s.windowSizeMillis());
        return emitter;
    }
}
