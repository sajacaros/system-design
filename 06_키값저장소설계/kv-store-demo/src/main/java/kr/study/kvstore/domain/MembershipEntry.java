package kr.study.kvstore.domain;

import java.time.Instant;

public record MembershipEntry(
    String nodeId,
    String endpoint,
    int heartbeat,
    NodeStatus status,
    Instant lastSeen
) {

    public MembershipEntry withHeartbeat(int nextHeartbeat, Instant seenAt) {
        return new MembershipEntry(nodeId, endpoint, nextHeartbeat, NodeStatus.ALIVE, seenAt);
    }

    public MembershipEntry withStatus(NodeStatus nextStatus) {
        return new MembershipEntry(nodeId, endpoint, heartbeat, nextStatus, lastSeen);
    }
}
