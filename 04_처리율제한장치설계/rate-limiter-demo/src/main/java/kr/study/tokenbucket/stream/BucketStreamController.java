package kr.study.tokenbucket.stream;

import kr.study.tokenbucket.ratelimit.RateLimiter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class BucketStreamController {

    private final BucketEventPublisher publisher;
    private final RateLimiter limiter;

    public BucketStreamController(BucketEventPublisher publisher, RateLimiter limiter) {
        this.publisher = publisher;
        this.limiter = limiter;
    }

    @GetMapping(path = "/api/token/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = publisher.subscribe();
        publisher.sendInitial(emitter, limiter.tokens(), limiter.capacity());
        return emitter;
    }
}
