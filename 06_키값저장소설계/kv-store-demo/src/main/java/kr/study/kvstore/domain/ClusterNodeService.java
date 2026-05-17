package kr.study.kvstore.domain;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import kr.study.kvstore.config.DemoProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

@Service
public class ClusterNodeService {

    private final DemoProperties properties;
    private final RestTemplate restTemplate;
    private final String nodeId;
    private final Map<String, Peer> peersByNodeId;
    private final ConsistentHashRing hashRing;
    private final Map<String, List<VersionedValue>> store = new ConcurrentHashMap<>();
    private final Map<String, List<HintedHandoff>> pendingHints = new ConcurrentHashMap<>();
    private final Map<String, MembershipEntry> membership = new ConcurrentHashMap<>();
    private final ArrayDeque<String> events = new ArrayDeque<>();
    private final AtomicInteger heartbeat = new AtomicInteger();
    private volatile int writeQuorum;
    private volatile int readQuorum;
    private volatile boolean available = true;

    public ClusterNodeService(DemoProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.nodeId = properties.nodeId();
        this.peersByNodeId = parsePeers(properties.peers());
        this.hashRing = new ConsistentHashRing(clusterNodeIds(), properties.virtualNodes());
        this.writeQuorum = clamp(properties.writeQuorum(), 1, effectiveReplicationFactor());
        this.readQuorum = clamp(properties.readQuorum(), 1, effectiveReplicationFactor());
        Instant now = Instant.now();
        membership.put(nodeId, new MembershipEntry(nodeId, "self", heartbeat.get(), NodeStatus.ALIVE, now));
        peersByNodeId.values().forEach(peer ->
            membership.put(peer.nodeId(), new MembershipEntry(peer.nodeId(), peer.endpoint(), 0, NodeStatus.ALIVE, now))
        );
        log("node " + nodeId + " started with peers " + peersByNodeId.keySet() + ", hash ring tokens " + hashRing.tokenCount());
    }

    public String nodeId() {
        return nodeId;
    }

    public boolean isAvailable() {
        return available;
    }

    @Scheduled(fixedDelay = 1000)
    public void tickHeartbeat() {
        if (!available) {
            return;
        }
        int next = heartbeat.incrementAndGet();
        membership.put(nodeId, new MembershipEntry(nodeId, "self", next, NodeStatus.ALIVE, Instant.now()));
        refreshFailureStatus();
    }

    @Scheduled(fixedDelay = 2500, initialDelay = 1200)
    public void automaticGossip() {
        if (available) {
            gossipRound();
        }
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 1500)
    public void automaticHintedHandoff() {
        if (!available || pendingHints.isEmpty()) {
            return;
        }
        deliverLocalHints();
    }

    public PutResult put(PutCommand command) {
        requireAvailable();
        List<ReplicaTarget> targets = replicaTargets(command.key());
        VectorClock baseClock = readVersionsForClock(command.key(), targets).stream()
            .map(VersionedValue::clock)
            .reduce(VectorClock.empty(), VectorClock::merge);
        VectorClock nextClock = baseClock.increment(nodeId);
        VersionedValue version = new VersionedValue(
            nodeId + "-" + UUID.randomUUID().toString().substring(0, 8),
            command.key(),
            command.value(),
            nextClock,
            nodeId,
            Instant.now()
        );

        int ackCount = 0;
        List<ReplicaAck> acks = new ArrayList<>();
        for (ReplicaTarget target : targets) {
            try {
                if (target.local()) {
                    saveVersion(version);
                    acks.add(new ReplicaAck(target.nodeId(), true, "local replica"));
                } else {
                    restTemplate.postForEntity(
                        target.endpoint() + "/internal/replica/write",
                        new ReplicaWriteRequest(version),
                        Void.class
                    );
                    acks.add(new ReplicaAck(target.nodeId(), true, "replicated"));
                    markPeerAlive(target.nodeId());
                }
                ackCount++;
            } catch (RestClientException exception) {
                acks.add(new ReplicaAck(target.nodeId(), false, "unreachable"));
                addHint(target.nodeId(), version);
                markPeerSuspect(target.nodeId());
            }
        }

        int requiredWriteQuorum = writeQuorum;
        boolean quorumReached = ackCount >= requiredWriteQuorum;
        log("PUT " + command.key() + "=" + command.value() + " replicas " + targets.stream().map(ReplicaTarget::nodeId).toList()
            + " -> " + ackCount + "/" + requiredWriteQuorum + " write quorum");
        return new PutResult(command.key(), version, ackCount, requiredWriteQuorum, quorumReached, acks, nodeId, nodeId, false);
    }

