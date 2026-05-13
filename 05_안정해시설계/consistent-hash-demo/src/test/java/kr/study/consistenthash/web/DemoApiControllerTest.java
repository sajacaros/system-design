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
    void dashboardEndpointReturnsScenarioPayload() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modulo.baselineServerCount").value(1))
            .andExpect(jsonPath("$.modulo.resizedServerCount").value(1))
            .andExpect(jsonPath("$.modulo.afterBuckets.length()").value(1))
            .andExpect(jsonPath("$.modulo.afterBuckets[0].keys.length()").value(0))
            .andExpect(jsonPath("$.modulo.servers.length()").value(6))
            .andExpect(jsonPath("$.modulo.servers[0].active").value(true))
            .andExpect(jsonPath("$.modulo.servers[1].active").value(false))
            .andExpect(jsonPath("$.addNode.servers.length()").value(5))
            .andExpect(jsonPath("$.ring.before.assignments.length()").value(4))
            .andExpect(jsonPath("$.virtualNodes.replicas").value(32));
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
    void nodeChangeActionsExposeServerToggleState() throws Exception {
        mockMvc.perform(post("/api/node-change/add"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.servers[4].active").value(true));

        mockMvc.perform(post("/api/node-change/toggle").param("serverId", "s1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.servers[1].state").value("disabled"));
    }

    @Test
    void healthEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }
}
