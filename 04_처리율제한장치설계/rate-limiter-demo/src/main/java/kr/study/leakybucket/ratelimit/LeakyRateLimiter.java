package kr.study.leakybucket.ratelimit;

import kr.study.leakybucket.bucket.LeakyBucket;
import kr.study.leakybucket.stream.LeakyEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LeakyRateLimiter {

    private final LeakyBucket bucket;
    private final LeakyEventPublisher events;

    public LeakyRateLimiter(LeakyBucket bucket, LeakyEventPublisher events) {
        this.bucket = bucket;
        this.events = events;
    }

    public Outcome tryEnqueue() {
        String reqId = UUID.randomUUID().toString().substring(0, 8);
        boolean ok = bucket.tryEnqueue(reqId);
        if (ok) {
            events.publish("accepted", reqId, bucket.size(), bucket.capacity());
            return new Outcome.Accepted(reqId);
        }
        events.publish("rejected", null, bucket.size(), bucket.capacity());
        return new Outcome.Rejected();
    }

    public void leakOne() {
        bucket.pollOne().ifPresent(reqId ->
                events.publish("processed", reqId, bucket.size(), bucket.capacity()));
    }

    public int size() {
        return bucket.size();
    }

    public int capacity() {
        return bucket.capacity();
    }
}
