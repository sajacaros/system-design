package kr.study.slidingwindowlog.stream;

import kr.study.slidingwindowlog.ratelimit.SlidingWindowLogRateLimiter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class SlidingWindowLogStreamController {

    private final SlidingWindowLogEventPublisher publisher;
    private final SlidingWindowLogRateLimiter limiter;

    public SlidingWindowLogStreamController(SlidingWindowLogEventPublisher publisher,
                                            SlidingWindowLogRateLimiter limiter) {
        this.publisher = publisher;
        this.limiter = limiter;
    }

    @GetMapping(path = "/api/sliding-log/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = publisher.subscribe();
        publisher.sendInitial(emitter, limiter.snapshot());
        return emitter;
    }
}
