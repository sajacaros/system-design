package com.minidrive;

import com.minidrive.support.IntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Verifies the full application context (including WebSocket/STOMP config and
 * notification beans) starts with H2 + fake storage.
 */
class ContextLoadTest extends IntegrationTest {

    @Test
    void contextLoads() {
        // context boot is the assertion
    }
}
