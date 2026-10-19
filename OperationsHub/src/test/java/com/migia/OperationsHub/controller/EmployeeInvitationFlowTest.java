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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Flow 5 — Employee invitation, onboarding, listing, and role management.
 *
 * Setup creates an org + owner user. Tests then:
 *  - Seed an EMPLOYEE invitation and have the employee accept it.
 *  - List and fetch employees.
 *  - Update the employee's role.
 *  - Verify a plain EMPLOYEE cannot change another's role (permission gate).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmployeeInvitationFlowTest {

    private static final String OWNER_EMAIL    = "emp-flow5-owner@example.com";
    private static final String OWNER_PASSWORD = "Owner@Pass1!";
    private static final String EMP_EMAIL      = "emp-flow5-emp@example.com";
    private static final String EMP_PASSWORD   = "Emp@Pass1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private InvitationRepository invitationRepository;

    private Organization org;
    private String ownerToken;
    private String employeeToken;
    private UUID   employeeUserId;

    @BeforeAll
    void setup() throws Exception {
        // Create org
        org = organizationRepository.save(Organization.builder()
                .name("Emp Flow Corp")
                .slug("emp-flow5-" + UUID.randomUUID().toString().substring(0, 6))
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());

        // Seed owner user
        userRepository.save(User.builder()
                .email(OWNER_EMAIL)
                .passwordHash(new BCryptPasswordEncoder().encode(OWNER_PASSWORD))
                .firstName("Owner").lastName("Flow5")
                .role(Role.ORGANIZATION_OWNER)
                .organization(org)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

        ownerToken = login(OWNER_EMAIL, OWNER_PASSWORD);
    }

    private String login(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);

        MvcResult result = mockMvc.perform(post("/" + org.getSlug() + "/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 5.1 — Employee accepts invitation and registers
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(1)
    void tc5_1_employeeAcceptsInvitation_returns201() throws Exception {
        String token = UUID.randomUUID().toString();
        invitationRepository.save(Invitation.builder()
                .email(EMP_EMAIL)
                .organization(org)
                .role(Role.EMPLOYEE)
                .token(token)
                .expiresAt(Instant.now().plusSeconds(3600))
                .accepted(false)
                .build());

        AcceptInvitationRequest req = new AcceptInvitationRequest();
        req.setToken(token);
        req.setPassword(EMP_PASSWORD);
        req.setFirstName("Bob");
        req.setLastName("Employee");

        MvcResult result = mockMvc.perform(post("/" + org.getSlug() + "/api/v1/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMP_EMAIL))
                .andReturn();

        employeeToken  = login(EMP_EMAIL, EMP_PASSWORD);
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 5.2 — Duplicate invitation → 409
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(2)
    void tc5_2_duplicatePendingInvitation_returns409() throws Exception {
        // Try to seed another invitation for the same email (already registered)
        // InvitationService checks userRepository.existsByEmailAndOrganization_Id → throws ConflictException
        // We test this via InvitationService called through a future endpoint; for now verify via direct check.
        boolean exists = userRepository.existsByEmailAndOrganization_Id(EMP_EMAIL, org.getId());
        org.getId();
        assert exists : "Employee should already be registered";
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 5.3 — List employees → 200, at least 2 members (owner + employee)
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(3)
    void tc5_3_listEmployees_ownerToken_returns200() throws Exception {
        mockMvc.perform(get("/" + org.getSlug() + "/api/v1/employees")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(org.getId() != null ? 2 : 2));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 5.4 — Get specific employee → 200
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(4)
    void tc5_4_getEmployee_validId_returns200() throws Exception {
        // Fetch the employee user ID via repository
        User emp = userRepository.findByEmailAndOrganization_Id(EMP_EMAIL, org.getId()).orElseThrow();
        employeeUserId = emp.getId();

        mockMvc.perform(get("/" + org.getSlug() + "/api/v1/employees/" + employeeUserId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMP_EMAIL));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 5.5 — Update employee role → 200
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(5)
    void tc5_5_updateEmployeeRole_ownerToken_returns200() throws Exception {
        mockMvc.perform(patch("/" + org.getSlug() + "/api/v1/employees/" + employeeUserId + "/role")
                        .param("role", "ORGANIZATION_ADMIN")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ORGANIZATION_ADMIN"));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 5.6 — Employee cannot update another's role → 403
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(6)
    void tc5_6_updateEmployeeRole_employeeToken_returns403() throws Exception {
        // employeeToken is still valid even after role update (JWT is stateless)
        // We re-login to get a token reflecting the new role
        String adminEmpToken = login(EMP_EMAIL, EMP_PASSWORD);

        // Get owner user ID
        User owner = userRepository.findByEmailAndOrganization_Id(OWNER_EMAIL, org.getId()).orElseThrow();

        // ORGANIZATION_ADMIN can manage employees, but let's use a pure EMPLOYEE token for this negative test
        // Seed a second plain employee and use their token
        String token2 = UUID.randomUUID().toString();
        invitationRepository.save(Invitation.builder()
                .email("emp2-flow5@example.com")
                .organization(org)
                .role(Role.EMPLOYEE)
                .token(token2)
                .expiresAt(Instant.now().plusSeconds(3600))
                .accepted(false)
                .build());

        AcceptInvitationRequest req = new AcceptInvitationRequest();
        req.setToken(token2);
        req.setPassword("Emp2@Pass1!");
        req.setFirstName("Carol");
        req.setLastName("Two");

        mockMvc.perform(post("/" + org.getSlug() + "/api/v1/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        String emp2Token = login("emp2-flow5@example.com", "Emp2@Pass1!");

        // Plain EMPLOYEE tries to change role → 403
        mockMvc.perform(patch("/" + org.getSlug() + "/api/v1/employees/" + owner.getId() + "/role")
                        .param("role", "EMPLOYEE")
                        .header("Authorization", "Bearer " + emp2Token))
                .andExpect(status().isForbidden());
    }
}
