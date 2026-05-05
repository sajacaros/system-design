package kr.study.fixedwindow.stream;

import kr.study.fixedwindow.counter.FixedWindowCounter;
import kr.study.fixedwindow.ratelimit.FixedWindowRateLimiter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class FixedWindowStreamController {

    private final FixedWindowEventPublisher publisher;
    private final FixedWindowRateLimiter limiter;

    public FixedWindowStreamController(FixedWindowEventPublisher publisher, FixedWindowRateLimiter limiter) {
        this.publisher = publisher;
        this.limiter = limiter;
    }

    @GetMapping(path = "/api/fixed/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = publisher.subscribe();
        FixedWindowCounter.Snapshot s = limiter.snapshot();
        publisher.sendInitial(emitter, s.windowStartMillis(), s.count(), s.threshold());
        return emitter;
    }
}
