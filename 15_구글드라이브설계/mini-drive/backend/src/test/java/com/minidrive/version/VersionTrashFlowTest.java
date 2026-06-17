package com.minidrive.version;

import com.minidrive.support.IntegrationTest;
import com.minidrive.user.User;
import com.minidrive.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VersionTrashFlowTest extends IntegrationTest {

    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder encoder;
    private final ObjectMapper om = new ObjectMapper();

    private Long uid;
    private String auth;

    private long upload(String name, String content) throws Exception {
        String body = mockMvc().perform(multipart("/api/files/upload")
                        .file(new MockMultipartFile("file", name, "text/plain", content.getBytes()))
                        .header("Authorization", auth))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("id").asLong();
    }

    private long uploadInFolder(String name, String content, long folderId) throws Exception {
        String body = mockMvc().perform(multipart("/api/files/upload")
                        .file(new MockMultipartFile("file", name, "text/plain", content.getBytes()))
                        .param("folderId", String.valueOf(folderId))
                        .header("Authorization", auth))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("id").asLong();
    }

    private long createFolder(String name) throws Exception {
        String body = mockMvc().perform(post("/api/folders").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", auth)
                        .content("{\"parentId\":null,\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("id").asLong();
    }

    private void setup(String email) {
        uid = userRepository.save(new User(email, encoder.encode("pw"), "n")).getId();
        auth = bearer(uid, email);
    }

    @Test
    void versions_history_and_restore() throws Exception {
        setup("vt-versions@x.com");
        long id = upload("a.txt", "v1");
        // create version 2
        mockMvc().perform(multipart("/api/files/" + id + "/content")
                        .file(new MockMultipartFile("file", "a.txt", "text/plain", "v2".getBytes()))
                        .param("baseVersion", "1").header("Authorization", auth))
                .andExpect(status().isOk());
        // history has 2 versions
        mockMvc().perform(get("/api/files/" + id + "/versions").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[1].version").value(1));
        // restore version 1 -> becomes version 3
        mockMvc().perform(post("/api/files/" + id + "/restore")
                        .contentType("application/json").content("{\"version\":1}")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3));
    }

    // v1.6: version download is now a gateway download (X-Accel-Redirect), not a presigned URL.
    @Test
    void versionDownload_isGateway_xAccelRedirect_and_authz() throws Exception {
        setup("vt-vdl@x.com");
        long id = upload("a.txt", "v1");

        // owner: empty-body 200 + X-Accel-Redirect (internal) + UTF-8 Content-Type + disposition.
        mockMvc().perform(get("/api/files/" + id + "/versions/1/download").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-Accel-Redirect",
                                org.hamcrest.Matchers.startsWith("/_minio/")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", "text/plain; charset=UTF-8"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Disposition", "attachment; filename*=UTF-8''a.txt"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(""));

        // unknown version -> 404
        mockMvc().perform(get("/api/files/" + id + "/versions/99/download").header("Authorization", auth))
                .andExpect(status().isNotFound());

        // non-owner -> 403
        Long intruder = userRepository.save(new User("vt-vdl-intruder@x.com", encoder.encode("pw"), "n")).getId();
        mockMvc().perform(get("/api/files/" + id + "/versions/1/download")
                        .header("Authorization", bearer(intruder, "vt-vdl-intruder@x.com")))
                .andExpect(status().isForbidden());

        // unauthenticated -> 401
        mockMvc().perform(get("/api/files/" + id + "/versions/1/download"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trash_restore_and_permanentDelete_rules() throws Exception {
        setup("vt-trash@x.com");
        long id = upload("b.txt", "data");
        // soft delete -> trash
        mockMvc().perform(delete("/api/files/" + id).header("Authorization", auth))
                .andExpect(status().isNoContent());
        // permanent delete works on trashed
        long id2 = upload("c.txt", "data");
        // c is not in trash -> permanent delete 409
        mockMvc().perform(delete("/api/files/" + id2 + "/permanent").header("Authorization", auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        // restore trashed file from trash
        mockMvc().perform(post("/api/files/" + id + "/restore").header("Authorization", auth))
                .andExpect(status().isOk());
    }

    // A6: global trash view spans all folders when folderId is unspecified.
    @Test
    void trash_globalView_spansAllFolders() throws Exception {
        setup("vt-globaltrash@x.com");
        long folderId = createFolder("Docs");
        long rootFile = upload("root.txt", "r");
        long folderFile = uploadInFolder("infolder.txt", "f", folderId);
        // delete both -> trash
        mockMvc().perform(delete("/api/files/" + rootFile).header("Authorization", auth))
                .andExpect(status().isNoContent());
        mockMvc().perform(delete("/api/files/" + folderFile).header("Authorization", auth))
                .andExpect(status().isNoContent());

        // status=DELETED without folderId -> both files (root + in-folder) returned
        mockMvc().perform(get("/api/files").param("status", "DELETED").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // status=DELETED with explicit folderId -> only that folder's deleted file
        mockMvc().perform(get("/api/files").param("status", "DELETED")
                        .param("folderId", String.valueOf(folderId)).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(folderFile));
    }

    // A7: folderId literal "null" must be treated as root (no 500 / no leaked message).
    @Test
    void list_folderIdLiteralNull_isHandledSafely() throws Exception {
        setup("vt-nullfolder@x.com");
        long folderId = createFolder("Docs");
        upload("root.txt", "r");
        uploadInFolder("infolder.txt", "f", folderId);

        // folderId=null + status=DELETED -> 200 (global trash view, currently empty)
        mockMvc().perform(get("/api/files").param("folderId", "null").param("status", "DELETED")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // folderId=null default UPLOADED -> root files only (1)
        mockMvc().perform(get("/api/files").param("folderId", "null").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].originalName").value("root.txt"));

        // invalid non-numeric folderId -> 400 VALIDATION, never 500
        mockMvc().perform(get("/api/files").param("folderId", "abc").header("Authorization", auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }
}
