package kr.study.kvstore.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public record VectorClock(Map<String, Integer> entries) {

    public VectorClock {
        entries = Map.copyOf(entries);
    }

    public static VectorClock empty() {
        return new VectorClock(Map.of());
    }

    public VectorClock increment(String nodeId) {
        Map<String, Integer> next = new LinkedHashMap<>(entries);
        next.put(nodeId, next.getOrDefault(nodeId, 0) + 1);
        return new VectorClock(next);
    }

    public VectorClock merge(VectorClock other) {
        Map<String, Integer> next = new LinkedHashMap<>(entries);
        other.entries().forEach((nodeId, counter) ->
            next.merge(nodeId, counter, Math::max)
        );
        return new VectorClock(next);
    }

    public boolean descendsFrom(VectorClock other) {
        boolean greater = false;
        for (String nodeId : unionKeys(other)) {
            int left = entries.getOrDefault(nodeId, 0);
            int right = other.entries().getOrDefault(nodeId, 0);
            if (left < right) {
                return false;
            }
            if (left > right) {
                greater = true;
            }
        }
        return greater || entries.equals(other.entries());
    }

    public boolean conflictsWith(VectorClock other) {
        return !descendsFrom(other) && !other.descendsFrom(this);
    }

    private Iterable<String> unionKeys(VectorClock other) {
        Map<String, Boolean> keys = new LinkedHashMap<>();
        entries.keySet().forEach(key -> keys.put(key, true));
        other.entries().keySet().forEach(key -> keys.put(key, true));
        return keys.keySet();
    }
}
