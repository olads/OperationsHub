package com.migia.OperationsHub.service;

import com.migia.OperationsHub.Service.JwtService;
import com.migia.OperationsHub.model.User;
import com.migia.OperationsHub.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtService}.
 * These are pure unit tests — no Spring context needed.
 * JwtService is instantiated directly with a known secret and expiry.
 */
class JwtServiceTest {

    private JwtService jwtService;

    // A 64+ byte hex string — sufficient for HMAC-SHA256
    private final String secret =
            "94a08da1fecbb6e8b46990538c7b50b2b814a7061d4b68ce80635e985b85a3c" +
            "94a08da1fecbb6e8b46990538c7b50b2b814a7061d4b68ce80635e985b85a3c";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secret, 900_000); // 15 minutes
    }

    /**
     * Test Case: Token Generation — Claims Are Correct
     * Verifies that the generated JWT contains the correct subject (email),
     * the correct organization ID claim, and passes validation.
     */
    @Test
    void generateAccessToken_containsCorrectClaims() {
        User user = new User();
        user.setEmail("test@example.com");
        UUID orgId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(user, orgId, Role.EMPLOYEE);

        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.extractEmail(token)).isEqualTo("test@example.com");
        assertThat(jwtService.extractOrganizationId(token)).isEqualTo(orgId);
    }

    /**
     * Test Case: Token Generation — Platform Admin Has Null Org
     * Verifies that a token generated for a platform admin (organization = null)
     * does not embed an orgId claim, and that the validation still passes.
     */
    @Test
    void generateAccessToken_platformAdmin_hasNullOrgId() {
        User admin = new User();
        admin.setEmail("admin@platform.com");

        // Platform admins are generated without an orgId
        String token = jwtService.generateAccessToken(admin, null, Role.PLATFORM_ADMIN);

        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.extractEmail(token)).isEqualTo("admin@platform.com");
        assertThat(jwtService.extractOrganizationId(token)).isNull();
    }

    /**
     * Test Case: Token Generation — Role Claim Is Present
     * Verifies that the role is embedded in the token as a custom claim
     * so the filter can reconstruct authorities without a DB call.
     */
    @Test
    void generateAccessToken_containsRoleClaim() {
        User user = new User();
        user.setEmail("admin@example.com");
        UUID orgId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(user, orgId, Role.ORGANIZATION_ADMIN);

        assertThat(jwtService.extractRole(token)).isEqualTo(Role.ORGANIZATION_ADMIN.name());
    }

    /**
     * Test Case: Token Validation — Tampered Token is Invalid
     * Verifies that any modification to the token (e.g., appending characters)
     * causes validateToken to return false, protecting against token forgery.
     */
    @Test
    void validateToken_returnsFalse_forTamperedToken() {
        User user = new User();
        user.setEmail("test@example.com");
        UUID orgId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(user, orgId, Role.EMPLOYEE);
        String tamperedToken = token + "TAMPERED";

        assertThat(jwtService.validateToken(tamperedToken)).isFalse();
    }

    /**
     * Test Case: Token Validation — Malformed String is Invalid
     * Verifies that completely arbitrary strings are not accepted as valid tokens.
     */
    @Test
    void validateToken_returnsFalse_forRandomString() {
        assertThat(jwtService.validateToken("not-a-jwt-at-all")).isFalse();
    }

    /**
     * Test Case: Token Expiry — Expired Token is Invalid
     * Verifies that a token created with a very short expiry (1ms) becomes
     * invalid after it expires.
     */
    @Test
    void validateToken_returnsFalse_forExpiredToken() throws InterruptedException {
        // Create a JwtService with 1ms expiry
        JwtService shortLivedJwt = new JwtService(secret, 1);

        User user = new User();
        user.setEmail("shortlived@example.com");
        UUID orgId = UUID.randomUUID();

        String token = shortLivedJwt.generateAccessToken(user, orgId, Role.EMPLOYEE);

        // Wait for it to expire
        Thread.sleep(50);

        assertThat(shortLivedJwt.validateToken(token)).isFalse();
    }
}
