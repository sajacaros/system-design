package kr.study.leakybucket.bucket;

import java.util.Optional;
import java.util.concurrent.LinkedBlockingDeque;

public class LeakyBucket {

    private final int capacity;
    private final LinkedBlockingDeque<String> queue;

    public LeakyBucket(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
        this.queue = new LinkedBlockingDeque<>(capacity);
    }

    public boolean tryEnqueue(String reqId) {
        return queue.offer(reqId);
    }

    public Optional<String> pollOne() {
        return Optional.ofNullable(queue.poll());
    }

    public int size() {
        return queue.size();
    }

    public int capacity() {
        return capacity;
    }
}
