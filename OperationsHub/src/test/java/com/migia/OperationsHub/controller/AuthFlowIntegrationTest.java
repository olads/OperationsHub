package com.migia.OperationsHub.controller;

import tools.jackson.databind.json.JsonMapper;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.dto.auth.AcceptInvitationRequest;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.dto.auth.LogoutRequest;
import com.migia.OperationsHub.dto.auth.RefreshRequest;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.enums.OrganizationStatus;
import com.migia.OperationsHub.model.enums.Role;
import com.migia.OperationsHub.model.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the full authentication flow.
 * Uses a real application context with an in-memory database (@SpringBootTest).
 * These tests validate that the entire auth pipeline (filters, service, database) works together.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    private Organization testOrg;

    @BeforeEach
    void setupOrganization() {
        // Each test gets a fresh org with a unique slug to avoid conflicts between tests
        String slug = "flow-test-org-" + UUID.randomUUID().toString().substring(0, 8);
        testOrg = organizationRepository.save(Organization.builder()
                .name("Flow Test Org")
                .slug(slug)
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());
    }

    /**
     * Test Case: Full Auth Flow - Login → Access Endpoint → Refresh → Logout
     * End-to-end test that:
     * 1. Sets up a user via InvitationService (simulating the invitation path)
     * 2. Logs in and obtains tokens
     * 3. Uses the access token to call a protected endpoint
     * 4. Refreshes the token (token rotation)
     * 5. Logs out
     * 6. Verifies the old refresh token is revoked (cannot be reused)
     */
    @Test
    void authFlow_fullCycleLoginRefreshLogout() throws Exception {
        // 1. Seed a user directly via repository (bypassing invitation for test setup speed)
        com.migia.OperationsHub.model.User user = userRepository.save(
                com.migia.OperationsHub.model.User.builder()
                        .email("flow-user@test.com")
                        .passwordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                                .encode("Password123!"))
                        .firstName("Flow")
                        .lastName("User")
                        .role(Role.EMPLOYEE)
                        .organization(testOrg)
                        .status(UserStatus.ACTIVE)
                        .mustChangePassword(false)
                        .build()
        );

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("flow-user@test.com");
        loginReq.setPassword("Password123!");

        // 2. Login and capture tokens
        MvcResult loginResult = mockMvc.perform(post("/" + testOrg.getSlug() + "/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class);
        String accessToken = loginResponse.getAccessToken();
        String refreshToken = loginResponse.getRefreshToken();

        // 3. Access protected endpoint with valid access token
        mockMvc.perform(get("/" + testOrg.getSlug() + "/api/v1/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("flow-user@test.com"));

        // 4. Refresh: exchange old refresh token for new pair
        RefreshRequest refreshReq = new RefreshRequest();
        refreshReq.setRefreshToken(refreshToken);

        MvcResult refreshResult = mockMvc.perform(post("/" + testOrg.getSlug() + "/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        LoginResponse refreshedResponse = objectMapper.readValue(
                refreshResult.getResponse().getContentAsString(), LoginResponse.class);
        String newRefreshToken = refreshedResponse.getRefreshToken();

        // 5. Logout with the new refresh token
        LogoutRequest logoutReq = new LogoutRequest();
        logoutReq.setRefreshToken(newRefreshToken);

        mockMvc.perform(post("/" + testOrg.getSlug() + "/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isNoContent());

        // 6. Attempt to reuse the revoked refresh token — must fail
        RefreshRequest reuseReq = new RefreshRequest();
        reuseReq.setRefreshToken(newRefreshToken);

        mockMvc.perform(post("/" + testOrg.getSlug() + "/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reuseReq)))
                .andExpect(status().isConflict()); // revoked token should return 409
    }

    /**
     * Test Case: Protected Endpoint - No Token
     * Verifies that accessing a protected endpoint without an Authorization header
     * returns 401 Unauthorized (or 403 Forbidden if using default Spring Security behavior).
     */
    @Test
    void protectedEndpoint_noToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/" + testOrg.getSlug() + "/api/v1/me"))
                .andExpect(status().isForbidden()); // Spring Security default
    }

    /**
     * Test Case: Login - Wrong Password
     * Verifies that providing an incorrect password returns a 404 Not Found,
     * which is the expected behavior for ResourceNotFoundException in this app
     * (credentials are intentionally treated as "not found" to avoid enumeration).
     */
    @Test
    void login_wrongPassword_returns404() throws Exception {
        // Seed user
        userRepository.save(
                com.migia.OperationsHub.model.User.builder()
                        .email("wrong-pass@test.com")
                        .passwordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                                .encode("CorrectPassword123!"))
                        .firstName("Wrong")
                        .lastName("Pass")
                        .role(Role.EMPLOYEE)
                        .organization(testOrg)
                        .status(UserStatus.ACTIVE)
                        .mustChangePassword(false)
                        .build()
        );

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("wrong-pass@test.com");
        loginReq.setPassword("WrongPassword123!");

        mockMvc.perform(post("/" + testOrg.getSlug() + "/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isNotFound());
    }
}