    public PutResult putOnNode(String targetNodeId, PutCommand command) {
        if (nodeId.equals(targetNodeId)) {
            return put(command);
        }
        Peer peer = peersByNodeId.get(targetNodeId);
        if (peer == null) {
            throw new IllegalArgumentException("unknown node: " + targetNodeId);
        }
        try {
            PutResult result = Objects.requireNonNull(restTemplate.postForEntity(
                peer.endpoint() + "/api/kv",
                command,
                PutResult.class
            ).getBody());
            markPeerAlive(targetNodeId);
            return result.withRequestedCoordinator(targetNodeId, false);
        } catch (RestClientException exception) {
            markPeerSuspect(targetNodeId);
            log("coordinator " + targetNodeId + " unavailable, fallback to " + nodeId);
            return put(command).withRequestedCoordinator(targetNodeId, true);
        }
    }

    public ReadResult get(String key) {
        requireAvailable();
        List<ReplicaRead> reads = new ArrayList<>();
        int successfulReads = 0;
        for (ReplicaTarget target : replicaTargets(key)) {
            try {
                if (target.local()) {
                    reads.add(new ReplicaRead(target.nodeId(), true, siblings(key), "local read"));
                } else {
                    String encodedKey = UriUtils.encodePathSegment(key, java.nio.charset.StandardCharsets.UTF_8);
                    ResponseEntity<ReplicaReadResponse> response = restTemplate.getForEntity(
                        target.endpoint() + "/internal/replica/read/" + encodedKey,
                        ReplicaReadResponse.class
                    );
                    ReplicaReadResponse body = Objects.requireNonNull(response.getBody());
                    reads.add(new ReplicaRead(target.nodeId(), true, body.versions(), "remote read"));
                    markPeerAlive(target.nodeId());
                }
                successfulReads++;
                if (successfulReads >= readQuorum) {
                    break;
                }
            } catch (RestClientException exception) {
                reads.add(new ReplicaRead(target.nodeId(), false, List.of(), "unreachable"));
                markPeerSuspect(target.nodeId());
            }
        }

        List<VersionedValue> versions = reads.stream()
            .filter(ReplicaRead::ok)
            .flatMap(read -> read.versions().stream())
            .toList();
        List<VersionedValue> reconciled = reconcile(versions);
        int requiredReadQuorum = readQuorum;
        boolean quorumReached = successfulReads >= requiredReadQuorum;
        boolean conflict = hasConflict(reconciled);
        log("GET " + key + " replicas " + reads.stream().map(ReplicaRead::nodeId).toList()
            + " -> " + successfulReads + "/" + requiredReadQuorum + " read quorum, versions " + reconciled.size());
        return new ReadResult(key, reconciled, reads, successfulReads, requiredReadQuorum, quorumReached, conflict, nodeId, nodeId, false);
    }

    public ReadResult getFromNode(String targetNodeId, String key) {
        if (nodeId.equals(targetNodeId)) {
            return get(key);
        }
        Peer peer = peersByNodeId.get(targetNodeId);
        if (peer == null) {
            throw new IllegalArgumentException("unknown node: " + targetNodeId);
        }
        String encodedKey = UriUtils.encodePathSegment(key, java.nio.charset.StandardCharsets.UTF_8);
        try {
            ReadResult result = Objects.requireNonNull(restTemplate.getForEntity(
                peer.endpoint() + "/api/kv/" + encodedKey,
                ReadResult.class
            ).getBody());
            markPeerAlive(targetNodeId);
            return result.withRequestedCoordinator(targetNodeId, false);
        } catch (RestClientException exception) {
            markPeerSuspect(targetNodeId);
            log("coordinator " + targetNodeId + " unavailable, fallback to " + nodeId);
            return get(key).withRequestedCoordinator(targetNodeId, true);
        }
    }

