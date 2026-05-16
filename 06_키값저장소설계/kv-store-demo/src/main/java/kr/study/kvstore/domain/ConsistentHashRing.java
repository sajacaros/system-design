package kr.study.kvstore.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

public class ConsistentHashRing {

    private final NavigableMap<Long, RingToken> ring;
    private final List<String> nodeIds;
    private final int virtualNodesPerNode;

    public ConsistentHashRing(List<String> nodeIds, int virtualNodesPerNode) {
        this.nodeIds = nodeIds.stream()
            .distinct()
            .sorted()
            .toList();
        this.virtualNodesPerNode = Math.max(1, virtualNodesPerNode);
        this.ring = buildRing(this.nodeIds, this.virtualNodesPerNode);
    }

    public List<String> replicasFor(String key, int replicaCount) {
        if (ring.isEmpty() || replicaCount <= 0) {
            return List.of();
        }

        int limit = Math.min(replicaCount, nodeIds.size());
        LinkedHashSet<String> replicas = new LinkedHashSet<>();
        long keyHash = hash(key);
        List<Long> tokenHashes = orderedTokenHashesFrom(keyHash);
        for (Long tokenHash : tokenHashes) {
            replicas.add(ring.get(tokenHash).nodeId());
            if (replicas.size() == limit) {
                break;
            }
        }
        return List.copyOf(replicas);
    }

    public long hashOf(String key) {
        return hash(key);
    }

    public int nodeCount() {
        return nodeIds.size();
    }

    public int tokenCount() {
        return ring.size();
    }

    public int virtualNodesPerNode() {
        return virtualNodesPerNode;
    }

    private NavigableMap<Long, RingToken> buildRing(List<String> nodes, int replicas) {
        NavigableMap<Long, RingToken> next = new TreeMap<>();
        for (String nodeId : nodes) {
            for (int replicaIndex = 0; replicaIndex < replicas; replicaIndex++) {
                String tokenId = nodeId + "-v" + replicaIndex;
                long position = hash(tokenId);
                while (next.containsKey(position)) {
                    position = position == Long.MAX_VALUE ? 0 : position + 1;
                }
                next.put(position, new RingToken(tokenId, nodeId));
            }
        }
        return next;
    }

    private List<Long> orderedTokenHashesFrom(long keyHash) {
        List<Long> tokenHashes = new ArrayList<>(ring.tailMap(keyHash, true).keySet());
        tokenHashes.addAll(ring.headMap(keyHash, false).keySet());
        tokenHashes.sort(Comparator.comparingLong(hash -> distanceFrom(keyHash, hash)));
        return tokenHashes;
    }

    private long distanceFrom(long keyHash, long tokenHash) {
        if (tokenHash >= keyHash) {
            return tokenHash - keyHash;
        }
        return Long.MAX_VALUE - keyHash + tokenHash;
    }

    private long hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            long value = 0L;
            for (int index = 0; index < 8; index++) {
                value = (value << 8) | (bytes[index] & 0xffL);
            }
            return value & Long.MAX_VALUE;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private record RingToken(String tokenId, String nodeId) {
    }
}
