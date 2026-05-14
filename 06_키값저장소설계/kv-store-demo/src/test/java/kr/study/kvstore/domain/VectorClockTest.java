package kr.study.kvstore.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class VectorClockTest {

    @Test
    void descendantDominatesOlderClock() {
        VectorClock older = new VectorClock(Map.of("A", 1, "B", 0));
        VectorClock newer = new VectorClock(Map.of("A", 2, "B", 0));

        assertThat(newer.descendsFrom(older)).isTrue();
        assertThat(older.descendsFrom(newer)).isFalse();
        assertThat(newer.conflictsWith(older)).isFalse();
    }

    @Test
    void independentWritesConflict() {
        VectorClock left = new VectorClock(Map.of("A", 2, "B", 0));
        VectorClock right = new VectorClock(Map.of("A", 1, "B", 1));

        assertThat(left.conflictsWith(right)).isTrue();
        assertThat(right.conflictsWith(left)).isTrue();
    }

    @Test
    void mergeKeepsMaxCounterPerNode() {
        VectorClock left = new VectorClock(Map.of("A", 2, "B", 0));
        VectorClock right = new VectorClock(Map.of("A", 1, "B", 3));

        assertThat(left.merge(right).entries()).containsEntry("A", 2).containsEntry("B", 3);
    }
}