    public boolean saveReplica(ReplicaWriteRequest request) {
        requireAvailable();
        boolean saved = saveVersion(request.version());
        log("replica write " + request.version().key() + " from " + request.version().writerNodeId());
        return saved;
    }

    public ReplicaReadResponse readReplica(String key) {
        requireAvailable();
        return new ReplicaReadResponse(nodeId, siblings(key));
    }

    public GossipResult gossipRound() {
        requireAvailable();
        refreshFailureStatus();
        List<GossipPeerResult> results = new ArrayList<>();
        GossipMessage message = new GossipMessage(nodeId, membershipSnapshot());
        Peer peer = randomGossipPeer();
        if (peer == null) {
            log("gossip round skipped: no peers");
            return new GossipResult(nodeId, membershipSnapshot(), results);
        }
        try {
            ResponseEntity<GossipMessage> response = restTemplate.postForEntity(
                peer.endpoint() + "/internal/gossip",
                message,
                GossipMessage.class
            );
            mergeMembership(Objects.requireNonNull(response.getBody()).membership());
            markPeerAlive(peer.nodeId());
            results.add(new GossipPeerResult(peer.nodeId(), true, "membership exchanged"));
        } catch (RestClientException exception) {
            markPeerSuspect(peer.nodeId());
            results.add(new GossipPeerResult(peer.nodeId(), false, "unreachable"));
        }
        log("gossip round -> random peer " + peer.nodeId() + " " + (results.get(0).ok() ? "reached" : "unreachable"));
        return new GossipResult(nodeId, membershipSnapshot(), results);
    }

    public GossipMessage receiveGossip(GossipMessage message) {
        requireAvailable();
        mergeMembership(message.membership());
        markPeerAlive(message.fromNodeId());
        log("gossip received from " + message.fromNodeId());
        return new GossipMessage(nodeId, membershipSnapshot());
    }

    public void setAvailable(boolean nextAvailable) {
        this.available = nextAvailable;
        if (nextAvailable) {
            membership.put(nodeId, new MembershipEntry(nodeId, "self", heartbeat.incrementAndGet(), NodeStatus.ALIVE, Instant.now()));
            log("node resumed");
        } else {
            log("node paused by demo control");
        }
    }

    public AvailabilityResult setNodeAvailability(String targetNodeId, boolean nextAvailable) {
        if (nodeId.equals(targetNodeId)) {
            setAvailable(nextAvailable);
            return new AvailabilityResult(targetNodeId, nextAvailable, true, "local node updated");
        }
        Peer peer = peersByNodeId.get(targetNodeId);
        if (peer == null) {
            return new AvailabilityResult(targetNodeId, nextAvailable, false, "unknown node");
        }
        try {
            restTemplate.postForEntity(
                peer.endpoint() + "/internal/admin/availability",
                new AvailabilityCommand(nextAvailable),
                Void.class
            );
            if (nextAvailable) {
                markPeerAlive(targetNodeId);
            } else {
                markPeerSuspect(targetNodeId);
            }
            log("demo control set " + targetNodeId + " available=" + nextAvailable);
            return new AvailabilityResult(targetNodeId, nextAvailable, true, "remote node updated");
        } catch (RestClientException exception) {
            markPeerSuspect(targetNodeId);
            return new AvailabilityResult(targetNodeId, nextAvailable, false, "remote node unreachable");
        }
    }

