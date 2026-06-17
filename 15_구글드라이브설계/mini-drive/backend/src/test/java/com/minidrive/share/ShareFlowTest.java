package com.minidrive.share;

import com.minidrive.file.FileEntity;
import com.minidrive.file.FileRepository;
import com.minidrive.file.FileStatus;
import com.minidrive.support.IntegrationTest;
import com.minidrive.user.User;
import com.minidrive.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShareFlowTest extends IntegrationTest {

    @Autowired
    UserRepository userRepository;
    @Autowired
    FileRepository fileRepository;
    @Autowired
    ShareLinkRepository shareRepository;
    @Autowired
    PasswordEncoder encoder;
    private final ObjectMapper om = new ObjectMapper();

    private long upload(Long uid, String email, String name) throws Exception {
        String body = mockMvc().perform(multipart("/api/files/upload")
                        .file(new MockMultipartFile("file", name, "text/plain", "x".getBytes()))
                        .header("Authorization", bearer(uid, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("id").asLong();
    }

    @Test
    void create_share_then_public_resolve_and_disable() throws Exception {
        Long uid = userRepository.save(new User("sh@x.com", encoder.encode("pw"), "n")).getId();
        long fileId = upload(uid, "sh@x.com", "s.txt");

        String body = mockMvc().perform(post("/api/files/" + fileId + "/share")
                        .contentType("application/json").content("{}")
                        .header("Authorization", bearer(uid, "sh@x.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isActive").value(true))
                .andReturn().getResponse().getContentAsString();
        String token = om.readTree(body).get("token").asText();
        long shareId = om.readTree(body).get("id").asLong();

        // public resolve works (unauthenticated) — v1.2 path /api/public/share/{token}
        mockMvc().perform(get("/api/public/share/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId))   // v1.2: first field is `id` (file id)
                .andExpect(jsonPath("$.extension").value("txt"))
                // v1.6: downloadUrl is the gateway path, not a presigned URL.
                .andExpect(jsonPath("$.downloadUrl")
                        .value("/api/public/share/" + token + "/download"));

        // v1.6 gateway download works (unauthenticated): empty body 200 + X-Accel-Redirect (internal)
        // + UTF-8 Content-Type + RFC5987 Content-Disposition. No presigned URL in body.
        mockMvc().perform(get("/api/public/share/" + token + "/download"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-Accel-Redirect",
                                org.hamcrest.Matchers.startsWith("/_minio/")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", "text/plain; charset=UTF-8"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Disposition",
                                "attachment; filename*=UTF-8''s.txt"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(""));

        // disable -> 204, then public resolve 410 DISABLED
        mockMvc().perform(delete("/api/share/" + shareId).header("Authorization", bearer(uid, "sh@x.com")))
                .andExpect(status().isNoContent());
        mockMvc().perform(get("/api/public/share/" + token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("DISABLED"));

        // v1.6 bypass-0%: the gateway download endpoint ALSO returns 410 DISABLED immediately
        // (no presign-TTL window survives disabling).
        mockMvc().perform(get("/api/public/share/" + token + "/download"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("DISABLED"));
    }

    @Test
    void public_invalidToken_404_and_expired_410() throws Exception {
        // invalid token (metadata + gateway download both 404 INVALID_LINK)
        mockMvc().perform(get("/api/public/share/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_LINK"));
        mockMvc().perform(get("/api/public/share/does-not-exist/download"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_LINK"));

        // expired link
        Long uid = userRepository.save(new User("sh2@x.com", encoder.encode("pw"), "n")).getId();
        FileEntity file = new FileEntity(null, uid, "e.txt", "txt");
        file.setStatus(FileStatus.UPLOADED);
        file.setObjectKey("users/" + uid + "/0");
        file = fileRepository.save(file);
        ShareLink link = new ShareLink(file.getId(), "expired-token-xyz", Instant.now().minusSeconds(60));
        shareRepository.save(link);
        mockMvc().perform(get("/api/public/share/expired-token-xyz"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("EXPIRED"));
        // v1.6: gateway download re-validates per request -> immediate 410 EXPIRED
        mockMvc().perform(get("/api/public/share/expired-token-xyz/download"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("EXPIRED"));
    }

    @Test
    void listMine_returns_only_owner_shares_sorted_desc() throws Exception {
        Long owner = userRepository.save(new User("list@x.com", encoder.encode("pw"), "n")).getId();
        Long other = userRepository.save(new User("list2@x.com", encoder.encode("pw"), "n")).getId();

        long f1 = upload(owner, "list@x.com", "a.txt");
        long f2 = upload(owner, "list@x.com", "b.txt");
        long fOther = upload(other, "list2@x.com", "c.txt");

        // owner creates two shares (f2 created last -> should sort first)
        mockMvc().perform(post("/api/files/" + f1 + "/share").contentType("application/json").content("{}")
                .header("Authorization", bearer(owner, "list@x.com"))).andExpect(status().isCreated());
        mockMvc().perform(post("/api/files/" + f2 + "/share").contentType("application/json").content("{}")
                .header("Authorization", bearer(owner, "list@x.com"))).andExpect(status().isCreated());
        // other user creates a share that must NOT leak into owner's list
        mockMvc().perform(post("/api/files/" + fOther + "/share").contentType("application/json").content("{}")
                .header("Authorization", bearer(other, "list2@x.com"))).andExpect(status().isCreated());

        mockMvc().perform(get("/api/shares").header("Authorization", bearer(owner, "list@x.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fileId").value(f2))       // createdAt DESC
                .andExpect(jsonPath("$[0].fileName").value("b.txt"))
                .andExpect(jsonPath("$[0].token").exists())
                .andExpect(jsonPath("$[0].isActive").value(true))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[1].fileId").value(f1));
    }

    @Test
    void listMine_url_is_relative_share_path() throws Exception {
        Long owner = userRepository.save(new User("url@x.com", encoder.encode("pw"), "n")).getId();
        long fileId = upload(owner, "url@x.com", "u.txt");
        String body = mockMvc().perform(post("/api/files/" + fileId + "/share").contentType("application/json").content("{}")
                        .header("Authorization", bearer(owner, "url@x.com")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = om.readTree(body).get("token").asText();

        mockMvc().perform(get("/api/shares").header("Authorization", bearer(owner, "url@x.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].url").value("/share/" + token));
    }

    @Test
    void listMine_unauthenticated_401() throws Exception {
        mockMvc().perform(get("/api/shares"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void share_otherOwner_403() throws Exception {
        Long owner = userRepository.save(new User("o@x.com", encoder.encode("pw"), "n")).getId();
        Long intruder = userRepository.save(new User("i@x.com", encoder.encode("pw"), "n")).getId();
        long fileId = upload(owner, "o@x.com", "p.txt");
        mockMvc().perform(post("/api/files/" + fileId + "/share")
                        .contentType("application/json").content("{}")
                        .header("Authorization", bearer(intruder, "i@x.com")))
                .andExpect(status().isForbidden());
    }
}
