package kr.study.consistenthash.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
    private static final int DEFAULT_VNODE_REPLICAS = 32;
    private static final List<ServerNode> ALL_MODULO_SERVERS = List.of(
        BASE_SERVERS.get(0),
        BASE_SERVERS.get(1),
        BASE_SERVERS.get(2),
        BASE_SERVERS.get(3),
        ADDED_SERVER,
        EXTRA_SERVER
    );
    private static final List<ServerNode> ALL_NODE_CHANGE_SERVERS = List.of(
        BASE_SERVERS.get(0),
        BASE_SERVERS.get(1),
        BASE_SERVERS.get(2),
        BASE_SERVERS.get(3),
        ADDED_SERVER
    );

    private final Object moduloStateLock = new Object();
    private final List<KeyNode> moduloKeys = new ArrayList<>(defaultModuloKeys());
    private final LinkedHashMap<String, Boolean> moduloServersEnabled = defaultModuloServersEnabled();
    private LinkedHashMap<String, Boolean> previousModuloServersEnabled = new LinkedHashMap<>(moduloServersEnabled);

    private final Object nodeClusterLock = new Object();
    private final Map<String, Boolean> nodeClusterEnabled = defaultNodeClusterEnabled();

    public DashboardState dashboard(int moduloServers, int replicas) {
        return new DashboardState(
            moduloScenario(),
            steadyRing(),
            nodeScenario(),
            nodeScenario(),
            virtualNodes(replicas)
        );
    }

    public ModuloScenario moduloScenario() {
        synchronized (moduloStateLock) {
            return buildModuloScenario(previousModuloServersEnabled, moduloServersEnabled, List.copyOf(moduloKeys));
        }
    }

    public ModuloScenario moduloScenario(int requestedServerCount) {
        synchronized (moduloStateLock) {
            return buildModuloScenario(previousModuloServersEnabled, moduloServersEnabled, List.copyOf(moduloKeys));
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
        int baselineServerCount = beforeServers.size();
        int resizedServerCount = afterServers.size();

        List<ModuloBucket> beforeBuckets = moduloBuckets(beforeServers, keys);
        List<ModuloBucket> afterBuckets = moduloBuckets(afterServers, keys);

        Map<String, String> beforeAssignments = moduloAssignments(beforeServers, keys);
        Map<String, String> afterAssignments = moduloAssignments(afterServers, keys);

        List<KeyMovement> movements = keys.stream()
            .map(key -> movementForModulo(key, beforeAssignments.get(key.id()), afterAssignments.get(key.id())))
            .filter(Objects::nonNull)
            .toList();

        double movedRatio = keys.isEmpty() ? 0.0 : (double) movements.size() / keys.size();
        List<ClusterServer> servers = moduloClusterServers(afterServerState);
        return new ModuloScenario(
            baselineServerCount,
            resizedServerCount,
            beforeBuckets,
            afterBuckets,
            movements,
            servers,
            movements.size(),
            movedRatio,
            "serverIndex = hash(key) % N",
            "서버 개수 N만 바뀌어도 대부분의 키가 다른 버킷으로 이동한다."
        );
    }

    public RingScenario steadyRing() {
        RingView ring = buildRingView(
            "기본 consistent hash ring",
            BASE_SERVERS,
            sampleRingKeys(),
            1,
            Map.of()
        );
        return new RingScenario(
            "steady",
            "키는 자신의 해시 위치에서 시계 방향으로 첫 번째 서버를 만난다.",
            "[5-7] 기본 조회",
            ring,
            null,
            List.of(),
            List.of(),
            ring.assignments().size(),
            0
        );
    }

    public RingScenario nodeChange(String mode) {
        return nodeScenario();
    }

    public RingScenario nodeScenario() {
        synchronized (nodeClusterLock) {
            return buildNodeScenario(nodeClusterEnabled, nodeClusterEnabled, "steady", null);
        }
    }

    public RingScenario addNodeToCluster() {
        synchronized (nodeClusterLock) {
            Map<String, Boolean> beforeState = new LinkedHashMap<>(nodeClusterEnabled);
            ServerNode candidate = ALL_NODE_CHANGE_SERVERS.stream()
                .filter(server -> !nodeClusterEnabled.getOrDefault(server.id(), false))
                .findFirst()
                .orElse(null);

            if (candidate != null) {
                nodeClusterEnabled.put(candidate.id(), true);
            }

            return buildNodeScenario(beforeState, nodeClusterEnabled, candidate != null ? "add" : "steady", candidate != null ? candidate.id() : null);
        }
    }

    public RingScenario toggleNodeInCluster(String serverId) {
        synchronized (nodeClusterLock) {
            Map<String, Boolean> beforeState = new LinkedHashMap<>(nodeClusterEnabled);
            Boolean currentlyEnabled = nodeClusterEnabled.get(serverId);
            if (currentlyEnabled == null) {
                return buildNodeScenario(beforeState, beforeState, "steady", null);
            }

            long activeCount = nodeClusterEnabled.values().stream().filter(Boolean::booleanValue).count();
            String mode = currentlyEnabled ? "disable" : "enable";
            if (currentlyEnabled && activeCount <= 1) {
                return buildNodeScenario(beforeState, beforeState, "steady", serverId);
            }

            nodeClusterEnabled.put(serverId, !currentlyEnabled);
            return buildNodeScenario(beforeState, nodeClusterEnabled, mode, serverId);
        }
    }

    public VirtualScenario virtualNodes(int requestedReplicas) {
        int replicas = clamp(requestedReplicas, 1, 128);
        List<KeyNode> sampleKeys = virtualSampleKeys();
        RingView singleRing = buildRingView("물리 서버만", BASE_SERVERS, sampleKeys, 1, Map.of());
        RingView virtualRing = buildRingView("가상 노드 적용", BASE_SERVERS, sampleKeys, replicas, Map.of());
        DistributionSummary singleDistribution = distributionSummary(BASE_SERVERS, statisticKeys(), 1, "replicas = 1");
        DistributionSummary virtualDistribution = distributionSummary(BASE_SERVERS, statisticKeys(), replicas, "replicas = " + replicas);

        return new VirtualScenario(
            replicas,
            singleRing,
            virtualRing,
            singleDistribution,
            virtualDistribution,
            "가상 노드를 늘릴수록 파티션 크기와 키 분포의 편차가 줄어든다."
        );
    }

    private List<ServerNode> concatServers(List<ServerNode> base, ServerNode appended) {
        List<ServerNode> servers = new ArrayList<>(base);
        servers.add(appended);
        return List.copyOf(servers);
    }

    private RingScenario buildNodeScenario(
        Map<String, Boolean> beforeState,
        Map<String, Boolean> afterState,
        String mode,
        String focusServerId
    ) {
        Map<String, ServerNode> serversById = ALL_NODE_CHANGE_SERVERS.stream()
            .collect(Collectors.toMap(ServerNode::id, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        List<ServerNode> beforeServers = activeServers(beforeState, serversById);
        List<ServerNode> afterServers = activeServers(afterState, serversById);

        RingView before = buildRingView("직전 상태", beforeServers, sampleRingKeys(), 1, Map.of());
        RingView after = buildRingView("현재 ring", afterServers, sampleRingKeys(), 1, Map.of());

        List<KeyMovement> movements = diffAssignments(before.assignments(), after.assignments());
        Set<String> movedKeyIds = movements.stream().map(KeyMovement::keyId).collect(Collectors.toSet());

        Map<String, String> statuses = new LinkedHashMap<>();
        for (ServerNode server : ALL_NODE_CHANGE_SERVERS) {
            if (!afterState.getOrDefault(server.id(), false)) {
                statuses.put(server.id(), "disabled");
            } else if (server.id().equals(focusServerId) && ("add".equals(mode) || "enable".equals(mode))) {
                statuses.put(server.id(), "added");
            } else {
                statuses.put(server.id(), "active");
            }
        }

        List<ClusterServer> clusterServers = ALL_NODE_CHANGE_SERVERS.stream()
            .map(server -> {
                boolean active = afterState.getOrDefault(server.id(), false);
                long activeCount = afterState.values().stream().filter(Boolean::booleanValue).count();
                boolean canToggle = active ? activeCount > 1 : true;
                String state = statuses.get(server.id());
                String actionLabel = active ? "Disable" : "Enable";
                return new ClusterServer(server.id(), server.label(), server.color(), state, actionLabel, canToggle, active);
            })
            .toList();

        String insight = switch (mode) {
            case "add", "enable" -> "추가된 서버와 직접 인접하지 않은 서버의 키도 일부 재배치될 수 있다.";
            case "disable" -> "비활성화된 서버뿐 아니라 떨어진 구간의 키도 다른 서버로 다시 매핑된다.";
            default -> "서버 블록에서 삭제/복구를 반복하며 키 재배치를 관찰한다.";
        };

        return new RingScenario(
            mode,
            insight,
            "현재 클러스터",
            withMovedMarkers(before, movedKeyIds),
            withMovedMarkers(after, movedKeyIds),
            movements,
            clusterServers,
            after.assignments().size(),
            movements.size()
        );
    }

    private List<ServerNode> activeServers(Map<String, Boolean> state, Map<String, ServerNode> serversById) {
        return state.entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(entry -> serversById.get(entry.getKey()))
            .filter(Objects::nonNull)
            .toList();
    }

    private LinkedHashMap<String, Boolean> defaultNodeClusterEnabled() {
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put("s0", true);
        defaults.put("s1", true);
        defaults.put("s2", true);
        defaults.put("s3", true);
        defaults.put("s4", false);
        return defaults;
    }

    private RingView buildRingView(
        String title,
        List<ServerNode> servers,
        List<KeyNode> keys,
        int replicas,
        Map<String, String> serverStates
    ) {
        NavigableMap<Long, RingToken> ring = buildRing(servers, replicas);
        List<RingPoint> points = new ArrayList<>();

        for (Map.Entry<Long, RingToken> entry : ring.entrySet()) {
            RingToken token = entry.getValue();
            boolean virtual = replicas > 1;
            boolean emphasize = !virtual || replicas <= 12 || token.replicaIndex() % Math.max(1, replicas / 4) == 0;
            String label = virtual ? (emphasize ? token.serverId() : "") : token.serverLabel();
            points.add(new RingPoint(
                token.tokenId(),
                label,
                virtual ? "virtual-server" : "server",
                token.serverId(),
                angle(entry.getKey()),
                token.color(),
                serverStates.getOrDefault(token.serverId(), "active"),
                emphasize
            ));
        }

        List<KeyAssignment> assignments = new ArrayList<>();
        for (KeyNode key : keys) {
            long keyHash = hash(key.id());
            Map.Entry<Long, RingToken> ownerEntry = ownerEntry(ring, keyHash);
            RingToken owner = ownerEntry.getValue();
            points.add(new RingPoint(
                key.id(),
                key.label(),
                "key",
                key.id(),
                angle(keyHash),
                "#1f2328",
                "active",
                true
            ));
            assignments.add(new KeyAssignment(
                key.id(),
                key.label(),
                owner.serverId(),
                owner.serverLabel(),
                owner.color(),
                owner.tokenId(),
                false
            ));
        }

        points.sort(Comparator.comparingDouble(RingPoint::angle));
        assignments.sort(Comparator.comparing(KeyAssignment::keyLabel));
        return new RingView(title, List.copyOf(points), List.copyOf(assignments));
    }

    private RingView withMovedMarkers(RingView ring, Set<String> movedKeyIds) {
        List<RingPoint> updatedPoints = ring.points().stream()
            .map(point -> {
                if ("key".equals(point.type()) && movedKeyIds.contains(point.id())) {
                    return point.withState("moved");
                }
                return point;
            })
            .toList();
        List<KeyAssignment> updatedAssignments = ring.assignments().stream()
            .map(assignment -> movedKeyIds.contains(assignment.keyId()) ? assignment.withMoved(true) : assignment)
            .toList();
        return new RingView(ring.title(), updatedPoints, updatedAssignments);
    }

    private List<KeyMovement> diffAssignments(List<KeyAssignment> before, List<KeyAssignment> after) {
        Map<String, KeyAssignment> beforeByKey = before.stream()
            .collect(Collectors.toMap(KeyAssignment::keyId, assignment -> assignment));
        return after.stream()
            .map(current -> {
                KeyAssignment previous = beforeByKey.get(current.keyId());
                if (previous == null || previous.serverId().equals(current.serverId())) {
                    return null;
                }
                return new KeyMovement(
                    current.keyId(),
                    current.keyLabel(),
                    previous.serverLabel(),
                    current.serverLabel()
                );
            })
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(KeyMovement::keyLabel))
            .toList();
    }

    private DistributionSummary distributionSummary(List<ServerNode> servers, List<KeyNode> keys, int replicas, String label) {
        NavigableMap<Long, RingToken> ring = buildRing(servers, replicas);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ServerNode server : servers) {
            counts.put(server.id(), 0);
        }
        for (KeyNode key : keys) {
            RingToken owner = ownerEntry(ring, hash(key.id())).getValue();
            counts.computeIfPresent(owner.serverId(), (ignored, count) -> count + 1);
        }
        List<ServerLoad> loads = servers.stream()
            .map(server -> new ServerLoad(server.id(), server.label(), server.color(), counts.get(server.id())))
            .toList();
        double mean = loads.stream().mapToInt(ServerLoad::keyCount).average().orElse(0.0);
        double variance = loads.stream()
            .mapToDouble(load -> Math.pow(load.keyCount() - mean, 2.0))
            .average()
            .orElse(0.0);
        int min = loads.stream().mapToInt(ServerLoad::keyCount).min().orElse(0);
        int max = loads.stream().mapToInt(ServerLoad::keyCount).max().orElse(0);
        return new DistributionSummary(label, loads, Math.sqrt(variance), min, max);
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
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put("s0", true);
        defaults.put("s1", false);
        defaults.put("s2", false);
        defaults.put("s3", false);
        defaults.put("s4", false);
        defaults.put("s5", false);
        return defaults;
    }

    private NavigableMap<Long, RingToken> buildRing(Collection<ServerNode> servers, int replicas) {
        NavigableMap<Long, RingToken> ring = new TreeMap<>();
        for (ServerNode server : servers) {
            for (int replicaIndex = 0; replicaIndex < replicas; replicaIndex++) {
                String tokenId = replicas == 1 ? server.id() : server.id() + "_" + replicaIndex;
                long position = hash(tokenId);
                while (ring.containsKey(position)) {
                    position++;
                }
                ring.put(position, new RingToken(tokenId, server.id(), server.label(), server.color(), replicaIndex));
            }
        }
        return ring;
    }

    private Map.Entry<Long, RingToken> ownerEntry(NavigableMap<Long, RingToken> ring, long keyHash) {
        Map.Entry<Long, RingToken> ceilingEntry = ring.ceilingEntry(keyHash);
        return ceilingEntry != null ? ceilingEntry : ring.firstEntry();
    }

    private List<KeyNode> defaultModuloKeys() {
        return List.of();
    }

    private List<KeyNode> sampleRingKeys() {
        return IntStream.range(0, 4)
            .mapToObj(index -> new KeyNode("k" + index, "key" + index))
            .toList();
    }

    private List<KeyNode> virtualSampleKeys() {
        return IntStream.range(0, 6)
            .mapToObj(index -> new KeyNode("vk" + index, "key" + index))
            .toList();
    }

    private List<KeyNode> statisticKeys() {
        return IntStream.range(0, 240)
            .mapToObj(index -> new KeyNode("dist-key-" + index, "key" + index))
            .toList();
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

    private double angle(long hashValue) {
        return (hashValue % 360_000) / 1_000.0;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ServerNode(String id, String label, String color) {
    }

    private record KeyNode(String id, String label) {
    }

    private record RingToken(String tokenId, String serverId, String serverLabel, String color, int replicaIndex) {
    }

    public record DashboardState(
        ModuloScenario modulo,
        RingScenario ring,
        RingScenario addNode,
        RingScenario removeNode,
        VirtualScenario virtualNodes
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

    public record RingScenario(
        String mode,
        String insight,
        String title,
        RingView before,
        RingView after,
        List<KeyMovement> movements,
        List<ClusterServer> servers,
        int totalKeys,
        int movedKeyCount
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

    public record RingView(
        String title,
        List<RingPoint> points,
        List<KeyAssignment> assignments
    ) {
    }

    public record RingPoint(
        String id,
        String label,
        String type,
        String ownerId,
        double angle,
        String color,
        String state,
        boolean emphasize
    ) {
        public RingPoint withState(String nextState) {
            return new RingPoint(id, label, type, ownerId, angle, color, nextState, emphasize);
        }
    }

    public record KeyAssignment(
        String keyId,
        String keyLabel,
        String serverId,
        String serverLabel,
        String serverColor,
        String targetPointId,
        boolean moved
    ) {
        public KeyAssignment withMoved(boolean nextMoved) {
            return new KeyAssignment(keyId, keyLabel, serverId, serverLabel, serverColor, targetPointId, nextMoved);
        }
    }

    public record KeyMovement(
        String keyId,
        String keyLabel,
        String fromServer,
        String toServer
    ) {
    }

    public record VirtualScenario(
        int replicas,
        RingView singleRing,
        RingView virtualRing,
        DistributionSummary singleDistribution,
        DistributionSummary virtualDistribution,
        String insight
    ) {
    }

    public record DistributionSummary(
        String label,
        List<ServerLoad> servers,
        double standardDeviation,
        int minLoad,
        int maxLoad
    ) {
    }

    public record ServerLoad(
        String serverId,
        String label,
        String color,
        int keyCount
    ) {
    }
}
