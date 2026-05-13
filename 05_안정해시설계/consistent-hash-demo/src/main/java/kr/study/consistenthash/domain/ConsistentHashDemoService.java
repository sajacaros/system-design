package kr.study.consistenthash.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ConsistentHashDemoService {

    private static final List<ServerNode> BASE_SERVERS = List.of(
        new ServerNode("s0", "server 1", "#bca7d8"),
        new ServerNode("s1", "server 2", "#3bb0d8"),
        new ServerNode("s2", "server 3", "#f34fe9"),
        new ServerNode("s3", "server 4", "#ffb561")
    );
    private static final ServerNode ADDED_SERVER = new ServerNode("s4", "server 5", "#15a34a");
    private static final ServerNode EXTRA_SERVER = new ServerNode("s5", "server 6", "#8b5cf6");
    private static final int DEFAULT_RESIZED_SERVER_COUNT = 1;
    private static final List<ServerNode> ALL_MODULO_SERVERS = List.of(
        BASE_SERVERS.get(0),
        BASE_SERVERS.get(1),
        BASE_SERVERS.get(2),
        BASE_SERVERS.get(3),
        ADDED_SERVER,
        EXTRA_SERVER
    );
    private static final List<ServerNode> ALL_HASH_RING_SERVERS = ALL_MODULO_SERVERS;
    private static final int HASH_RING_TOKENS_PER_SERVER = 1;
    private static final int DEFAULT_VIRTUAL_REPLICAS = 16;
    private static final int MIN_VIRTUAL_REPLICAS = 1;
    private static final int MAX_VIRTUAL_REPLICAS = 64;

    private final Object moduloStateLock = new Object();
    private final List<KeyNode> moduloKeys = new ArrayList<>();
    private final LinkedHashMap<String, Boolean> moduloServersEnabled = defaultModuloServersEnabled();
    private LinkedHashMap<String, Boolean> previousModuloServersEnabled = new LinkedHashMap<>(moduloServersEnabled);

    private final Object hashRingLock = new Object();
    private final List<KeyNode> hashRingKeys = new ArrayList<>();
    private final LinkedHashMap<String, Boolean> hashRingServersEnabled = defaultHashRingServersEnabled();

    private final Object virtualNodesLock = new Object();
    private final List<KeyNode> virtualNodeKeys = new ArrayList<>();
    private final LinkedHashMap<String, Boolean> virtualServersEnabled = defaultVirtualServersEnabled();
    private int virtualReplicas = DEFAULT_VIRTUAL_REPLICAS;

    public DashboardState dashboard(int moduloServers) {
        return new DashboardState(moduloScenario(moduloServers), hashRing(), virtualNodes());
    }

    public ModuloScenario moduloScenario() {
        synchronized (moduloStateLock) {
            return buildModuloScenario(previousModuloServersEnabled, moduloServersEnabled, List.copyOf(moduloKeys));
        }
    }

    public ModuloScenario moduloScenario(int requestedServerCount) {
        synchronized (moduloStateLock) {
            LinkedHashMap<String, Boolean> requestedState = moduloServersEnabledForCount(requestedServerCount);
            return buildModuloScenario(previousModuloServersEnabled, requestedState, List.copyOf(moduloKeys));
        }
    }

    public ModuloScenario toggleModuloServer(String serverId) {
        synchronized (moduloStateLock) {
            Boolean active = moduloServersEnabled.get(serverId);
            if (active == null) {
                return buildModuloScenario(previousModuloServersEnabled, moduloServersEnabled, List.copyOf(moduloKeys));
            }

            long activeCount = moduloServersEnabled.values().stream().filter(Boolean::booleanValue).count();
            if (active && activeCount <= 1) {
                return buildModuloScenario(previousModuloServersEnabled, moduloServersEnabled, List.copyOf(moduloKeys));
            }

            previousModuloServersEnabled = new LinkedHashMap<>(moduloServersEnabled);
            moduloServersEnabled.put(serverId, !active);
            return buildModuloScenario(previousModuloServersEnabled, moduloServersEnabled, List.copyOf(moduloKeys));
        }
    }

    public ModuloScenario addModuloKey() {
        synchronized (moduloStateLock) {
            settleModuloMovements();
            int nextIndex = moduloKeys.size();
            moduloKeys.add(new KeyNode("key" + nextIndex, "key" + nextIndex));
            return buildModuloScenario(previousModuloServersEnabled, moduloServersEnabled, List.copyOf(moduloKeys));
        }
    }

    public ModuloScenario resetModulo() {
        synchronized (moduloStateLock) {
            settleModuloMovements();
            moduloKeys.clear();
            resetModuloServersToDefault();
            return buildModuloScenario(previousModuloServersEnabled, moduloServersEnabled, List.copyOf(moduloKeys));
        }
    }

    public HashRingScenario hashRing() {
        synchronized (hashRingLock) {
            return buildHashRingScenario(List.copyOf(hashRingKeys));
        }
    }

    public HashRingScenario addHashRingKey() {
        synchronized (hashRingLock) {
            int nextIndex = hashRingKeys.size();
            hashRingKeys.add(new KeyNode("key" + nextIndex, "key" + nextIndex));
            return buildHashRingScenario(List.copyOf(hashRingKeys));
        }
    }

    public HashRingScenario resetHashRing() {
        synchronized (hashRingLock) {
            hashRingKeys.clear();
            resetHashRingServersToDefault();
            return buildHashRingScenario(List.of());
        }
    }

    public HashRingScenario toggleHashRingServer(String serverId) {
        synchronized (hashRingLock) {
            Boolean active = hashRingServersEnabled.get(serverId);
            if (active == null) {
                return buildHashRingScenario(List.copyOf(hashRingKeys));
            }

            long activeCount = hashRingServersEnabled.values().stream().filter(Boolean::booleanValue).count();
            if (active && activeCount <= 1) {
                return buildHashRingScenario(List.copyOf(hashRingKeys));
            }

            hashRingServersEnabled.put(serverId, !active);
            return buildHashRingScenario(List.copyOf(hashRingKeys));
        }
    }

    public VirtualNodesScenario virtualNodes() {
        synchronized (virtualNodesLock) {
            return buildVirtualNodesScenario(List.copyOf(virtualNodeKeys), virtualReplicas);
        }
    }

    public VirtualNodesScenario addVirtualNodeKey() {
        synchronized (virtualNodesLock) {
            int nextIndex = virtualNodeKeys.size();
            virtualNodeKeys.add(new KeyNode("key" + nextIndex, "key" + nextIndex));
            return buildVirtualNodesScenario(List.copyOf(virtualNodeKeys), virtualReplicas);
        }
    }

    public VirtualNodesScenario resetVirtualNodes() {
        synchronized (virtualNodesLock) {
            virtualNodeKeys.clear();
            resetVirtualServersToDefault();
            virtualReplicas = DEFAULT_VIRTUAL_REPLICAS;
            return buildVirtualNodesScenario(List.of(), virtualReplicas);
        }
    }

    public VirtualNodesScenario updateVirtualReplicas(int replicas) {
        synchronized (virtualNodesLock) {
            virtualReplicas = clamp(replicas, MIN_VIRTUAL_REPLICAS, MAX_VIRTUAL_REPLICAS);
            return buildVirtualNodesScenario(List.copyOf(virtualNodeKeys), virtualReplicas);
        }
    }

    public VirtualNodesScenario toggleVirtualNodeServer(String serverId) {
        synchronized (virtualNodesLock) {
            Boolean active = virtualServersEnabled.get(serverId);
            if (active == null) {
                return buildVirtualNodesScenario(List.copyOf(virtualNodeKeys), virtualReplicas);
            }

            long activeCount = virtualServersEnabled.values().stream().filter(Boolean::booleanValue).count();
            if (active && activeCount <= 1) {
                return buildVirtualNodesScenario(List.copyOf(virtualNodeKeys), virtualReplicas);
            }

            virtualServersEnabled.put(serverId, !active);
            return buildVirtualNodesScenario(List.copyOf(virtualNodeKeys), virtualReplicas);
        }
    }

    private void settleModuloMovements() {
        previousModuloServersEnabled = new LinkedHashMap<>(moduloServersEnabled);
    }

    private ModuloScenario buildModuloScenario(
        LinkedHashMap<String, Boolean> beforeServerState,
        LinkedHashMap<String, Boolean> afterServerState,
        List<KeyNode> keys
    ) {
        List<ServerNode> beforeServers = activeModuloServers(beforeServerState);
        List<ServerNode> afterServers = activeModuloServers(afterServerState);
        List<ModuloBucket> beforeBuckets = moduloBuckets(beforeServers, keys);
        List<ModuloBucket> afterBuckets = moduloBuckets(afterServers, keys);
        Map<String, String> beforeAssignments = moduloAssignments(beforeServers, keys);
        Map<String, String> afterAssignments = moduloAssignments(afterServers, keys);

        List<KeyMovement> movements = keys.stream()
            .map(key -> movementForModulo(key, beforeAssignments.get(key.id()), afterAssignments.get(key.id())))
            .filter(Objects::nonNull)
            .toList();

        double movedRatio = keys.isEmpty() ? 0.0 : (double) movements.size() / keys.size();
        return new ModuloScenario(
            beforeServers.size(),
            afterServers.size(),
            beforeBuckets,
            afterBuckets,
            movements,
            moduloClusterServers(afterServerState),
            movements.size(),
            movedRatio,
            "serverIndex = hash(key) % N",
            "Changing only N remaps many keys to different buckets."
        );
    }

    private HashRingScenario buildHashRingScenario(List<KeyNode> keys) {
        List<ServerNode> activeServers = activeHashRingServers();
        NavigableMap<Long, RingToken> ring = buildHashRing(activeServers);
        List<HashRingToken> tokens = ring.entrySet().stream()
            .map(entry -> {
                RingToken token = entry.getValue();
                return new HashRingToken(
                    token.tokenId(),
                    token.serverId(),
                    token.serverLabel(),
                    token.color(),
                    entry.getKey(),
                    angle(entry.getKey())
                );
            })
            .toList();

        List<RingPoint> points = new ArrayList<>();
        for (ServerNode server : activeServers) {
            long representativeHash = representativeHash(server.id());
            points.add(new RingPoint(
                server.id(),
                server.label(),
                "server",
                server.id(),
                serverDisplayAngle(server.id()),
                server.color(),
                representativeHash
            ));
        }

        Map<String, Integer> keyCounts = new LinkedHashMap<>();
        for (ServerNode server : activeServers) {
            keyCounts.put(server.id(), 0);
        }
        List<KeyAssignment> assignments = new ArrayList<>();
        for (KeyNode key : keys) {
            long keyHash = hash(key.id());
            Map.Entry<Long, RingToken> ownerEntry = ownerEntry(ring, keyHash);
            RingToken owner = ownerEntry.getValue();
            keyCounts.computeIfPresent(owner.serverId(), (ignored, count) -> count + 1);
            points.add(new RingPoint(
                key.id(),
                key.label(),
                "key",
                key.id(),
                angle(keyHash),
                "#1f2328",
                keyHash
            ));
            assignments.add(new KeyAssignment(
                key.id(),
                key.label(),
                keyHash,
                owner.serverId(),
                owner.serverLabel(),
                owner.color(),
                owner.serverId(),
                ownerEntry.getKey()
            ));
        }

        List<HashRingServer> servers = hashRingClusterServers(keyCounts);
        points.sort(Comparator.comparingDouble(RingPoint::angle));
        assignments.sort(Comparator.comparing(KeyAssignment::keyLabel));
        return new HashRingScenario(
            new HashRingView("Hash Ring", List.copyOf(points), List.copyOf(assignments)),
            tokens,
            servers,
            activeServers.size(),
            keys.size(),
            "Six fixed server positions; keys move clockwise to the next enabled server."
        );
    }

    private NavigableMap<Long, RingToken> buildHashRing(List<ServerNode> servers) {
        NavigableMap<Long, RingToken> ring = new TreeMap<>();
        for (ServerNode server : servers) {
            long position = representativeHash(server.id());
            while (ring.containsKey(position)) {
                position = position == Long.MAX_VALUE ? 0 : position + 1;
            }
            ring.put(position, new RingToken(server.id(), server.id(), server.label(), server.color()));
        }
        return ring;
    }

    private VirtualNodesScenario buildVirtualNodesScenario(List<KeyNode> keys, int replicas) {
        List<ServerNode> activeServers = activeVirtualServers();
        NavigableMap<Long, RingToken> ring = buildVirtualNodeRing(activeServers, replicas);
        List<HashRingToken> tokens = ring.entrySet().stream()
            .map(entry -> {
                RingToken token = entry.getValue();
                return new HashRingToken(
                    token.tokenId(),
                    token.serverId(),
                    token.serverLabel(),
                    token.color(),
                    entry.getKey(),
                    angle(entry.getKey())
                );
            })
            .toList();

        List<RingPoint> points = ring.entrySet().stream()
            .map(entry -> {
                RingToken token = entry.getValue();
                return new RingPoint(
                    token.tokenId(),
                    "",
                    "virtual-server",
                    token.serverId(),
                    angle(entry.getKey()),
                    token.color(),
                    entry.getKey()
                );
            })
            .collect(Collectors.toCollection(ArrayList::new));

        Map<String, Integer> keyCounts = new LinkedHashMap<>();
        for (ServerNode server : activeServers) {
            keyCounts.put(server.id(), 0);
        }

        List<KeyAssignment> assignments = new ArrayList<>();
        for (KeyNode key : keys) {
            long keyHash = hash(key.id());
            Map.Entry<Long, RingToken> ownerEntry = ownerEntry(ring, keyHash);
            RingToken owner = ownerEntry.getValue();
            keyCounts.computeIfPresent(owner.serverId(), (ignored, count) -> count + 1);
            points.add(new RingPoint(
                key.id(),
                key.label(),
                "key",
                key.id(),
                angle(keyHash),
                "#1f2328",
                keyHash
            ));
            assignments.add(new KeyAssignment(
                key.id(),
                key.label(),
                keyHash,
                owner.serverId(),
                owner.serverLabel(),
                owner.color(),
                owner.tokenId(),
                ownerEntry.getKey()
            ));
        }

        List<VirtualServer> virtualServers = virtualClusterServers(keyCounts, replicas);

        points.sort(Comparator.comparingDouble(RingPoint::angle));
        assignments.sort(Comparator.comparing(KeyAssignment::keyLabel));
        return new VirtualNodesScenario(
            new HashRingView("Virtual Nodes", List.copyOf(points), List.copyOf(assignments)),
            tokens,
            virtualServers,
            activeServers.size(),
            tokens.size(),
            keys.size(),
            replicas,
            "Each server owns multiple virtual nodes; keys move clockwise to the next virtual node."
        );
    }

    private NavigableMap<Long, RingToken> buildVirtualNodeRing(List<ServerNode> servers, int replicas) {
        NavigableMap<Long, RingToken> ring = new TreeMap<>();
        for (ServerNode server : servers) {
            for (int replicaIndex = 0; replicaIndex < replicas; replicaIndex++) {
                String tokenId = server.id() + "-v" + replicaIndex;
                long position = hash(tokenId);
                while (ring.containsKey(position)) {
                    position = position == Long.MAX_VALUE ? 0 : position + 1;
                }
                ring.put(position, new RingToken(tokenId, server.id(), server.label(), server.color()));
            }
        }
        return ring;
    }

    private Map.Entry<Long, RingToken> ownerEntry(NavigableMap<Long, RingToken> ring, long keyHash) {
        Map.Entry<Long, RingToken> ceilingEntry = ring.ceilingEntry(keyHash);
        return ceilingEntry != null ? ceilingEntry : ring.firstEntry();
    }

    private List<ModuloBucket> moduloBuckets(List<ServerNode> servers, List<KeyNode> keys) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (ServerNode server : servers) {
            grouped.put(server.id(), new ArrayList<>());
        }
        for (KeyNode key : keys) {
            if (servers.isEmpty()) {
                break;
            }
            ServerNode server = moduloServerNode(key.id(), servers);
            grouped.get(server.id()).add(key.label());
        }
        List<ModuloBucket> buckets = new ArrayList<>();
        for (int index = 0; index < servers.size(); index++) {
            ServerNode server = servers.get(index);
            buckets.add(new ModuloBucket(index, server.id(), server.label(), server.color(), List.copyOf(grouped.get(server.id()))));
        }
        return buckets;
    }

    private Map<String, String> moduloAssignments(List<ServerNode> servers, List<KeyNode> keys) {
        return keys.stream().collect(Collectors.toMap(KeyNode::id, key -> {
            if (servers.isEmpty()) {
                return "";
            }
            return moduloServerNode(key.id(), servers).label();
        }));
    }

    private KeyMovement movementForModulo(KeyNode key, String beforeLabel, String afterLabel) {
        if (Objects.equals(beforeLabel, afterLabel)) {
            return null;
        }
        return new KeyMovement(key.id(), key.label(), beforeLabel, afterLabel);
    }

    private int moduloServerIndex(String keyId, int serverCount) {
        return (int) (hash(keyId) % serverCount);
    }

    private ServerNode moduloServerNode(String keyId, List<ServerNode> servers) {
        int serverIndex = moduloServerIndex(keyId, servers.size());
        return servers.get(serverIndex);
    }

    private List<ServerNode> activeModuloServers(LinkedHashMap<String, Boolean> state) {
        Map<String, ServerNode> serversById = ALL_MODULO_SERVERS.stream()
            .collect(Collectors.toMap(ServerNode::id, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        return state.entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(entry -> serversById.get(entry.getKey()))
            .filter(Objects::nonNull)
            .toList();
    }

    private List<ClusterServer> moduloClusterServers(LinkedHashMap<String, Boolean> state) {
        long activeCount = state.values().stream().filter(Boolean::booleanValue).count();
        return ALL_MODULO_SERVERS.stream()
            .map(server -> {
                boolean active = state.getOrDefault(server.id(), false);
                return new ClusterServer(
                    server.id(),
                    server.label(),
                    server.color(),
                    active ? "active" : "disabled",
                    active ? "Disable" : "Enable",
                    active ? activeCount > 1 : true,
                    active
                );
            })
            .toList();
    }

    private void resetModuloServersToDefault() {
        moduloServersEnabled.clear();
        moduloServersEnabled.putAll(defaultModuloServersEnabled());
        previousModuloServersEnabled = new LinkedHashMap<>(moduloServersEnabled);
    }

    private LinkedHashMap<String, Boolean> defaultModuloServersEnabled() {
        return moduloServersEnabledForCount(DEFAULT_RESIZED_SERVER_COUNT);
    }

    private LinkedHashMap<String, Boolean> moduloServersEnabledForCount(int requestedServerCount) {
        int activeServerCount = clamp(requestedServerCount, 1, ALL_MODULO_SERVERS.size());
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        for (int index = 0; index < ALL_MODULO_SERVERS.size(); index++) {
            defaults.put(ALL_MODULO_SERVERS.get(index).id(), index < activeServerCount);
        }
        return defaults;
    }

    private List<ServerNode> activeHashRingServers() {
        Map<String, ServerNode> serversById = ALL_HASH_RING_SERVERS.stream()
            .collect(Collectors.toMap(ServerNode::id, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        return hashRingServersEnabled.entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(entry -> serversById.get(entry.getKey()))
            .filter(Objects::nonNull)
            .toList();
    }

    private List<HashRingServer> hashRingClusterServers(Map<String, Integer> keyCounts) {
        long activeCount = hashRingServersEnabled.values().stream().filter(Boolean::booleanValue).count();
        return ALL_HASH_RING_SERVERS.stream()
            .map(server -> {
                boolean active = hashRingServersEnabled.getOrDefault(server.id(), false);
                return new HashRingServer(
                    server.id(),
                    server.label(),
                    server.color(),
                    active,
                    active ? "active" : "disabled",
                    active ? "Disable" : "Enable",
                    active ? activeCount > 1 : true,
                    active ? HASH_RING_TOKENS_PER_SERVER : 0,
                    keyCounts.getOrDefault(server.id(), 0)
                );
            })
            .toList();
    }

    private List<ServerNode> activeVirtualServers() {
        Map<String, ServerNode> serversById = ALL_HASH_RING_SERVERS.stream()
            .collect(Collectors.toMap(ServerNode::id, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        return virtualServersEnabled.entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(entry -> serversById.get(entry.getKey()))
            .filter(Objects::nonNull)
            .toList();
    }

    private List<VirtualServer> virtualClusterServers(Map<String, Integer> keyCounts, int replicas) {
        long activeCount = virtualServersEnabled.values().stream().filter(Boolean::booleanValue).count();
        return ALL_HASH_RING_SERVERS.stream()
            .map(server -> {
                boolean active = virtualServersEnabled.getOrDefault(server.id(), false);
                return new VirtualServer(
                    server.id(),
                    server.label(),
                    server.color(),
                    active,
                    active ? "active" : "disabled",
                    active ? "Disable" : "Enable",
                    active ? activeCount > 1 : true,
                    active ? replicas : 0,
                    keyCounts.getOrDefault(server.id(), 0)
                );
            })
            .toList();
    }

    private LinkedHashMap<String, Boolean> defaultVirtualServersEnabled() {
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        for (ServerNode server : ALL_HASH_RING_SERVERS) {
            defaults.put(server.id(), true);
        }
        return defaults;
    }

    private void resetVirtualServersToDefault() {
        virtualServersEnabled.clear();
        virtualServersEnabled.putAll(defaultVirtualServersEnabled());
    }

    private LinkedHashMap<String, Boolean> defaultHashRingServersEnabled() {
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        for (int index = 0; index < ALL_HASH_RING_SERVERS.size(); index++) {
            defaults.put(ALL_HASH_RING_SERVERS.get(index).id(), index == 0);
        }
        return defaults;
    }

    private void resetHashRingServersToDefault() {
        hashRingServersEnabled.clear();
        hashRingServersEnabled.putAll(defaultHashRingServersEnabled());
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

    private long tokenPosition(int serverIndex) {
        int serverCount = ALL_HASH_RING_SERVERS.size();
        return (long) ((serverIndex / (double) serverCount) * Long.MAX_VALUE);
    }

    private long representativeHash(String serverId) {
        return tokenPosition(hashRingServerIndex(serverId));
    }

    private double serverDisplayAngle(String serverId) {
        return (360.0 / ALL_HASH_RING_SERVERS.size()) * hashRingServerIndex(serverId);
    }

    private int hashRingServerIndex(String serverId) {
        for (int index = 0; index < ALL_HASH_RING_SERVERS.size(); index++) {
            if (ALL_HASH_RING_SERVERS.get(index).id().equals(serverId)) {
                return index;
            }
        }
        return 0;
    }

    private double angle(long hashValue) {
        return (hashValue / (double) Long.MAX_VALUE) * 360.0;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ServerNode(String id, String label, String color) {
    }

    private record KeyNode(String id, String label) {
    }

    private record RingToken(String tokenId, String serverId, String serverLabel, String color) {
    }

    public record DashboardState(
        ModuloScenario modulo,
        HashRingScenario hashRing,
        VirtualNodesScenario virtualNodes
    ) {
    }

    public record ModuloScenario(
        int baselineServerCount,
        int resizedServerCount,
        List<ModuloBucket> beforeBuckets,
        List<ModuloBucket> afterBuckets,
        List<KeyMovement> movements,
        List<ClusterServer> servers,
        int movedKeyCount,
        double movedRatio,
        String formula,
        String insight
    ) {
    }

    public record ModuloBucket(
        int serverIndex,
        String serverId,
        String label,
        String color,
        List<String> keys
    ) {
    }

    public record ClusterServer(
        String serverId,
        String label,
        String color,
        String state,
        String actionLabel,
        boolean canToggle,
        boolean active
    ) {
    }

    public record KeyMovement(
        String keyId,
        String keyLabel,
        String fromServer,
        String toServer
    ) {
    }

    public record HashRingScenario(
        HashRingView ring,
        List<HashRingToken> tokens,
        List<HashRingServer> servers,
        int serverCount,
        int keyCount,
        String lookupRule
    ) {
    }

    public record HashRingView(
        String title,
        List<RingPoint> points,
        List<KeyAssignment> assignments
    ) {
    }

    public record HashRingToken(
        String tokenId,
        String serverId,
        String serverLabel,
        String color,
        long hash,
        double angle
    ) {
    }

    public record HashRingServer(
        String serverId,
        String label,
        String color,
        boolean active,
        String state,
        String actionLabel,
        boolean canToggle,
        int tokenCount,
        int keyCount
    ) {
    }

    public record VirtualNodesScenario(
        HashRingView ring,
        List<HashRingToken> tokens,
        List<VirtualServer> servers,
        int serverCount,
        int tokenCount,
        int keyCount,
        int replicas,
        String lookupRule
    ) {
    }

    public record VirtualServer(
        String serverId,
        String label,
        String color,
        boolean active,
        String state,
        String actionLabel,
        boolean canToggle,
        int tokenCount,
        int keyCount
    ) {
    }

    public record RingPoint(
        String id,
        String label,
        String type,
        String ownerId,
        double angle,
        String color,
        long hash
    ) {
    }

    public record KeyAssignment(
        String keyId,
        String keyLabel,
        long keyHash,
        String serverId,
        String serverLabel,
        String serverColor,
        String targetPointId,
        long targetHash
    ) {
    }
}
