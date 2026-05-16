package kr.study.kvstore.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConsistentHashRingTest {

    @Test
    void selectsDistinctPhysicalNodesForReplicas() {
        ConsistentHashRing ring = new ConsistentHashRing(
            List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J"),
            16
        );

        List<String> replicas = ring.replicasFor("cart:42", 5);

        assertThat(replicas).hasSize(5).doesNotHaveDuplicates();
    }

    @Test
    void replicaSelectionIsIndependentOfInputNodeOrder() {
        ConsistentHashRing left = new ConsistentHashRing(List.of("A", "B", "C", "D", "E"), 16);
        ConsistentHashRing right = new ConsistentHashRing(List.of("E", "D", "C", "B", "A"), 16);

        assertThat(right.replicasFor("cart:42", 3)).isEqualTo(left.replicasFor("cart:42", 3));
    }

    @Test
    void clampsReplicaCountToPhysicalNodeCount() {
        ConsistentHashRing ring = new ConsistentHashRing(List.of("A", "B", "C"), 16);

        assertThat(ring.replicasFor("cart:42", 5)).hasSize(3).doesNotHaveDuplicates();
    }
}
