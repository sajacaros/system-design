package kr.study.systemdesign.consistenthash;

import java.util.SortedMap;
import java.util.TreeMap;

public class ConsistentHashRing {
    private final SortedMap<Integer, Node.VirtualNode> ring = new TreeMap<>();
    private final HashFunction hashFunction;
    private final int virtualNodes;

    public ConsistentHashRing(HashFunction hashFunction, int virtualNodes) {
        this.hashFunction = hashFunction;
        this.virtualNodes = virtualNodes;
    }

    public void addNode(Node.PhysicalNode physicalNode) {
        for (int i = 0; i < virtualNodes; i++) {
            Node.VirtualNode vNode = new Node.VirtualNode(physicalNode, i);
            int hash = hashFunction.hash(vNode.getId());
            ring.put(hash, vNode);
        }
    }

    public void removeNode(Node.PhysicalNode physicalNode) {
        for (int i = 0; i < virtualNodes; i++) {
            Node.VirtualNode vNode = new Node.VirtualNode(physicalNode, i);
            int hash = hashFunction.hash(vNode.getId());
            ring.remove(hash);
        }
    }

    public Node getNode(String key) {
        if (ring.isEmpty()) return null;
        int hash = hashFunction.hash(key);
        SortedMap<Integer, Node.VirtualNode> tailMap = ring.tailMap(hash);
        int nodeHash = tailMap.isEmpty() ? ring.firstKey() : tailMap.firstKey();
        return ring.get(nodeHash).getNode();
    }
}