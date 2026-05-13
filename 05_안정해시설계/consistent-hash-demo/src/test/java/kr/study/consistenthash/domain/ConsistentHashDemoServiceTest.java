package kr.study.consistenthash.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsistentHashDemoServiceTest {

    private final ConsistentHashDemoService service = new ConsistentHashDemoService();

    @Test
    void defaultModuloScenarioStartsWithOneEnabledServerAndZeroKeys() {
        ConsistentHashDemoService.ModuloScenario scenario = service.moduloScenario();

        assertThat(scenario.resizedServerCount()).isEqualTo(1);
        assertThat(scenario.afterBuckets()).hasSize(1);
        assertThat(scenario.afterBuckets().get(0).keys()).isEmpty();
        assertThat(scenario.servers()).hasSize(6);
        assertThat(scenario.servers()).filteredOn(ConsistentHashDemoService.ClusterServer::active).hasSize(1);
        assertThat(scenario.servers()).anyMatch(server -> server.serverId().equals("s0") && server.active());
        assertThat(scenario.movedKeyCount()).isZero();
    }

    @Test
    void moduloResizeMovesMostKeys() {
        service.resetModulo();
        for (int index = 0; index < 8; index++) {
            service.addModuloKey();
        }
        ConsistentHashDemoService.ModuloScenario scenario = service.toggleModuloServer("s1");

        assertThat(scenario.movedKeyCount()).isGreaterThanOrEqualTo(5);
        assertThat(scenario.movements()).hasSize(scenario.movedKeyCount());
    }

    @Test
    void canAddModuloKeyDynamically() {
        service.resetModulo();
        ConsistentHashDemoService.ModuloScenario scenario = service.addModuloKey();

        int totalKeys = scenario.afterBuckets().stream().mapToInt(bucket -> bucket.keys().size()).sum();
        assertThat(totalKeys).isEqualTo(1);
        assertThat(scenario.afterBuckets()).hasSize(1);
    }

    @Test
    void addingKeyClearsPreviousMovedMarkers() {
        service.resetModulo();
        for (int index = 0; index < 6; index++) {
            service.addModuloKey();
        }
        service.toggleModuloServer("s1");

        ConsistentHashDemoService.ModuloScenario scenario = service.addModuloKey();

        assertThat(scenario.movedKeyCount()).isZero();
        assertThat(scenario.movements()).isEmpty();
    }

    @Test
    void disablingModuloServerKeepsItsCardVisible() {
        service.resetModulo();
        service.toggleModuloServer("s1");

        ConsistentHashDemoService.ModuloScenario scenario = service.toggleModuloServer("s1");

        assertThat(scenario.servers()).anyMatch(server -> server.serverId().equals("s1") && "disabled".equals(server.state()));
        assertThat(scenario.resizedServerCount()).isEqualTo(1);
    }

    @Test
    void requestedModuloServerCountBuildsPreviewScenario() {
        ConsistentHashDemoService.ModuloScenario scenario = service.moduloScenario(3);

        assertThat(scenario.baselineServerCount()).isEqualTo(1);
        assertThat(scenario.resizedServerCount()).isEqualTo(3);
        assertThat(scenario.afterBuckets()).hasSize(3);
        assertThat(scenario.servers()).filteredOn(ConsistentHashDemoService.ClusterServer::active).hasSize(3);
    }

    @Test
    void defaultHashRingStartsWithOneEnabledServerAndNoKeys() {
        ConsistentHashDemoService.HashRingScenario scenario = service.hashRing();

        assertThat(scenario.serverCount()).isEqualTo(1);
        assertThat(scenario.keyCount()).isZero();
        assertThat(scenario.tokens()).hasSize(1);
        assertThat(scenario.servers()).filteredOn(ConsistentHashDemoService.HashRingServer::active).hasSize(1);
        assertThat(scenario.servers()).filteredOn(ConsistentHashDemoService.HashRingServer::active)
            .allMatch(server -> server.tokenCount() == 1);
        assertThat(scenario.ring().assignments()).isEmpty();
        assertThat(scenario.ring().points()).filteredOn(point -> point.type().equals("server")).hasSize(1);
        assertThat(scenario.servers()).anyMatch(server ->
            server.serverId().equals("s0") && server.active() && !server.canToggle()
        );
    }

    @Test
    void addingHashRingKeyCreatesAssignment() {
        service.resetHashRing();

        ConsistentHashDemoService.HashRingScenario scenario = service.addHashRingKey();

        assertThat(scenario.keyCount()).isEqualTo(1);
        assertThat(scenario.ring().assignments()).hasSize(1);
        assertThat(scenario.ring().points()).filteredOn(point -> point.type().equals("key")).hasSize(1);
    }

    @Test
    void hashRingCanEnableServerButKeepsCardVisible() {
        service.resetHashRing();

        ConsistentHashDemoService.HashRingScenario scenario = service.toggleHashRingServer("s1");

        assertThat(scenario.serverCount()).isEqualTo(2);
        assertThat(scenario.tokens()).hasSize(2);
        assertThat(scenario.servers()).anyMatch(server ->
            server.serverId().equals("s1")
                && server.active()
                && "active".equals(server.state())
                && "Disable".equals(server.actionLabel())
        );
        assertThat(scenario.servers()).anyMatch(server ->
            server.serverId().equals("s0")
                && server.active()
                && "Disable".equals(server.actionLabel())
        );
        assertThat(scenario.ring().points())
            .filteredOn(point -> point.type().equals("server"))
            .anyMatch(point -> point.ownerId().equals("s1"));
    }

    @Test
    void hashRingFixedServerPositionsKeepDistributionReasonablyBalanced() {
        service.resetHashRing();
        service.toggleHashRingServer("s1");
        service.toggleHashRingServer("s2");
        service.toggleHashRingServer("s3");
        service.toggleHashRingServer("s4");
        service.toggleHashRingServer("s5");
        ConsistentHashDemoService.HashRingScenario scenario = null;
        for (int index = 0; index < 400; index++) {
            scenario = service.addHashRingKey();
        }

        assertThat(scenario).isNotNull();
        List<Integer> keyCounts = scenario.servers().stream()
            .filter(ConsistentHashDemoService.HashRingServer::active)
            .map(ConsistentHashDemoService.HashRingServer::keyCount)
            .toList();

        assertThat(keyCounts).hasSize(6);
        assertThat(keyCounts.stream().mapToInt(Integer::intValue).max().orElseThrow()
            - keyCounts.stream().mapToInt(Integer::intValue).min().orElseThrow())
            .isLessThanOrEqualTo(35);
    }

    @Test
    void hashRingAssignmentsUseFirstClockwiseServerToken() {
        service.resetHashRing();
        ConsistentHashDemoService.HashRingScenario scenario = null;
        for (int index = 0; index < 20; index++) {
            scenario = service.addHashRingKey();
        }

        assertThat(scenario).isNotNull();
        for (ConsistentHashDemoService.KeyAssignment assignment : scenario.ring().assignments()) {
            long expectedTargetHash = scenario.tokens().stream()
                .mapToLong(ConsistentHashDemoService.HashRingToken::hash)
                .filter(tokenHash -> tokenHash >= assignment.keyHash())
                .findFirst()
                .orElse(scenario.tokens().get(0).hash());

            assertThat(assignment.targetHash()).isEqualTo(expectedTargetHash);
        }
    }

    @Test
    void hashRingWrapsKeysPastLastTokenToFirstToken() {
        service.resetHashRing();
        ConsistentHashDemoService.HashRingScenario scenario = null;
        ConsistentHashDemoService.KeyAssignment wrapAssignment = null;

        for (int index = 0; index < 5_000 && wrapAssignment == null; index++) {
            scenario = service.addHashRingKey();
            long lastTokenHash = scenario.tokens().stream()
                .map(ConsistentHashDemoService.HashRingToken::hash)
                .max(Comparator.naturalOrder())
                .orElseThrow();
            wrapAssignment = scenario.ring().assignments().stream()
                .filter(assignment -> assignment.keyHash() > lastTokenHash)
                .findFirst()
                .orElse(null);
        }

        assertThat(scenario).isNotNull();
        assertThat(wrapAssignment).isNotNull();
        assertThat(wrapAssignment.targetHash()).isEqualTo(scenario.tokens().get(0).hash());
    }

    @Test
    void resetHashRingClearsKeysAndAssignments() {
        service.toggleHashRingServer("s1");
        service.addHashRingKey();
        service.addHashRingKey();

        ConsistentHashDemoService.HashRingScenario scenario = service.resetHashRing();

        assertThat(scenario.keyCount()).isZero();
        assertThat(scenario.serverCount()).isEqualTo(1);
        assertThat(scenario.ring().assignments()).isEmpty();
        assertThat(scenario.ring().points()).filteredOn(point -> point.type().equals("key")).isEmpty();
    }

    @Test
    void defaultVirtualNodesScenarioHasReplicatedTokensAndNoKeys() {
        ConsistentHashDemoService.VirtualNodesScenario scenario = service.virtualNodes();

        assertThat(scenario.serverCount()).isEqualTo(6);
        assertThat(scenario.replicas()).isEqualTo(16);
        assertThat(scenario.tokenCount()).isEqualTo(96);
        assertThat(scenario.tokens()).hasSize(96);
        assertThat(scenario.servers()).hasSize(6);
        assertThat(scenario.servers()).allMatch(server -> server.tokenCount() == 16);
        assertThat(scenario.ring().assignments()).isEmpty();
        assertThat(scenario.ring().points()).filteredOn(point -> point.type().equals("virtual-server")).hasSize(96);
    }

    @Test
    void addingVirtualNodeKeyCreatesAssignmentToFirstClockwiseToken() {
        service.resetVirtualNodes();
        ConsistentHashDemoService.VirtualNodesScenario scenario = null;
        for (int index = 0; index < 20; index++) {
            scenario = service.addVirtualNodeKey();
        }

        assertThat(scenario).isNotNull();
        assertThat(scenario.keyCount()).isEqualTo(20);
        assertThat(scenario.ring().assignments()).hasSize(20);
        for (ConsistentHashDemoService.KeyAssignment assignment : scenario.ring().assignments()) {
            long expectedTargetHash = scenario.tokens().stream()
                .mapToLong(ConsistentHashDemoService.HashRingToken::hash)
                .filter(tokenHash -> tokenHash >= assignment.keyHash())
                .findFirst()
                .orElse(scenario.tokens().get(0).hash());

            assertThat(assignment.targetHash()).isEqualTo(expectedTargetHash);
        }
    }

    @Test
    void updatingVirtualNodeReplicasRebuildsTokenCountAndKeepsKeys() {
        service.resetVirtualNodes();
        service.addVirtualNodeKey();

        ConsistentHashDemoService.VirtualNodesScenario scenario = service.updateVirtualReplicas(32);

        assertThat(scenario.replicas()).isEqualTo(32);
        assertThat(scenario.tokenCount()).isEqualTo(192);
        assertThat(scenario.keyCount()).isEqualTo(1);
        assertThat(scenario.servers()).allMatch(server -> server.tokenCount() == 32);
    }

    @Test
    void virtualNodeServersCanBeDisabledAndHiddenFromRing() {
        service.resetVirtualNodes();

        ConsistentHashDemoService.VirtualNodesScenario scenario = service.toggleVirtualNodeServer("s1");

        assertThat(scenario.serverCount()).isEqualTo(5);
        assertThat(scenario.tokenCount()).isEqualTo(80);
        assertThat(scenario.servers()).anyMatch(server ->
            server.serverId().equals("s1")
                && !server.active()
                && "disabled".equals(server.state())
                && "Enable".equals(server.actionLabel())
                && server.tokenCount() == 0
        );
        assertThat(scenario.ring().points())
            .filteredOn(point -> point.type().equals("virtual-server"))
            .noneMatch(point -> point.ownerId().equals("s1"));
    }

    @Test
    void resetVirtualNodesClearsKeysAndRestoresDefaultReplicas() {
        service.updateVirtualReplicas(32);
        service.toggleVirtualNodeServer("s1");
        service.addVirtualNodeKey();
        service.addVirtualNodeKey();

        ConsistentHashDemoService.VirtualNodesScenario scenario = service.resetVirtualNodes();

        assertThat(scenario.replicas()).isEqualTo(16);
        assertThat(scenario.tokenCount()).isEqualTo(96);
        assertThat(scenario.keyCount()).isZero();
        assertThat(scenario.serverCount()).isEqualTo(6);
        assertThat(scenario.servers()).allMatch(ConsistentHashDemoService.VirtualServer::active);
        assertThat(scenario.ring().assignments()).isEmpty();
    }
}
