package com.minidrive.folder;

import com.minidrive.support.IntegrationTest;
import com.minidrive.user.User;
import com.minidrive.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FolderFlowTest extends IntegrationTest {

    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder encoder;
    private final ObjectMapper om = new ObjectMapper();

    private Long createUser(String email) {
        return userRepository.save(new User(email, encoder.encode("pw"), "n")).getId();
    }

    @Test
    void create_duplicate_409_and_other_owner_403() throws Exception {
        Long uid = createUser("fo1@x.com");
        Long other = createUser("fo2@x.com");

        String body = mockMvc().perform(post("/api/folders").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(uid, "fo1@x.com"))
                        .content("{\"parentId\":null,\"name\":\"Docs\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long folderId = om.readTree(body).get("id").asLong();

        // duplicate name -> 409
        mockMvc().perform(post("/api/folders").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(uid, "fo1@x.com"))
                        .content("{\"parentId\":null,\"name\":\"Docs\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NAME_CONFLICT"));

        // other user deletes -> 403
        mockMvc().perform(delete("/api/folders/" + folderId)
                        .header("Authorization", bearer(other, "fo2@x.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_withoutToken_401() throws Exception {
        mockMvc().perform(post("/api/folders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":null,\"name\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }
}
