package kr.study.leakybucket.stream;

import kr.study.leakybucket.ratelimit.LeakyRateLimiter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class LeakyStreamController {

    private final LeakyEventPublisher publisher;
    private final LeakyRateLimiter limiter;

    public LeakyStreamController(LeakyEventPublisher publisher, LeakyRateLimiter limiter) {
        this.publisher = publisher;
        this.limiter = limiter;
    }

    @GetMapping(path = "/api/leaky/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = publisher.subscribe();
        publisher.sendInitial(emitter, limiter.size(), limiter.capacity());
        return emitter;
    }
}
