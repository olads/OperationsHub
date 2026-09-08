package com.migia.OperationsHub.service;

import com.migia.OperationsHub.Service.JwtService;
import com.migia.OperationsHub.model.User;
import com.migia.OperationsHub.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secret = "94a08da1fecbb6e8b46990538c7b50b2b814a7061d4b68ce80635e985b85a3c94a08da1fecbb6e8b46990538c7b50b2b814a7061d4b68ce80635e985b85a3c";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secret, 900000);
    }

    @Test
    void generateToken_containsCorrectClaims() {
        User user = new User();
        user.setEmail("test@example.com");
        UUID orgId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(user, orgId, Role.ORGANIZATION_OWNER);

        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.extractEmail(token)).isEqualTo("test@example.com");
        assertThat(jwtService.extractOrganizationId(token)).isEqualTo(orgId);
    }

    @Test
    void validateToken_returnsFalse_forTamperedToken() {
        User user = new User();
        user.setEmail("test@example.com");
        UUID orgId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(user, orgId, Role.ORGANIZATION_OWNER);
        String tamperedToken = token + "bad";

        assertThat(jwtService.validateToken(tamperedToken)).isFalse();
    }
}
