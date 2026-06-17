package com.minidrive.auth;

import com.minidrive.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowTest extends IntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void signup_login_refresh_logout_flow() throws Exception {
        // signup 201
        mockMvc().perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@x.com\",\"password\":\"secret1\",\"nickname\":\"Al\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("a@x.com"));

        // duplicate email 409 EMAIL_TAKEN
        mockMvc().perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@x.com\",\"password\":\"secret1\",\"nickname\":\"Al\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));

        // login 200
        String body = mockMvc().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@x.com\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = om.readTree(body);
        String refresh = json.get("refreshToken").asText();

        // refresh 200 with rotation
        String refreshed = mockMvc().perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newRefresh = om.readTree(refreshed).get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(refresh);

        // old refresh now invalid (rotated) -> 401 INVALID_REFRESH
        mockMvc().perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH"));

        // logout 204, then refresh fails
        mockMvc().perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(json.get("user").get("id").asLong(), "a@x.com"))
                        .content("{\"refreshToken\":\"" + newRefresh + "\"}"))
                .andExpect(status().isNoContent());
        mockMvc().perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + newRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_badCredentials_401() throws Exception {
        mockMvc().perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"b@x.com\",\"password\":\"secret1\",\"nickname\":\"B\"}"));
        mockMvc().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"b@x.com\",\"password\":\"WRONG\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }
}
