package kr.study.tokenbucket.bucket;

import java.util.concurrent.atomic.AtomicInteger;

public class TokenBucket {

    private final int capacity;
    private final AtomicInteger tokens;

    public TokenBucket(int capacity, int initialTokens) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        if (initialTokens < 0 || initialTokens > capacity) {
            throw new IllegalArgumentException(
                    "initialTokens out of range [0, " + capacity + "]: " + initialTokens);
        }
        this.capacity = capacity;
        this.tokens = new AtomicInteger(initialTokens);
    }

    public boolean tryConsume() {
        while (true) {
            int current = tokens.get();
            if (current == 0) {
                return false;
            }
            if (tokens.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }

    public void refillOne() {
        while (true) {
            int current = tokens.get();
            if (current >= capacity) {
                return;
            }
            if (tokens.compareAndSet(current, current + 1)) {
                return;
            }
        }
    }

    public int tokens() {
        return tokens.get();
    }

    public int capacity() {
        return capacity;
    }
}
