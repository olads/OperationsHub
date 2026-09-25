package com.migia.OperationsHub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: verifies that the entire Spring application context loads
 * successfully with the "test" profile (in-memory DB, no external dependencies).
 *
 * If this test fails it usually means:
 * - A @Bean definition has a wiring error
 * - A required configuration property is missing from application-test.yml
 * - A Flyway migration file has a syntax error
 */
@SpringBootTest
@ActiveProfiles("test")
class OperationsHubApplicationTests {

    /**
     * Test Case: Application Context Loads
     * The test passes simply by loading — no additional assertions needed.
     * Any context startup failure will cause this test to fail with a descriptive error.
     */
    @Test
    void contextLoads() {
        // Intentionally empty — context loading itself is the assertion
    }
}
