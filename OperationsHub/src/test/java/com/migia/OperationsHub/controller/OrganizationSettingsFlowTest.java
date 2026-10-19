package com.migia.OperationsHub.controller;

import tools.jackson.databind.json.JsonMapper;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.dto.organization.UpdateOrganizationSettingsRequest;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flow 4 — Organization Settings & Profile.
 *
 * Verifies:
 *  - 4.1 Get settings with Owner token → 200 OK
 *  - 4.2 Update settings (all fields) → 200 OK, updated fields reflected
 *  - 4.3 Partial update (only currency) → 200 OK
 *  - 4.4 Regular employee cannot update settings → 403 Forbidden
 *  - 4.5 Unauthorized access (no token) → 401/403
 *  - 4.6 Cross-tenant access (Org B owner accessing Org A settings) → 403 Forbidden
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrganizationSettingsFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;

    private Organization orgA;
    private Organization orgB;
    private String ownerTokenA;
    private String ownerTokenB;
    private String employeeTokenA;

    @BeforeAll
    void setup() throws Exception {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();

        // Org A
        orgA = organizationRepository.save(Organization.builder()
                .name("Acme Corp")
                .slug("acme-" + UUID.randomUUID().toString().substring(0, 6))
                .defaultCurrency("USD")
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());

        // Org B
        orgB = organizationRepository.save(Organization.builder()
                .name("Beta Industries")
                .slug("beta-" + UUID.randomUUID().toString().substring(0, 6))
                .defaultCurrency("EUR")
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());

        // Org A Owner
        userRepository.save(User.builder()
                .email("owner@acme.com")
                .passwordHash(enc.encode("OwnerPass1!"))
                .firstName("Alice")
                .lastName("Acme")
                .role(Role.ORGANIZATION_OWNER)
                .organization(orgA)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

        // Org A Employee
        userRepository.save(User.builder()
                .email("employee@acme.com")
                .passwordHash(enc.encode("EmpPass1!"))
                .firstName("Bob")
                .lastName("Worker")
                .role(Role.EMPLOYEE)
                .organization(orgA)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

        // Org B Owner
        userRepository.save(User.builder()
                .email("owner@beta.com")
                .passwordHash(enc.encode("BetaPass1!"))
                .firstName("Charlie")
                .lastName("Beta")
                .role(Role.ORGANIZATION_OWNER)
                .organization(orgB)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

        ownerTokenA = loginAs("owner@acme.com", "OwnerPass1!", orgA.getSlug());
        employeeTokenA = loginAs("employee@acme.com", "EmpPass1!", orgA.getSlug());
        ownerTokenB = loginAs("owner@beta.com", "BetaPass1!", orgB.getSlug());
    }

    private String loginAs(String email, String pass, String slug) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(pass);

        MvcResult r = mockMvc.perform(patch("/" + slug + "/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        // In case auth is POST:
        if (r.getResponse().getStatus() != 200) {
            r = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/" + slug + "/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andReturn();
        }

        return objectMapper.readValue(r.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();
    }

    @Test
    @Order(1)
    void tc4_1_getOrganizationSettings_returns200() throws Exception {
        mockMvc.perform(get("/" + orgA.getSlug() + "/api/v1/organization")
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.defaultCurrency").value("USD"));
    }

    @Test
    @Order(2)
    void tc4_2_updateOrganizationSettings_returns200() throws Exception {
        UpdateOrganizationSettingsRequest req = new UpdateOrganizationSettingsRequest();
        req.setDefaultCurrency("GBP");
        req.setContactEmail("contact@acme.com");
        req.setContactPhone("+441234567890");
        req.setWebsite("https://acme.example.com");
        req.setLogoUrl("https://acme.example.com/logo.png");

        mockMvc.perform(patch("/" + orgA.getSlug() + "/api/v1/organization/settings")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultCurrency").value("GBP"))
                .andExpect(jsonPath("$.contactEmail").value("contact@acme.com"))
                .andExpect(jsonPath("$.contactPhone").value("+441234567890"))
                .andExpect(jsonPath("$.website").value("https://acme.example.com"))
                .andExpect(jsonPath("$.logoUrl").value("https://acme.example.com/logo.png"));

        // Verify persisted state in DB
        Organization refreshed = organizationRepository.findById(orgA.getId()).orElseThrow();
        assertThat(refreshed.getDefaultCurrency()).isEqualTo("GBP");
        assertThat(refreshed.getContactEmail()).isEqualTo("contact@acme.com");
    }

    @Test
    @Order(3)
    void tc4_3_partialUpdateSettings_returns200() throws Exception {
        UpdateOrganizationSettingsRequest req = new UpdateOrganizationSettingsRequest();
        req.setDefaultCurrency("EUR");

        mockMvc.perform(patch("/" + orgA.getSlug() + "/api/v1/organization/settings")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultCurrency").value("EUR"))
                // Existing contactEmail remains preserved
                .andExpect(jsonPath("$.contactEmail").value("contact@acme.com"));
    }

    @Test
    @Order(4)
    void tc4_4_employeeCannotUpdateSettings_returns403() throws Exception {
        UpdateOrganizationSettingsRequest req = new UpdateOrganizationSettingsRequest();
        req.setDefaultCurrency("CAD");

        mockMvc.perform(patch("/" + orgA.getSlug() + "/api/v1/organization/settings")
                        .header("Authorization", "Bearer " + employeeTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    void tc4_5_unauthenticatedAccess_returns401Or403() throws Exception {
        mockMvc.perform(get("/" + orgA.getSlug() + "/api/v1/organization"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    void tc4_6_crossTenantAccess_returns403() throws Exception {
        // Owner B hits Org A settings
        mockMvc.perform(get("/" + orgA.getSlug() + "/api/v1/organization")
                        .header("Authorization", "Bearer " + ownerTokenB))
                .andExpect(status().isForbidden());
    }
}
