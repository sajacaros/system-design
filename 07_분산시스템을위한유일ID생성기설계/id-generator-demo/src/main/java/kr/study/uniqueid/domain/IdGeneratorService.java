package kr.study.uniqueid.domain;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import kr.study.uniqueid.config.DemoProperties;
import kr.study.uniqueid.domain.SnowflakeIdGenerator.DecodedId;

@Service
public class IdGeneratorService {
    private static final int MAX_BATCH_SIZE = 200;
    private static final int RECENT_LIMIT = 120;
    private static final int MIN_RESPONSE_DELAY_MS = 300;
    private static final int MAX_RESPONSE_DELAY_MS = 500;

    private final DemoProperties properties;
    private final RestTemplate restTemplate;
    private final SnowflakeIdGenerator generator;
    private final List<NodeEndpoint> peerEndpoints;
    private final Deque<GeneratedId> recent = new ArrayDeque<>();
    private final AtomicLong localGenerationOrder = new AtomicLong();

    public IdGeneratorService(DemoProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.generator = new SnowflakeIdGenerator(properties.datacenterId(), properties.workerId());
        this.peerEndpoints = parsePeers(properties.peers());
    }

    public NodeBatch generateLocal(GenerateCommand command) {
        int count = normalizeCount(command == null ? properties.defaultBatchSize() : command.count());
        List<GeneratedId> generated = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long numericId = generator.nextId();
            DecodedId decoded = generator.decode(numericId);
            GeneratedId id = new GeneratedId(
                numericId,
                Long.toUnsignedString(numericId),
                properties.nodeId(),
                decoded.timestampMillis(),
                decoded.instant().toString(),
                decoded.datacenterId(),
                decoded.workerId(),
                decoded.sequence(),
                localGenerationOrder.incrementAndGet(),
                toBinary(numericId)
            );
            generated.add(id);
            remember(id);
        }
        int responseDelayMillis = randomResponseDelayMillis();
        sleep(responseDelayMillis);
        return new NodeBatch(properties.nodeId(), "self", true, "generated", responseDelayMillis, generated);
    }

    public ClusterGenerateResult generateCluster(ClusterGenerateCommand command) {
        int countPerNode = normalizeCount(command == null ? properties.defaultBatchSize() : command.countPerNode());
        List<NodeEndpoint> endpoints = allEndpoints();

        List<GeneratedCall> completedCalls = Collections.synchronizedList(new ArrayList<>());
        AtomicLong requestOrder = new AtomicLong();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (NodeEndpoint endpoint : endpoints) {
            futures.add(CompletableFuture.runAsync(() -> {
                for (int index = 0; index < countPerNode; index++) {
                    long order = requestOrder.incrementAndGet();
                    NodeBatch batch = generateFrom(endpoint, 1);
                    completedCalls.add(new GeneratedCall(order, batch));
                }
            }));
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        List<GeneratedCall> calls = List.copyOf(completedCalls);
        List<NodeBatch> batches = aggregateByNode(calls);

        List<GeneratedRow> arrivalOrder = new ArrayList<>();
        int arrivalIndex = 1;
        for (GeneratedCall call : calls) {
            NodeBatch batch = call.batch();
            for (GeneratedId id : batch.generated()) {
                arrivalOrder.add(new GeneratedRow(
                    (int) call.requestOrder(),
                    arrivalIndex++,
                    0,
                    batch.responseDelayMillis(),
                    id
                ));
            }
        }

        List<GeneratedRow> sortedById = arrivalOrder.stream()
            .sorted(Comparator.comparingLong(row -> row.id().numericId()))
            .map(row -> new GeneratedRow(
                row.requestOrder(),
                row.arrivalOrder(),
                0,
                row.responseDelayMillis(),
                row.id()
            ))
            .toList();

        List<GeneratedRow> sortedWithOrder = new ArrayList<>(sortedById.size());
        for (int index = 0; index < sortedById.size(); index++) {
            GeneratedRow row = sortedById.get(index);
            sortedWithOrder.add(new GeneratedRow(
                row.requestOrder(),
                row.arrivalOrder(),
                index + 1,
                row.responseDelayMillis(),
                row.id()
            ));
        }

        boolean timeOrdered = true;
        long previousTimestamp = Long.MIN_VALUE;
        for (GeneratedRow row : sortedWithOrder) {
            if (row.id().timestampMillis() < previousTimestamp) {
                timeOrdered = false;
                break;
            }
            previousTimestamp = row.id().timestampMillis();
        }

        return new ClusterGenerateResult(
            Instant.now().toString(),
            countPerNode,
            batches,
            arrivalOrder,
            sortedWithOrder,
            timeOrdered,
            "ID는 timestamp -> datacenterId -> workerId -> sequence 비트 순서로 정렬됩니다."
        );
    }

    public ClusterSnapshot clusterSnapshot() {
        List<NodeSnapshot> nodes = allEndpoints().stream()
            .map(this::snapshotFrom)
            .toList();
        return new ClusterSnapshot(
            properties.nodeId(),
            properties.defaultBatchSize(),
            SnowflakeIdGenerator.CUSTOM_EPOCH_MILLIS,
            Instant.ofEpochMilli(SnowflakeIdGenerator.CUSTOM_EPOCH_MILLIS).toString(),
            new BitLayout(
                SnowflakeIdGenerator.UNUSED_SIGN_BITS,
                SnowflakeIdGenerator.TIMESTAMP_BITS,
                SnowflakeIdGenerator.DATACENTER_BITS,
                SnowflakeIdGenerator.WORKER_BITS,
                SnowflakeIdGenerator.SEQUENCE_BITS
            ),
            nodes
        );
    }

    public NodeSnapshot localSnapshot() {
        List<GeneratedId> recentIds;
        synchronized (recent) {
            recentIds = List.copyOf(recent);
        }
        return new NodeSnapshot(
            properties.nodeId(),
            "self",
            true,
            "ready",
            properties.datacenterId(),
            properties.workerId(),
            recentIds.size(),
            recentIds
        );
    }

    private NodeBatch generateFrom(NodeEndpoint endpoint, int count) {
        if (endpoint.local()) {
            return generateLocal(new GenerateCommand(count));
        }

        try {
            NodeBatch batch = restTemplate.postForObject(
                endpoint.url() + "/internal/generate",
                new GenerateCommand(count),
                NodeBatch.class
            );
            return Objects.requireNonNull(batch);
        } catch (RuntimeException ex) {
            return new NodeBatch(endpoint.nodeId(), endpoint.url(), false, shortMessage(ex), 0, List.of());
        }
    }

    private NodeSnapshot snapshotFrom(NodeEndpoint endpoint) {
        if (endpoint.local()) {
            return localSnapshot();
        }

        try {
            NodeSnapshot snapshot = restTemplate.getForObject(endpoint.url() + "/internal/snapshot", NodeSnapshot.class);
            if (snapshot == null) {
                return endpoint.failedSnapshot("empty response");
            }
            return snapshot.withEndpoint(endpoint.url());
        } catch (RuntimeException ex) {
            return endpoint.failedSnapshot(shortMessage(ex));
        }
    }

    private List<NodeEndpoint> allEndpoints() {
        List<NodeEndpoint> endpoints = new ArrayList<>();
        endpoints.add(new NodeEndpoint(properties.nodeId(), "self", true));
        endpoints.addAll(peerEndpoints);
        return endpoints;
    }

    private List<NodeBatch> aggregateByNode(List<GeneratedCall> calls) {
        Map<String, MutableNodeBatch> batches = new LinkedHashMap<>();
        for (GeneratedCall call : calls) {
            NodeBatch batch = call.batch();
            MutableNodeBatch aggregate = batches.computeIfAbsent(
                batch.nodeId(),
                ignored -> new MutableNodeBatch(batch.nodeId(), batch.endpoint())
            );
            aggregate.ok = aggregate.ok && batch.ok();
            if (!batch.ok()) {
                aggregate.messages.add(batch.message());
            }
            aggregate.totalDelayMillis += batch.responseDelayMillis();
            aggregate.generated.addAll(batch.generated());
        }

        return batches.values().stream()
            .map(batch -> new NodeBatch(
                batch.nodeId,
                batch.endpoint,
                batch.ok,
                batch.messages.isEmpty() ? "generated" : String.join("; ", batch.messages),
                batch.totalDelayMillis,
                List.copyOf(batch.generated)
            ))
            .toList();
    }

    private List<NodeEndpoint> parsePeers(List<String> peerValues) {
        if (peerValues == null || peerValues.isEmpty()) {
            return List.of();
        }

        return peerValues.stream()
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(value -> {
                String[] parts = value.split("=", 2);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Peer must be formatted as NODE_ID=http://host:port: " + value);
                }
                return new NodeEndpoint(parts[0].trim(), parts[1].trim(), false);
            })
            .toList();
    }

    private void remember(GeneratedId id) {
        synchronized (recent) {
            recent.addFirst(id);
            while (recent.size() > RECENT_LIMIT) {
                recent.removeLast();
            }
        }
    }

    private int normalizeCount(int requested) {
        if (requested <= 0) {
            return properties.defaultBatchSize();
        }
        return Math.min(requested, MAX_BATCH_SIZE);
    }

    private String toBinary(long id) {
        String binary = Long.toBinaryString(id);
        return "0".repeat(Long.SIZE - binary.length()) + binary;
    }

    private String shortMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 120 ? message.substring(0, 120) + "..." : message;
    }

    private int randomResponseDelayMillis() {
        return ThreadLocalRandom.current().nextInt(MIN_RESPONSE_DELAY_MS, MAX_RESPONSE_DELAY_MS + 1);
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while delaying response", ex);
        }
    }

    public record GenerateCommand(int count) {
    }

    public record ClusterGenerateCommand(int countPerNode) {
    }

    public record ClusterGenerateResult(
        String requestedAt,
        int countPerNode,
        List<NodeBatch> nodes,
        List<GeneratedRow> arrivalOrder,
        List<GeneratedRow> sortedById,
        boolean timeOrdered,
        String orderingRule
    ) {
    }

    public record NodeBatch(
        String nodeId,
        String endpoint,
        boolean ok,
        String message,
        long responseDelayMillis,
        List<GeneratedId> generated
    ) {
    }

    public record GeneratedRow(
        int requestOrder,
        int arrivalOrder,
        int sortedOrder,
        long responseDelayMillis,
        GeneratedId id
    ) {
    }

    public record GeneratedId(
        long numericId,
        String id,
        String nodeId,
        long timestampMillis,
        String timestampIso,
        long datacenterId,
        long workerId,
        long sequence,
        long localOrder,
        String binary
    ) {
    }

    public record ClusterSnapshot(
        String coordinatorNodeId,
        int defaultBatchSize,
        long customEpochMillis,
        String customEpochIso,
        BitLayout bitLayout,
        List<NodeSnapshot> nodes
    ) {
    }

    public record BitLayout(
        int sign,
        int timestamp,
        int datacenter,
        int worker,
        int sequence
    ) {
    }

    public record NodeSnapshot(
        String nodeId,
        String endpoint,
        boolean ok,
        String message,
        long datacenterId,
        long workerId,
        int recentCount,
        List<GeneratedId> recent
    ) {
        NodeSnapshot withEndpoint(String endpoint) {
            return new NodeSnapshot(nodeId, endpoint, ok, message, datacenterId, workerId, recentCount, recent);
        }
    }

    private record NodeEndpoint(String nodeId, String url, boolean local) {
        NodeSnapshot failedSnapshot(String message) {
            return new NodeSnapshot(nodeId, url, false, message, -1, -1, 0, List.of());
        }
    }

    private record GeneratedCall(
        long requestOrder,
        NodeBatch batch
    ) {
    }

    private static class MutableNodeBatch {
        private final String nodeId;
        private final String endpoint;
        private final List<String> messages = new ArrayList<>();
        private final List<GeneratedId> generated = new ArrayList<>();
        private boolean ok = true;
        private long totalDelayMillis;

        private MutableNodeBatch(String nodeId, String endpoint) {
            this.nodeId = nodeId;
            this.endpoint = endpoint;
        }
    }
}
