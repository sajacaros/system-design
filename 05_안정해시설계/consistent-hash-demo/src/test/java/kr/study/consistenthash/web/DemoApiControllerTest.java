package kr.study.consistenthash.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DemoApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dashboardEndpointReturnsModuloHashRingAndVirtualNodesPayload() throws Exception {
        mockMvc.perform(post("/api/modulo/reset"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/hashring/reset"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/virtual/reset"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modulo.baselineServerCount").value(1))
            .andExpect(jsonPath("$.modulo.resizedServerCount").value(1))
            .andExpect(jsonPath("$.modulo.afterBuckets.length()").value(1))
            .andExpect(jsonPath("$.modulo.servers.length()").value(6))
            .andExpect(jsonPath("$.hashRing.serverCount").value(1))
            .andExpect(jsonPath("$.hashRing.keyCount").value(0))
            .andExpect(jsonPath("$.hashRing.tokens.length()").value(1))
            .andExpect(jsonPath("$.hashRing.servers.length()").value(6))
            .andExpect(jsonPath("$.virtualNodes.serverCount").value(6))
            .andExpect(jsonPath("$.virtualNodes.replicas").value(16))
            .andExpect(jsonPath("$.virtualNodes.tokenCount").value(96))
            .andExpect(jsonPath("$.virtualNodes.ring.assignments.length()").value(0))
            .andExpect(jsonPath("$.ring").doesNotExist())
            .andExpect(jsonPath("$.addNode").doesNotExist());
    }

    @Test
    void dashboardEndpointUsesRequestedModuloServerCount() throws Exception {
        mockMvc.perform(post("/api/modulo/reset"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard").param("moduloServers", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modulo.baselineServerCount").value(1))
            .andExpect(jsonPath("$.modulo.resizedServerCount").value(3))
            .andExpect(jsonPath("$.modulo.afterBuckets.length()").value(3))
            .andExpect(jsonPath("$.modulo.servers[2].active").value(true))
            .andExpect(jsonPath("$.modulo.servers[3].active").value(false));
    }

    @Test
    void addModuloKeyEndpointReturnsUpdatedScenario() throws Exception {
        mockMvc.perform(post("/api/modulo/reset"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resizedServerCount").value(1))
            .andExpect(jsonPath("$.afterBuckets.length()").value(1));

        mockMvc.perform(post("/api/modulo/keys"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.afterBuckets[*].keys.length()")
                .isArray());
    }

    @Test
    void moduloServerToggleUpdatesServerCards() throws Exception {
        mockMvc.perform(post("/api/modulo/reset"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/modulo/toggle").param("serverId", "s1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resizedServerCount").value(2))
            .andExpect(jsonPath("$.servers[1].state").value("active"));
    }

    @Test
    void hashRingEndpointReturnsCurrentRing() throws Exception {
        mockMvc.perform(post("/api/hashring/reset"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/hashring"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serverCount").value(1))
            .andExpect(jsonPath("$.keyCount").value(0))
            .andExpect(jsonPath("$.tokens.length()").value(1))
            .andExpect(jsonPath("$.ring.assignments.length()").value(0));
    }

    @Test
    void hashRingKeyEndpointAddsAssignment() throws Exception {
        mockMvc.perform(post("/api/hashring/reset"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/hashring/keys"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.keyCount").value(1))
            .andExpect(jsonPath("$.ring.assignments.length()").value(1))
            .andExpect(jsonPath("$.ring.assignments[0].keyId").value("key0"))
            .andExpect(jsonPath("$.ring.points.length()").value(2));
    }

    @Test
    void hashRingServerToggleEnablesServer() throws Exception {
        mockMvc.perform(post("/api/hashring/reset"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/hashring/servers/toggle").param("serverId", "s1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serverCount").value(2))
            .andExpect(jsonPath("$.tokens.length()").value(2))
            .andExpect(jsonPath("$.ring.points.length()").value(2))
            .andExpect(jsonPath("$.servers[1].active").value(true))
            .andExpect(jsonPath("$.servers[1].actionLabel").value("Disable"))
            .andExpect(jsonPath("$.servers[0].actionLabel").value("Disable"));
    }

    @Test
    void hashRingResetEndpointClearsAssignments() throws Exception {
        mockMvc.perform(post("/api/hashring/keys"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/hashring/reset"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.keyCount").value(0))
            .andExpect(jsonPath("$.ring.assignments.length()").value(0));
    }

    @Test
    void virtualNodesEndpointReturnsCurrentScenario() throws Exception {
        mockMvc.perform(post("/api/virtual/reset"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/virtual"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serverCount").value(6))
            .andExpect(jsonPath("$.replicas").value(16))
            .andExpect(jsonPath("$.tokenCount").value(96))
            .andExpect(jsonPath("$.servers.length()").value(6))
            .andExpect(jsonPath("$.ring.points.length()").value(96));
    }

    @Test
    void virtualNodesKeyEndpointAddsAssignment() throws Exception {
        mockMvc.perform(post("/api/virtual/reset"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/virtual/keys"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.keyCount").value(1))
            .andExpect(jsonPath("$.ring.assignments.length()").value(1))
            .andExpect(jsonPath("$.ring.assignments[0].keyId").value("key0"))
            .andExpect(jsonPath("$.ring.points.length()").value(97));
    }

    @Test
    void virtualNodesReplicaEndpointRebuildsRing() throws Exception {
        mockMvc.perform(post("/api/virtual/reset"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/virtual/replicas").param("replicas", "32"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.replicas").value(32))
            .andExpect(jsonPath("$.tokenCount").value(192))
            .andExpect(jsonPath("$.servers[0].tokenCount").value(32));
    }

    @Test
    void virtualNodesServerToggleDisablesServerAndRemovesItsTokens() throws Exception {
        mockMvc.perform(post("/api/virtual/reset"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/virtual/servers/toggle").param("serverId", "s1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serverCount").value(5))
            .andExpect(jsonPath("$.tokenCount").value(80))
            .andExpect(jsonPath("$.servers[1].active").value(false))
            .andExpect(jsonPath("$.servers[1].tokenCount").value(0))
            .andExpect(jsonPath("$.servers[1].actionLabel").value("Enable"));
    }

    @Test
    void healthEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }
}
