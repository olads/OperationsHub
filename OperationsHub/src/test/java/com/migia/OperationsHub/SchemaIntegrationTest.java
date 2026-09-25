package com.migia.OperationsHub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test verifying that the full database schema initializes correctly.
 *
 * Specifically validates:
 * - All Flyway migrations (V1 through V5) run without error on the test database
 * - Hibernate entity mappings are consistent with the schema
 *   (column names, types, constraints all match the entity annotations)
 * - No "schema mismatch" or "column not found" errors at startup
 *
 * This catches common issues like:
 * - A new entity field added in Java but missing from the Flyway migration SQL
 * - A Flyway migration that adds a NOT NULL column without a default, breaking existing data
 * - Enum type mismatch between @Enumerated and the DB column type
 */
@SpringBootTest
@ActiveProfiles("test")
class SchemaIntegrationTest {

    /**
     * Test Case: Schema and Migrations Are Valid
     * Passes if the Spring context (including Flyway + Hibernate validation) starts cleanly.
     */
    @Test
    void flywayMigrationsAndHibernateMappingsAreConsistent() {
        // If context loads without exception, Flyway ran all migrations and
        // Hibernate validated all entity → schema mappings successfully.
        assertTrue(true, "Context loaded — schema is valid");
    }
}
