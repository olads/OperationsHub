package com.migia.OperationsHub.controller;

import tools.jackson.databind.json.JsonMapper;
import com.migia.OperationsHub.Repository.InvitationRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.dto.auth.AcceptInvitationRequest;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.dto.auth.LogoutRequest;
import com.migia.OperationsHub.dto.auth.RefreshRequest;
import com.migia.OperationsHub.dto.auth.RegisterResponse;
import com.migia.OperationsHub.model.Invitation;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.enums.OrganizationStatus;
import com.migia.OperationsHub.model.enums.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flow 3 — Owner invitation lookup → accept → register → login → refresh → logout.
 *
 * The organization and invitation are seeded directly via repositories
 * (faster than calling admin API) so this flow is self-contained.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OwnerRegistrationFlowTest {

    private static final String OWNER_EMAIL    = "owner-flow3@example.com";
    private static final String OWNER_PASSWORD = "Owner@Pass1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private InvitationRepository invitationRepository;
    @Autowired private UserRepository userRepository;

    private Organization org;
    private String invitationToken;
    private String ownerAccessToken;
    private String ownerRefreshToken;

    @BeforeAll
    void setupOrgAndInvitation() {
        org = organizationRepository.save(Organization.builder()
                .name("Flow3 Corp")
                .slug("flow3-org-" + UUID.randomUUID().toString().substring(0, 6))
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());

        invitationToken = UUID.randomUUID().toString();
        invitationRepository.save(Invitation.builder()
                .email(OWNER_EMAIL)
                .organization(org)
                .role(Role.ORGANIZATION_OWNER)
                .token(invitationToken)
                .expiresAt(Instant.now().plusSeconds(3600))
                .accepted(false)
                .build());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 3.1 — Fetch invitation details by token (public) → 200
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(1)
    void tc3_1_getInvitationByToken_valid_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/invitations/" + invitationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(OWNER_EMAIL))
                .andExpect(jsonPath("$.role").value("ORGANIZATION_OWNER"))
                .andExpect(jsonPath("$.organizationSlug").value(org.getSlug()));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 3.2 — Fetch with invalid token → 404
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(2)
    void tc3_2_getInvitationByToken_invalid_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/invitations/totally-invalid-token"))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 3.3 — Accept invitation and register as owner → 201
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(3)
    void tc3_3_acceptInvitation_valid_returns201() throws Exception {
        AcceptInvitationRequest req = new AcceptInvitationRequest();
        req.setToken(invitationToken);
        req.setPassword(OWNER_PASSWORD);
        req.setFirstName("Alice");
        req.setLastName("Owner");

        MvcResult result = mockMvc.perform(post("/" + org.getSlug() + "/api/v1/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(OWNER_EMAIL))
                .andExpect(jsonPath("$.organizationSlug").value(org.getSlug()))
                .andReturn();

        RegisterResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), RegisterResponse.class);
        assertThat(response.getUserId()).isNotNull();
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 3.4 — Re-accepting same token → 409 (already accepted)
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(4)
    void tc3_4_acceptInvitation_alreadyAccepted_returns409() throws Exception {
        AcceptInvitationRequest req = new AcceptInvitationRequest();
        req.setToken(invitationToken);
        req.setPassword(OWNER_PASSWORD);
        req.setFirstName("Alice");
        req.setLastName("Owner");

        mockMvc.perform(post("/" + org.getSlug() + "/api/v1/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 3.5 — Owner logs in for the first time → 200, tokens issued
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(5)
    void tc3_5_ownerLogin_validCredentials_returns200WithTokens() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(OWNER_EMAIL);
        req.setPassword(OWNER_PASSWORD);

        MvcResult result = mockMvc.perform(post("/" + org.getSlug() + "/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        LoginResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), LoginResponse.class);
        ownerAccessToken  = response.getAccessToken();
        ownerRefreshToken = response.getRefreshToken();
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 3.6 — Login with wrong org slug → 404
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(6)
    void tc3_6_ownerLogin_wrongOrgSlug_returns404() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(OWNER_EMAIL);
        req.setPassword(OWNER_PASSWORD);

        mockMvc.perform(post("/non-existent-org/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 3.7 — Refresh token → new token pair
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(7)
    void tc3_7_refreshToken_valid_returnsNewTokenPair() throws Exception {
        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken(ownerRefreshToken);

        MvcResult result = mockMvc.perform(post("/" + org.getSlug() + "/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        // Rotate — use new refresh token for logout
        LoginResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), LoginResponse.class);
        ownerRefreshToken = response.getRefreshToken();
        ownerAccessToken  = response.getAccessToken();
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 3.8 — Logout → 204
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(8)
    void tc3_8_logout_valid_returns204() throws Exception {
        LogoutRequest req = new LogoutRequest();
        req.setRefreshToken(ownerRefreshToken);

        mockMvc.perform(post("/" + org.getSlug() + "/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 3.9 — Refresh with revoked token → 409
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(9)
    void tc3_9_refreshToken_revoked_returns409() throws Exception {
        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken(ownerRefreshToken); // already revoked in TC 3.8

        mockMvc.perform(post("/" + org.getSlug() + "/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 3.10 — Get /me with valid access token → 200, ORGANIZATION_OWNER
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(10)
    void tc3_10_getMe_validToken_returnsOwnerProfile() throws Exception {
        // Re-login to get a fresh token after logout
        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail(OWNER_EMAIL);
        loginReq.setPassword(OWNER_PASSWORD);

        MvcResult loginResult = mockMvc.perform(post("/" + org.getSlug() + "/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String freshToken = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();

        mockMvc.perform(get("/" + org.getSlug() + "/api/v1/me")
                        .header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(OWNER_EMAIL))
                .andExpect(jsonPath("$.role").value("ORGANIZATION_OWNER"));
    }
}
