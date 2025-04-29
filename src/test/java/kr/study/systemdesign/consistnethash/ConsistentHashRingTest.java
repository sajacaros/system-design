package kr.study.systemdesign.consistnethash;

import kr.study.systemdesign.consistenthash.ConsistentHashRing;
import kr.study.systemdesign.consistenthash.HashFunction;
import kr.study.systemdesign.consistenthash.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ConsistentHashRingTest {
    private HashFunction hashFunction;
    private int virtualNodeCount;

    @BeforeEach
    void setup() {
        hashFunction = new HashFunction();
        virtualNodeCount = 1000;
    }

    @Test
    void testBasicFunctionality() {
        ConsistentHashRing ring = new ConsistentHashRing(hashFunction, virtualNodeCount);

        Node.PhysicalNode node1 = new Node.PhysicalNode("Node1");
        Node.PhysicalNode node2 = new Node.PhysicalNode("Node2");
        Node.PhysicalNode node3 = new Node.PhysicalNode("Node3");

        ring.addNode(node1);
        ring.addNode(node2);
        ring.addNode(node3);

        int totalKeys = 100;
        for (int i = 0; i < totalKeys; i++) {
            String key = "key" + i;
            Node assignedNode = ring.getNode(key);
            assertNotNull(assignedNode, "Key should be assigned to a node");
        }
    }

    @Test
    void testDistributionIsFair() {
        ConsistentHashRing ring = new ConsistentHashRing(hashFunction, virtualNodeCount);

        Node.PhysicalNode node1 = new Node.PhysicalNode("Node1");
        Node.PhysicalNode node2 = new Node.PhysicalNode("Node2");
        Node.PhysicalNode node3 = new Node.PhysicalNode("Node3");
        Node.PhysicalNode node4 = new Node.PhysicalNode("Node4");
        Node.PhysicalNode node5 = new Node.PhysicalNode("Node5");

        ring.addNode(node1);
        ring.addNode(node2);
        ring.addNode(node3);
        ring.addNode(node4);
        ring.addNode(node5);

        int totalKeys = 1000;
        Map<String, Integer> distribution = new HashMap<>();

        for (int i = 0; i < totalKeys; i++) {
            String key = "key" + i;
            Node assignedNode = ring.getNode(key);
            distribution.merge(assignedNode.getId(), 1, Integer::sum);
        }

        int avg = totalKeys / 5;
        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            int count = entry.getValue();
            int lowerBound = (int) (avg * 0.8); // 20% 차이 허용
            int upperBound = (int) (avg * 1.2);
            assertTrue(count >= lowerBound && count <= upperBound, "Distribution should be within 20% of average");
        }
    }

    @Test
    void testKeyMovementWhenAddingNode() {
        ConsistentHashRing ring = new ConsistentHashRing(hashFunction, virtualNodeCount);

        Node.PhysicalNode node1 = new Node.PhysicalNode("Node1");
        Node.PhysicalNode node2 = new Node.PhysicalNode("Node2");
        Node.PhysicalNode node3 = new Node.PhysicalNode("Node3");

        ring.addNode(node1);
        ring.addNode(node2);
        ring.addNode(node3);

        int totalKeys = 500;
        Map<String, String> keyToNodeBefore = new HashMap<>();

        for (int i = 0; i < totalKeys; i++) {
            String key = "key" + i;
            keyToNodeBefore.put(key, ring.getNode(key).getId());
        }

        Node.PhysicalNode node4 = new Node.PhysicalNode("Node4");
        ring.addNode(node4);

        int movedKeys = 0;
        for (Map.Entry<String, String> entry : keyToNodeBefore.entrySet()) {
            String key = entry.getKey();
            String oldNodeId = entry.getValue();
            String newNodeId = ring.getNode(key).getId();
            if (!oldNodeId.equals(newNodeId)) {
                movedKeys++;
            }
        }

        double movementRatio = (double) movedKeys / totalKeys;
        assertTrue(movementRatio <= 0.25, "Key movement should be <= 25% after adding a node");
    }

    @Test
    void testKeyMovementWhenRemovingNode() {
        ConsistentHashRing ring = new ConsistentHashRing(hashFunction, virtualNodeCount);

        Node.PhysicalNode node1 = new Node.PhysicalNode("Node1");
        Node.PhysicalNode node2 = new Node.PhysicalNode("Node2");
        Node.PhysicalNode node3 = new Node.PhysicalNode("Node3");
        Node.PhysicalNode node4 = new Node.PhysicalNode("Node4");

        ring.addNode(node1);
        ring.addNode(node2);
        ring.addNode(node3);
        ring.addNode(node4);

        int totalKeys = 500;
        Map<String, String> keyToNodeBefore = new HashMap<>();

        for (int i = 0; i < totalKeys; i++) {
            String key = "key" + i;
            keyToNodeBefore.put(key, ring.getNode(key).getId());
        }

        // 특정 노드 삭제
        ring.removeNode(node3);

        for (Map.Entry<String, String> entry : keyToNodeBefore.entrySet()) {
            String key = entry.getKey();
            String oldNodeId = entry.getValue();
            String newNodeId = ring.getNode(key).getId();
            if (!oldNodeId.equals(newNodeId)) {
                assertEquals("Node3", oldNodeId, "Only keys belonging to removed node should be reassigned");
            }
        }
    }

    @Test
    void testVirtualNodeEffectiveness() {
        int[] virtualNodeCounts = {10, 100, 1000};
        int totalKeys = 1000;

        Map<Integer, Double> stdDeviations = new HashMap<>();

        for (int vNodeCount : virtualNodeCounts) {
            ConsistentHashRing ring = new ConsistentHashRing(hashFunction, vNodeCount);

            Node.PhysicalNode node1 = new Node.PhysicalNode("Node1");
            Node.PhysicalNode node2 = new Node.PhysicalNode("Node2");
            Node.PhysicalNode node3 = new Node.PhysicalNode("Node3");
            Node.PhysicalNode node4 = new Node.PhysicalNode("Node4");

            ring.addNode(node1);
            ring.addNode(node2);
            ring.addNode(node3);
            ring.addNode(node4);

            Map<String, Integer> distribution = new HashMap<>();

            for (int i = 0; i < totalKeys; i++) {
                String key = "key" + i;
                Node assignedNode = ring.getNode(key);
                distribution.merge(assignedNode.getId(), 1, Integer::sum);
            }

            // 표준편차 계산
            double mean = totalKeys / 4.0;
            double variance = distribution.values().stream()
                    .mapToDouble(count -> (count - mean) * (count - mean))
                    .average()
                    .orElse(0.0);
            double stdDev = Math.sqrt(variance);
            stdDeviations.put(vNodeCount, stdDev);
        }

        System.out.println("Standard deviations with virtual nodes:");
        stdDeviations.forEach((vNodes, stdDev) -> {
            System.out.println("Virtual Nodes: " + vNodes + ", StdDev: " + stdDev);
        });

        // 가상 노드 수가 많아질수록 stdDev가 감소해야 함
        assertTrue(stdDeviations.get(100) < stdDeviations.get(10));
        assertTrue(stdDeviations.get(1000) < stdDeviations.get(100));
    }
}