    public QuorumSettingsResult setClusterQuorum(QuorumSettingsCommand command) {
        QuorumSettings settings = applyQuorum(command);
        List<ReplicaAck> acks = new ArrayList<>();
        acks.add(new ReplicaAck(nodeId, true, "local quorum updated"));
        for (Peer peer : peersByNodeId.values()) {
            try {
                restTemplate.postForEntity(
                    peer.endpoint() + "/internal/admin/quorum",
                    command,
                    Void.class
                );
                acks.add(new ReplicaAck(peer.nodeId(), true, "remote quorum updated"));
                markPeerAlive(peer.nodeId());
            } catch (RestClientException exception) {
                acks.add(new ReplicaAck(peer.nodeId(), false, "remote node unreachable"));
                markPeerSuspect(peer.nodeId());
            }
        }
        log("quorum changed to W=" + settings.writeQuorum() + ", R=" + settings.readQuorum());
        return new QuorumSettingsResult(
            effectiveReplicationFactor(),
            settings.writeQuorum(),
            settings.readQuorum(),
            settings.writeQuorum() + settings.readQuorum() > effectiveReplicationFactor(),
            acks
        );
    }

    public QuorumSettings applyQuorum(QuorumSettingsCommand command) {
        int nextWriteQuorum = clamp(command.writeQuorum(), 1, effectiveReplicationFactor());
        int nextReadQuorum = clamp(command.readQuorum(), 1, effectiveReplicationFactor());
        writeQuorum = nextWriteQuorum;
        readQuorum = nextReadQuorum;
        return new QuorumSettings(effectiveReplicationFactor(), nextWriteQuorum, nextReadQuorum);
    }

    public HintedHandoffResult runClusterHintedHandoff() {
        requireAvailable();
        List<HintDelivery> deliveries = new ArrayList<>();
        List<ReplicaAck> nodes = new ArrayList<>();
        HintedHandoffResult localResult = runLocalHintedHandoff();
        deliveries.addAll(localResult.deliveries());
        nodes.add(new ReplicaAck(nodeId, true, "local handoff checked"));
        for (Peer peer : peersByNodeId.values()) {
            try {
                HintedHandoffResult response = restTemplate.postForEntity(
                    peer.endpoint() + "/internal/admin/handoff",
                    null,
                    HintedHandoffResult.class
                ).getBody();
                if (response != null) {
                    deliveries.addAll(response.deliveries());
                }
                nodes.add(new ReplicaAck(peer.nodeId(), true, "remote handoff checked"));
                markPeerAlive(peer.nodeId());
            } catch (RestClientException exception) {
                nodes.add(new ReplicaAck(peer.nodeId(), false, "remote node unreachable"));
                markPeerSuspect(peer.nodeId());
            }
        }
        log("cluster hinted handoff -> " + deliveries.stream().filter(HintDelivery::delivered).count() + " delivered");
        return new HintedHandoffResult(List.copyOf(deliveries), List.copyOf(nodes));
    }

    public HintedHandoffResult runLocalHintedHandoff() {
        requireAvailable();
        List<HintDelivery> deliveries = deliverLocalHints();
        if (deliveries.isEmpty()) {
            log("hinted handoff checked: no pending hints");
        } else {
            log("hinted handoff checked: " + deliveries.stream().filter(HintDelivery::delivered).count() + "/" + deliveries.size() + " delivered");
        }
        return new HintedHandoffResult(deliveries, List.of(new ReplicaAck(nodeId, true, "local handoff checked")));
    }

    public NodeSnapshot localSnapshot() {
        return new NodeSnapshot(
            nodeId,
            "self",
            available,
            Map.copyOf(store),
            hintSnapshot(),
            membershipSnapshot(),
            List.copyOf(events)
        );
    }

