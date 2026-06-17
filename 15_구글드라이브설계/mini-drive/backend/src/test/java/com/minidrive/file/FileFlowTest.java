package com.minidrive.file;

import com.minidrive.support.IntegrationTest;
import com.minidrive.user.User;
import com.minidrive.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileFlowTest extends IntegrationTest {

    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder encoder;

    private final ObjectMapper om = new ObjectMapper();

    private Long createUser(String email) {
        return userRepository.save(new User(email, encoder.encode("pw"), "nick")).getId();
    }

    @Test
    void upload_then_newVersion_conflict_and_overwrite() throws Exception {
        Long uid = createUser("u-files1@x.com");
        var f = new MockMultipartFile("file", "report.pdf", "application/pdf", "v1".getBytes());

        // upload 201 -> version 1, UPLOADED
        String body = mockMvc().perform(multipart("/api/files/upload").file(f)
                        .header("Authorization", bearer(uid, "u-files1@x.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = om.readTree(body);
        long fileId = json.get("id").asLong();

        // new version with stale baseVersion -> 409 CONFLICT
        var f2 = new MockMultipartFile("file", "report.pdf", "application/pdf", "v2".getBytes());
        mockMvc().perform(multipart("/api/files/" + fileId + "/content").file(f2)
                        .param("baseVersion", "99")
                        .header("Authorization", bearer(uid, "u-files1@x.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        // overwrite=true -> 200 version 2
        var f3 = new MockMultipartFile("file", "report.pdf", "application/pdf", "v2".getBytes());
        mockMvc().perform(multipart("/api/files/" + fileId + "/content").file(f3)
                        .param("baseVersion", "99").param("overwrite", "true")
                        .header("Authorization", bearer(uid, "u-files1@x.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void get_uploadedFile_downloadUrl_isGatewayPath() throws Exception {
        Long uid = createUser("u-utf8@x.com");
        var f = new MockMultipartFile("file", "hangul.txt", "text/plain",
                "가나다".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String body = mockMvc().perform(multipart("/api/files/upload").file(f)
                        .header("Authorization", bearer(uid, "u-utf8@x.com")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long fileId = om.readTree(body).get("id").asLong();

        // v1.6: downloadUrl is the gateway path, NOT a presigned URL. No MinIO host/SigV4 leaks.
        mockMvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/files/" + fileId)
                        .header("Authorization", bearer(uid, "u-utf8@x.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").value("/api/files/" + fileId + "/download"));
    }

    @Test
    void download_textFile_setsXAccelRedirect_utf8ContentType_and_disposition() throws Exception {
        Long uid = createUser("u-dl-utf8@x.com");
        var f = new MockMultipartFile("file", "한글.txt", "text/plain",
                "가나다".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String body = mockMvc().perform(multipart("/api/files/upload").file(f)
                        .header("Authorization", bearer(uid, "u-dl-utf8@x.com")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long fileId = om.readTree(body).get("id").asLong();

        // v1.6 gateway download: empty-body 200 with X-Accel-Redirect (internal-only) +
        // UTF-8 Content-Type + RFC5987 Content-Disposition. No presigned URL in the body.
        mockMvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/files/" + fileId + "/download")
                        .header("Authorization", bearer(uid, "u-dl-utf8@x.com")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-Accel-Redirect",
                                org.hamcrest.Matchers.startsWith("/_minio/")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", "text/plain; charset=UTF-8"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Disposition",
                                "attachment; filename*=UTF-8''%ED%95%9C%EA%B8%80.txt"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(""));
    }

    @Test
    void download_nonOwner_403_and_missing_404() throws Exception {
        Long owner = createUser("u-dl-owner@x.com");
        Long intruder = createUser("u-dl-intruder@x.com");
        var f = new MockMultipartFile("file", "p.txt", "text/plain", "x".getBytes());
        String body = mockMvc().perform(multipart("/api/files/upload").file(f)
                        .header("Authorization", bearer(owner, "u-dl-owner@x.com")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long fileId = om.readTree(body).get("id").asLong();

        // non-owner -> 403
        mockMvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/files/" + fileId + "/download")
                        .header("Authorization", bearer(intruder, "u-dl-intruder@x.com")))
                .andExpect(status().isForbidden());

        // unknown id -> 404
        mockMvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/files/999999/download")
                        .header("Authorization", bearer(owner, "u-dl-owner@x.com")))
                .andExpect(status().isNotFound());

        // unauthenticated -> 401
        mockMvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/files/" + fileId + "/download"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_withoutToken_401() throws Exception {
        mockMvc().perform(multipart("/api/files/upload")
                        .file(new MockMultipartFile("file", "x.txt", "text/plain", "x".getBytes())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void access_otherOwnersFile_403() throws Exception {
        Long owner = createUser("owner@x.com");
        Long intruder = createUser("intruder@x.com");
        var f = new MockMultipartFile("file", "secret.txt", "text/plain", "data".getBytes());
        String body = mockMvc().perform(multipart("/api/files/upload").file(f)
                        .header("Authorization", bearer(owner, "owner@x.com")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long fileId = om.readTree(body).get("id").asLong();

        mockMvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/files/" + fileId)
                        .header("Authorization", bearer(intruder, "intruder@x.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void upload_duplicateName_409_nameConflict() throws Exception {
        Long uid = createUser("dup@x.com");
        var f = new MockMultipartFile("file", "dup.txt", "text/plain", "a".getBytes());
        mockMvc().perform(multipart("/api/files/upload").file(f)
                        .header("Authorization", bearer(uid, "dup@x.com")))
                .andExpect(status().isCreated());
        var f2 = new MockMultipartFile("file", "dup.txt", "text/plain", "b".getBytes());
        mockMvc().perform(multipart("/api/files/upload").file(f2)
                        .header("Authorization", bearer(uid, "dup@x.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NAME_CONFLICT"));
    }
}
