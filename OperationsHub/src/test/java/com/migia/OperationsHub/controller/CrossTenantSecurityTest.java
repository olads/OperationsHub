package com.migia.OperationsHub.controller;

import tools.jackson.databind.json.JsonMapper;
import com.migia.OperationsHub.Repository.InvitationRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.dto.auth.AcceptInvitationRequest;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.model.Invitation;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.User;
import com.migia.OperationsHub.model.enums.OrganizationStatus;
import com.migia.OperationsHub.model.enums.Role;
import com.migia.OperationsHub.model.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for cross-tenant security enforcement.
 * Verifies that the JWT-org-binding mechanism prevents users from one
 * organization accessing resources of another organization.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrossTenantSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private InvitationRepository invitationRepository;

    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private Organization orgA;
    private Organization orgB;
    private String tokenOrgA;

    @BeforeEach
    void setup() throws Exception {
        // Create two separate organizations
        orgA = organizationRepository.save(Organization.builder()
                .name("Org A")
                .slug("cross-org-a-" + UUID.randomUUID().toString().substring(0, 6))
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());

        orgB = organizationRepository.save(Organization.builder()
                .name("Org B")
                .slug("cross-org-b-" + UUID.randomUUID().toString().substring(0, 6))
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());

        // Seed an owner for Org A
        userRepository.save(User.builder()
                .email("owner-a@test.com")
                .passwordHash(encoder.encode("Password123!"))
                .firstName("Owner")
                .lastName("A")
                .role(Role.ORGANIZATION_OWNER)
                .organization(orgA)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

        // Log in as Org A owner and capture the token
        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("owner-a@test.com");
        loginReq.setPassword("Password123!");

        MvcResult result = mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), LoginResponse.class);
        tokenOrgA = loginResponse.getAccessToken();
    }

    /**
     * Test Case: Cross-Tenant Token Rejection
     * Verifies that a JWT issued for Org A is rejected when used to access
     * resources under Org B's URL namespace. The JwtAuthenticationFilter
     * compares the token's org claim against the URL-resolved org ID.
     */
    @Test
    void crossTenant_orgAToken_rejectedOnOrgBEndpoint() throws Exception {
        // Use Org A's token to try to access Org B's /me endpoint
        mockMvc.perform(get("/" + orgB.getSlug() + "/api/v1/me")
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isForbidden()); // Token's orgId != URL's orgId
    }

    /**
     * Test Case: Same-Tenant Token Accepted
     * Verifies that a JWT issued for Org A successfully works on Org A's endpoints.
     * This is a positive test to confirm the cross-tenant rejection is selective,
     * not a blanket block.
     */
    @Test
    void sameTenant_orgAToken_acceptedOnOrgAEndpoint() throws Exception {
        mockMvc.perform(get("/" + orgA.getSlug() + "/api/v1/me")
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner-a@test.com"));
    }

    /**
     * Test Case: Invitation Accept - Valid Flow Across New User
     * Verifies that an employee can accept an invitation for Org A,
     * receive a fresh set of credentials, and successfully log in.
     * Credentials are completely separate from any other org.
     */
    @Test
    void invitationAccept_createsIsolatedCredentials_canLogin() throws Exception {
        // Seed an invitation for a new employee in Org A
        Invitation invitation = invitationRepository.save(Invitation.builder()
                .email("new-employee@test.com")
                .organization(orgA)
                .role(Role.EMPLOYEE)
                .token(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusSeconds(7200))
                .accepted(false)
                .build());

        // Accept the invitation
        AcceptInvitationRequest acceptReq = new AcceptInvitationRequest();
        acceptReq.setToken(invitation.getToken());
        acceptReq.setPassword("Employee123!");
        acceptReq.setFirstName("New");
        acceptReq.setLastName("Employee");

        mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new-employee@test.com"))
                .andExpect(jsonPath("$.organizationSlug").value(orgA.getSlug()));

        // The employee can now log into Org A
        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("new-employee@test.com");
        loginReq.setPassword("Employee123!");

        mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // The same email + password does NOT work in Org B (credential isolation)
        mockMvc.perform(post("/" + orgB.getSlug() + "/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isNotFound()); // No user record exists in Org B
    }
}
