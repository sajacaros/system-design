package kr.study.consistenthash.domain;

import static org.assertj.core.api.Assertions.assertThat;

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

        ConsistentHashDemoService.ModuloScenario scenario = service.toggleModuloServer("s1");

        assertThat(scenario.servers()).anyMatch(server -> server.serverId().equals("s1") && "disabled".equals(server.state()));
        assertThat(scenario.resizedServerCount()).isEqualTo(1);
    }

    @Test
    void addServerMovesOnlySubsetOfKeys() {
        ConsistentHashDemoService.RingScenario scenario = service.addNodeToCluster();

        assertThat(scenario.movedKeyCount()).isGreaterThan(0);
        assertThat(scenario.movedKeyCount()).isLessThan(scenario.totalKeys());
        assertThat(scenario.servers()).anyMatch(server -> server.serverId().equals("s4") && server.active());
    }

    @Test
    void disablingSpecificServerMarksItDisabledButKeepsCardVisible() {
        ConsistentHashDemoService.RingScenario scenario = service.toggleNodeInCluster("s1");

        assertThat(scenario.servers()).anyMatch(server -> server.serverId().equals("s1") && "disabled".equals(server.state()));
        assertThat(scenario.movedKeyCount()).isGreaterThan(0);
    }

    @Test
    void virtualNodesReduceDistributionDeviation() {
        ConsistentHashDemoService.VirtualScenario scenario = service.virtualNodes(64);

        assertThat(scenario.virtualDistribution().standardDeviation())
            .isLessThan(scenario.singleDistribution().standardDeviation());
    }
}
