package kr.study.tokenbucket.ratelimit;

import kr.study.tokenbucket.bucket.TokenBucket;
import kr.study.tokenbucket.stream.BucketEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

    private final TokenBucket bucket;
    private final BucketEventPublisher events;

    public RateLimiter(TokenBucket bucket, BucketEventPublisher events) {
        this.bucket = bucket;
        this.events = events;
    }

    public boolean tryAcquire() {
        boolean ok = bucket.tryConsume();
        events.publish(ok ? "consume" : "rejected", bucket.tokens());
        return ok;
    }

    public void refill() {
        bucket.refillOne();
        events.publish("refill", bucket.tokens());
    }

    public int tokens() {
        return bucket.tokens();
    }

    public int capacity() {
        return bucket.capacity();
    }
}
