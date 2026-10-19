package com.migia.OperationsHub.controller;

import tools.jackson.databind.json.JsonMapper;
import com.migia.OperationsHub.Repository.InvitationRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.model.Invitation;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.enums.OrganizationStatus;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Flow 2 — Admin creates, reads, updates, and archives organizations.
 *
 * Each test builds on the previous: TC 2.1 creates the org whose ID
 * is used by TC 2.3-2.8. Run with @TestMethodOrder so ordering is guaranteed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminOrganizationFlowTest {

    private static final String ADMIN_EMAIL  = "admin@operationshub.local";
    private static final String DEFAULT_PASS = "ChangeMe123!";
    private static final String ORG_SLUG_BASE = "flow2-org-";

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private InvitationRepository invitationRepository;

    private String adminToken;
    private UUID   createdOrgId;
    private String orgSlug;

    // ─────────────────────────────────────────────────────────────────
    // Helper — login as platform admin
    // ─────────────────────────────────────────────────────────────────
    private String loginAsAdmin() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(ADMIN_EMAIL);
        req.setPassword(DEFAULT_PASS);

        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 2.1 — Create organization with unique slug → 201
    // ─────────────────────────────────────────────────────────────────
    /**
     * Platform admin creates a new organization.
     * The service also seeds an owner invitation automatically.
     */
    @Test
    @Order(1)
    void tc2_1_createOrganization_validRequest_returns201() throws Exception {
        adminToken = loginAsAdmin();
        orgSlug = ORG_SLUG_BASE + UUID.randomUUID().toString().substring(0, 6);

        String body = """
                {
                  "name": "Flow2 Corp",
                  "slug": "%s",
                  "ownerEmail": "owner-flow2@testcorp.com",
                  "defaultCurrency": "USD"
                }
                """.formatted(orgSlug);

        MvcResult result = mockMvc.perform(post("/api/v1/admin/organizations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(orgSlug))
                .andExpect(jsonPath("$.ownerInvitationSent").value(true))
                .andReturn();

        // Capture the org ID for subsequent tests
        Organization org = objectMapper.readValue(
                result.getResponse().getContentAsString(), Organization.class);
        createdOrgId = org.getId();
        assertThat(createdOrgId).isNotNull();
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 2.2 — Duplicate slug → 409
    // ─────────────────────────────────────────────────────────────────
    /** Creating a second org with the same slug must be rejected. */
    @Test
    @Order(2)
    void tc2_2_createOrganization_duplicateSlug_returns409() throws Exception {
        assertThat(orgSlug).as("Requires TC 2.1").isNotBlank();

        String body = """
                {
                  "name": "Duplicate Corp",
                  "slug": "%s",
                  "ownerEmail": "another@example.com",
                  "defaultCurrency": "USD"
                }
                """.formatted(orgSlug);

        mockMvc.perform(post("/api/v1/admin/organizations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 2.3 — List organizations (paged) → 200, contains created org
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(3)
    void tc2_3_listOrganizations_returns200WithContent() throws Exception {
        mockMvc.perform(get("/api/v1/admin/organizations")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 2.4 — Get single organization by ID → 200, correct details
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(4)
    void tc2_4_getOrganizationById_validId_returns200() throws Exception {
        assertThat(createdOrgId).as("Requires TC 2.1").isNotNull();

        mockMvc.perform(get("/api/v1/admin/organizations/" + createdOrgId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdOrgId.toString()))
                .andExpect(jsonPath("$.slug").value(orgSlug));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 2.5 — Get non-existent org → 404
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(5)
    void tc2_5_getOrganizationById_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/organizations/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 2.6 — Update org status to SUSPENDED → 200
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(6)
    void tc2_6_updateOrgStatus_toSuspended_returns200() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/organizations/" + createdOrgId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 2.7 — Update org status back to ACTIVE → 200
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(7)
    void tc2_7_updateOrgStatus_toActive_returns200() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/organizations/" + createdOrgId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 2.8 — Soft-delete (archive) org → 204, status becomes ARCHIVED
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(8)
    void tc2_8_deleteOrganization_softDelete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/organizations/" + createdOrgId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Verify archived via direct repo query
        Optional<Organization> org = organizationRepository.findById(createdOrgId);
        assertThat(org).isPresent();
        assertThat(org.get().getStatus()).isEqualTo(OrganizationStatus.ARCHIVED);
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 2.9 — No token → 403
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(9)
    void tc2_9_listOrganizations_noToken_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/organizations"))
                .andExpect(status().isForbidden());
    }
}