    public ClusterSnapshot clusterSnapshot() {
        List<NodeSnapshot> snapshots = new ArrayList<>();
        snapshots.add(localSnapshot());
        for (Peer peer : peersByNodeId.values()) {
            try {
                NodeSnapshot snapshot = restTemplate.getForEntity(peer.endpoint() + "/internal/snapshot", NodeSnapshot.class).getBody();
                if (snapshot != null) {
                    snapshots.add(snapshot.withEndpoint(peer.endpoint()));
                    if (snapshot.available()) {
                        markPeerAlive(peer.nodeId());
                    } else {
                        markPeerSuspect(peer.nodeId());
                    }
                }
            } catch (RestClientException exception) {
                markPeerSuspect(peer.nodeId());
                snapshots.add(new NodeSnapshot(
                    peer.nodeId(),
                    peer.endpoint(),
                    false,
                    Map.of(),
                    List.of(),
                    List.of(membership.getOrDefault(peer.nodeId(),
                        new MembershipEntry(peer.nodeId(), peer.endpoint(), 0, NodeStatus.SUSPECT, Instant.now()))),
                    List.of("snapshot unreachable")
                ));
            }
        }
        return new ClusterSnapshot(
            nodeId,
            hashRing.nodeCount(),
            effectiveReplicationFactor(),
            hashRing.virtualNodesPerNode(),
            hashRing.tokenCount(),
            writeQuorum,
            readQuorum,
            snapshots
        );
    }

    public KeyPlacement keyPlacement(String key) {
        return new KeyPlacement(
            key,
            hashRing.hashOf(key),
            replicaTargets(key).stream().map(ReplicaTarget::nodeId).toList(),
            hashRing.nodeCount(),
            effectiveReplicationFactor(),
            hashRing.virtualNodesPerNode(),
            hashRing.tokenCount()
        );
    }

    private boolean saveVersion(VersionedValue incoming) {
        List<VersionedValue> next = new ArrayList<>(siblings(incoming.key()));
        if (next.stream().anyMatch(existing -> existing.clock().descendsFrom(incoming.clock()))) {
            return false;
        }
        next.removeIf(existing -> incoming.clock().descendsFrom(existing.clock()));
        next.add(incoming);
        next.sort(Comparator.comparing(VersionedValue::writtenAt));
        store.put(incoming.key(), List.copyOf(next));
        return true;
    }

    private List<VersionedValue> siblings(String key) {
        return store.getOrDefault(key, List.of());
    }

    private List<VersionedValue> reconcile(List<VersionedValue> versions) {
        List<VersionedValue> reconciled = new ArrayList<>();
        for (VersionedValue version : versions) {
            boolean dominated = reconciled.stream().anyMatch(existing -> existing.clock().descendsFrom(version.clock()));
            if (dominated) {
                continue;
            }
            reconciled.removeIf(existing -> version.clock().descendsFrom(existing.clock()));
            boolean duplicate = reconciled.stream().anyMatch(existing -> existing.versionId().equals(version.versionId()));
            if (!duplicate) {
                reconciled.add(version);
            }
        }
        reconciled.sort(Comparator.comparing(VersionedValue::writtenAt));
        return List.copyOf(reconciled);
    }

