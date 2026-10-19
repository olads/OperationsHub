package com.migia.OperationsHub.controller;

import tools.jackson.databind.json.JsonMapper;
import com.migia.OperationsHub.dto.auth.ChangePasswordRequest;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flow 1 — Platform Admin Bootstrap & Auth
 *
 * Tests the lifecycle of the platform admin account that is auto-seeded
 * by {@link com.migia.OperationsHub.Security.PlatformAdminInitializer} on startup.
 *
 * Execution order is important: TC 1.1 captures the token used by TC 1.3.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlatformAdminFlowTest {

    private static final String ADMIN_EMAIL    = "admin@operationshub.local";
    private static final String DEFAULT_PASS   = "ChangeMe123!";
    private static final String NEW_PASS       = "Secure@NewPass1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;

    /** Shared between ordered tests */
    private String adminToken;

    // ─────────────────────────────────────────────────────────────────
    // TC 1.1 — Login with default credentials (mustChangePassword=true)
    // ─────────────────────────────────────────────────────────────────
    /**
     * Verifies the auto-seeded admin account exists and issues a JWT.
     * The response must carry mustChangePassword=true so the UI can force a reset.
     */
    @Test
    @Order(1)
    void tc1_1_adminLogin_defaultCredentials_returns200AndMustChangePassword() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(ADMIN_EMAIL);
        req.setPassword(DEFAULT_PASS);

        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn();

        LoginResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), LoginResponse.class);
        adminToken = response.getAccessToken();
        assertThat(adminToken).isNotBlank();
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 1.2 — Login with wrong password → 404 (credential enumeration guard)
    // ─────────────────────────────────────────────────────────────────
    /**
     * Verifies wrong credentials are rejected. The app returns 404 instead of
     * 401 to avoid confirming whether the email exists (enumeration protection).
     */
    @Test
    @Order(2)
    void tc1_2_adminLogin_wrongPassword_returns404() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(ADMIN_EMAIL);
        req.setPassword("WrongPassword!");

        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 1.3 — Change password with valid old password → 204
    // ─────────────────────────────────────────────────────────────────
    /**
     * Verifies the admin can change their password after first login.
     * After this, mustChangePassword should be false.
     */
    @Test
    @Order(3)
    void tc1_3_changePassword_valid_returns204() throws Exception {
        // Ensure token is available (TC 1.1 must run first)
        assertThat(adminToken).as("adminToken must be set by TC 1.1").isNotBlank();

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword(DEFAULT_PASS);
        req.setNewPassword(NEW_PASS);

        mockMvc.perform(post("/api/v1/admin/auth/change-password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 1.4 — Re-login with new password → mustChangePassword=false
    // ─────────────────────────────────────────────────────────────────
    /**
     * After changing the password, login with the new credentials.
     * mustChangePassword must now be false to confirm the flag was cleared.
     */
    @Test
    @Order(4)
    void tc1_4_adminLogin_newPassword_mustChangePasswordFalse() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(ADMIN_EMAIL);
        req.setPassword(NEW_PASS);

        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 1.5 — Change password with wrong old password → 400
    // ─────────────────────────────────────────────────────────────────
    /**
     * Verifies that providing the wrong old password when trying to change it
     * returns 400 Bad Request.
     */
    @Test
    @Order(5)
    void tc1_5_changePassword_wrongOldPassword_returns400() throws Exception {
        // Log in again with new password to get a fresh token
        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail(ADMIN_EMAIL);
        loginReq.setPassword(NEW_PASS);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String freshToken = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();

        ChangePasswordRequest changeReq = new ChangePasswordRequest();
        changeReq.setOldPassword("WrongOldPassword!");
        changeReq.setNewPassword("SomeNewPass@1!");

        mockMvc.perform(post("/api/v1/admin/auth/change-password")
                        .header("Authorization", "Bearer " + freshToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isBadRequest());
    }
}
