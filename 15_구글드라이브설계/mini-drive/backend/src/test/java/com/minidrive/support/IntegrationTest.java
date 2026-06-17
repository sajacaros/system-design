package com.minidrive.support;

import com.minidrive.auth.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.web.FilterChainProxy;

import static org.springframework.test.web.servlet.setup.SharedHttpSessionConfigurer.sharedHttpSession;

/**
 * Base for web-layer integration tests. Boots the full Spring context with an in-memory
 * H2 database and a fake StorageService (no MinIO required).
 */
@SpringBootTest
@Import(TestStorageConfig.class)
public abstract class IntegrationTest {

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected FilterChainProxy springSecurityFilterChain;

    protected MockMvc mvc;

    protected MockMvc mockMvc() {
        if (mvc == null) {
            mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(springSecurityFilterChain)
                    .apply(sharedHttpSession())
                    .build();
        }
        return mvc;
    }

    protected String bearer(Long userId, String email) {
        return "Bearer " + jwtService.generateAccessToken(userId, email);
    }

    /** A deliberately expired access token (negative TTL not supported -> craft manually). */
    protected String expiredBearer() {
        return "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidHlwIjoiYWNjZXNzIiwiZXhwIjoxMDAwMDAwMDB9.invalidsignature";
    }
}