    private boolean hasConflict(List<VersionedValue> versions) {
        for (int left = 0; left < versions.size(); left++) {
            for (int right = left + 1; right < versions.size(); right++) {
                if (versions.get(left).clock().conflictsWith(versions.get(right).clock())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<ReplicaTarget> replicaTargets(String key) {
        return hashRing.replicasFor(key, effectiveReplicationFactor()).stream()
            .map(this::targetFor)
            .filter(Objects::nonNull)
            .toList();
    }

    private ReplicaTarget targetFor(String targetNodeId) {
        if (nodeId.equals(targetNodeId)) {
            return new ReplicaTarget(targetNodeId, "self", true);
        }
        Peer peer = peersByNodeId.get(targetNodeId);
        if (peer == null) {
            return null;
        }
        return new ReplicaTarget(peer.nodeId(), peer.endpoint(), false);
    }

    private List<VersionedValue> readVersionsForClock(String key, List<ReplicaTarget> targets) {
        List<VersionedValue> versions = new ArrayList<>();
        for (ReplicaTarget target : targets) {
            try {
                if (target.local()) {
                    versions.addAll(siblings(key));
                } else {
                    String encodedKey = UriUtils.encodePathSegment(key, java.nio.charset.StandardCharsets.UTF_8);
                    ResponseEntity<ReplicaReadResponse> response = restTemplate.getForEntity(
                        target.endpoint() + "/internal/replica/read/" + encodedKey,
                        ReplicaReadResponse.class
                    );
                    ReplicaReadResponse body = Objects.requireNonNull(response.getBody());
                    versions.addAll(body.versions());
                    markPeerAlive(target.nodeId());
                }
            } catch (RestClientException exception) {
                markPeerSuspect(target.nodeId());
            }
        }
        return versions;
    }

    private int effectiveReplicationFactor() {
        return Math.min(properties.replicationFactor(), hashRing.nodeCount());
    }

    private List<String> clusterNodeIds() {
        List<String> nodeIds = new ArrayList<>();
        nodeIds.add(nodeId);
        nodeIds.addAll(peersByNodeId.keySet());
        return nodeIds.stream()
            .distinct()
            .sorted()
            .toList();
    }

    private Peer randomGossipPeer() {
        List<Peer> peers = List.copyOf(peersByNodeId.values());
        if (peers.isEmpty()) {
            return null;
        }
        return peers.get(ThreadLocalRandom.current().nextInt(peers.size()));
    }

    private void addHint(String targetNodeId, VersionedValue version) {
        pendingHints.compute(targetNodeId, (ignored, current) -> {
            List<HintedHandoff> next = current == null ? new ArrayList<>() : new ArrayList<>(current);
            boolean alreadyQueued = next.stream()
                .anyMatch(hint -> hint.version().versionId().equals(version.versionId()));
            if (!alreadyQueued) {
                next.add(new HintedHandoff(
                    nodeId + "-hint-" + UUID.randomUUID().toString().substring(0, 8),
                    targetNodeId,
                    version,
                    Instant.now()
                ));
            }
            return List.copyOf(next);
        });
        log("hint stored for " + targetNodeId + " key " + version.key());
    }

    private List<HintDelivery> deliverLocalHints() {
        List<HintDelivery> deliveries = new ArrayList<>();
        for (Map.Entry<String, List<HintedHandoff>> entry : pendingHints.entrySet()) {
            Peer target = peersByNodeId.get(entry.getKey());
            if (target == null) {
                for (HintedHandoff hint : entry.getValue()) {
                    deliveries.add(delivery(hint, false, "unknown target"));
                }
                continue;
            }

            List<String> deliveredHintIds = new ArrayList<>();
            for (HintedHandoff hint : entry.getValue()) {
                try {
                    restTemplate.postForEntity(
                        target.endpoint() + "/internal/replica/write",
                        new ReplicaWriteRequest(hint.version()),
                        Void.class
                    );
                    deliveredHintIds.add(hint.hintId());
                    deliveries.add(delivery(hint, true, "handed off"));
                    markPeerAlive(target.nodeId());
                } catch (RestClientException exception) {
                    deliveries.add(delivery(hint, false, "target still unreachable"));
                    markPeerSuspect(target.nodeId());
                }
            }
            removeDeliveredHints(entry.getKey(), deliveredHintIds);
        }
        return List.copyOf(deliveries);
    }

    private HintDelivery delivery(HintedHandoff hint, boolean delivered, String message) {
        VersionedValue version = hint.version();
        return new HintDelivery(
            nodeId,
            hint.targetNodeId(),
            version.key(),
            version.value(),
            version.versionId(),
            delivered,
            message
        );
    }

    private void removeDeliveredHints(String targetNodeId, List<String> deliveredHintIds) {
        if (deliveredHintIds.isEmpty()) {
            return;
        }
        pendingHints.computeIfPresent(targetNodeId, (ignored, current) -> {
            List<HintedHandoff> remaining = current.stream()
                .filter(hint -> !deliveredHintIds.contains(hint.hintId()))
                .toList();
            return remaining.isEmpty() ? null : List.copyOf(remaining);
        });
    }

    private void mergeMembership(List<MembershipEntry> incoming) {
        Instant now = Instant.now();
        for (MembershipEntry entry : incoming) {
            if (entry.nodeId().equals(nodeId)) {
                continue;
            }
            membership.merge(entry.nodeId(), entry, (current, candidate) -> {
                if (candidate.heartbeat() > current.heartbeat()) {
                    return new MembershipEntry(
                        candidate.nodeId(),
                        knownEndpoint(candidate.nodeId(), candidate.endpoint()),
                        candidate.heartbeat(),
                        candidate.status(),
                        now
                    );
                }
                if (candidate.status() == NodeStatus.DEAD && current.status() != NodeStatus.ALIVE) {
                    return current.withStatus(NodeStatus.DEAD);
                }
                return current;
            });
        }
        refreshFailureStatus();
    }

    private String knownEndpoint(String incomingNodeId, String fallback) {
        Peer peer = peersByNodeId.get(incomingNodeId);
        return peer == null ? fallback : peer.endpoint();
    }

    private void markPeerAlive(String peerNodeId) {
        if (peerNodeId == null || peerNodeId.equals(nodeId)) {
            return;
        }
        membership.computeIfPresent(peerNodeId, (ignored, entry) ->
            new MembershipEntry(entry.nodeId(), entry.endpoint(), Math.max(entry.heartbeat(), 1), NodeStatus.ALIVE, Instant.now())
        );
    }

    private void markPeerSuspect(String peerNodeId) {
        if (peerNodeId == null || peerNodeId.equals(nodeId)) {
            return;
        }
        membership.computeIfPresent(peerNodeId, (ignored, entry) -> {
            if (entry.status() == NodeStatus.DEAD) {
                return entry;
            }
            return entry.withStatus(NodeStatus.SUSPECT);
        });
    }

    private void refreshFailureStatus() {
        Instant now = Instant.now();
        for (MembershipEntry entry : membership.values()) {
            if (entry.nodeId().equals(nodeId)) {
                continue;
            }
            long staleMillis = Duration.between(entry.lastSeen(), now).toMillis();
            if (staleMillis > properties.gossip().deadAfterMs()) {
                membership.put(entry.nodeId(), entry.withStatus(NodeStatus.DEAD));
            } else if (staleMillis > properties.gossip().suspectAfterMs()) {
                membership.put(entry.nodeId(), entry.withStatus(NodeStatus.SUSPECT));
            }
        }
    }

    private List<MembershipEntry> membershipSnapshot() {
        return membership.values().stream()
            .sorted(Comparator.comparing(MembershipEntry::nodeId))
            .toList();
    }

    private List<HintSummary> hintSnapshot() {
        return pendingHints.values().stream()
            .flatMap(List::stream)
            .sorted(Comparator.comparing(HintedHandoff::createdAt))
            .map(hint -> new HintSummary(
                hint.hintId(),
                hint.targetNodeId(),
                hint.version().key(),
                hint.version().value(),
                hint.version().versionId(),
                hint.createdAt()
            ))
            .toList();
    }

    private void requireAvailable() {
        if (!available) {
            throw new NodeUnavailableException(nodeId + " is paused");
        }
    }

    private void log(String message) {
        String line = Instant.now() + " | " + message;
        synchronized (events) {
            events.addFirst(line);
            while (events.size() > 12) {
                events.removeLast();
            }
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private Map<String, Peer> parsePeers(List<String> configuredPeers) {
        Map<String, Peer> peers = new LinkedHashMap<>();
        if (configuredPeers == null) {
            return peers;
        }
        for (String rawPeer : configuredPeers) {
            if (rawPeer == null || rawPeer.isBlank()) {
                continue;
            }
            String peer = rawPeer.trim();
            String parsedNodeId;
            String endpoint;
            if (peer.contains("=")) {
                String[] parts = peer.split("=", 2);
                parsedNodeId = parts[0].trim();
                endpoint = parts[1].trim();
            } else {
                endpoint = peer;
                parsedNodeId = URI.create(peer).getPort() == -1 ? peer : "N" + URI.create(peer).getPort();
            }
            if (!parsedNodeId.equals(nodeId)) {
                peers.put(parsedNodeId, new Peer(parsedNodeId, endpoint));
            }
        }
        return peers;
    }

    private record Peer(String nodeId, String endpoint) {
    }

    private record ReplicaTarget(String nodeId, String endpoint, boolean local) {
    }

    private record HintedHandoff(String hintId, String targetNodeId, VersionedValue version, Instant createdAt) {
    }

    public record PutCommand(String key, String value) {
    }

    public record PutResult(
        String key,
        VersionedValue version,
        int ackCount,
        int requiredAckCount,
        boolean quorumReached,
        List<ReplicaAck> acks,
        String requestedCoordinatorNodeId,
        String coordinatorNodeId,
        boolean coordinatorFallback
    ) {

        public PutResult withRequestedCoordinator(String nextRequestedCoordinatorNodeId, boolean nextCoordinatorFallback) {
            return new PutResult(
                key,
                version,
                ackCount,
                requiredAckCount,
                quorumReached,
                acks,
                nextRequestedCoordinatorNodeId,
                coordinatorNodeId,
                nextCoordinatorFallback
            );
        }
    }

    public record ReplicaAck(String nodeId, boolean ok, String message) {
    }

    public record ReadResult(
        String key,
        List<VersionedValue> versions,
        List<ReplicaRead> reads,
        int readCount,
        int requiredReadCount,
        boolean quorumReached,
        boolean conflict,
        String requestedCoordinatorNodeId,
        String coordinatorNodeId,
        boolean coordinatorFallback
    ) {

        public ReadResult withRequestedCoordinator(String nextRequestedCoordinatorNodeId, boolean nextCoordinatorFallback) {
            return new ReadResult(
                key,
                versions,
                reads,
                readCount,
                requiredReadCount,
                quorumReached,
                conflict,
                nextRequestedCoordinatorNodeId,
                coordinatorNodeId,
                nextCoordinatorFallback
            );
        }
    }

    public record ReplicaRead(String nodeId, boolean ok, List<VersionedValue> versions, String message) {
    }

    public record ReplicaWriteRequest(VersionedValue version) {
    }

    public record ReplicaReadResponse(String nodeId, List<VersionedValue> versions) {
    }

    public record GossipMessage(String fromNodeId, List<MembershipEntry> membership) {
    }

    public record GossipResult(String nodeId, List<MembershipEntry> membership, List<GossipPeerResult> peers) {
    }

    public record GossipPeerResult(String nodeId, boolean ok, String message) {
    }

    public record AvailabilityCommand(boolean available) {
    }

    public record AvailabilityResult(String nodeId, boolean available, boolean ok, String message) {
    }

    public record QuorumSettingsCommand(int writeQuorum, int readQuorum) {
    }

    public record QuorumSettings(int replicationFactor, int writeQuorum, int readQuorum) {
    }

    public record KeyPlacement(
        String key,
        long keyHash,
        List<String> replicaNodes,
        int serverCount,
        int replicationFactor,
        int virtualNodes,
        int tokenCount
    ) {
    }

    public record QuorumSettingsResult(
        int replicationFactor,
        int writeQuorum,
        int readQuorum,
        boolean quorumOverlap,
        List<ReplicaAck> acks
    ) {
    }

    public record HintSummary(
        String hintId,
        String targetNodeId,
        String key,
        String value,
        String versionId,
        Instant createdAt
    ) {
    }

    public record HintDelivery(
        String holderNodeId,
        String targetNodeId,
        String key,
        String value,
        String versionId,
        boolean delivered,
        String message
    ) {
    }

    public record HintedHandoffResult(List<HintDelivery> deliveries, List<ReplicaAck> nodes) {
    }

    public record NodeSnapshot(
        String nodeId,
        String endpoint,
        boolean available,
        Map<String, List<VersionedValue>> store,
        List<HintSummary> hints,
        List<MembershipEntry> membership,
        List<String> events
    ) {

        public NodeSnapshot withEndpoint(String nextEndpoint) {
            return new NodeSnapshot(nodeId, nextEndpoint, available, store, hints, membership, events);
        }
    }

    public record ClusterSnapshot(
        String coordinatorNodeId,
        int serverCount,
        int replicationFactor,
        int virtualNodes,
        int tokenCount,
        int writeQuorum,
        int readQuorum,
        List<NodeSnapshot> nodes
    ) {
    }
}
