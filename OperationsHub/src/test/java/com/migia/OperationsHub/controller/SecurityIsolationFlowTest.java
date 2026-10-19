package com.migia.OperationsHub.controller;

import tools.jackson.databind.json.JsonMapper;
import com.migia.OperationsHub.Repository.CustomerRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.ProductRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.Service.JwtService;
import com.migia.OperationsHub.dto.WebhookPayload;
import com.migia.OperationsHub.dto.auth.CustomerRegisterRequest;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.dto.product.CreateProductRequest;
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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flow 15 — Security & Cross-Tenant Isolation.
 *
 * Verifies:
 *  - 15.1 Org A token cannot view Org B products → 403 Forbidden
 *  - 15.2 Org A token cannot access Org B employees → 403 Forbidden
 *  - 15.3 Org A token cannot access Org B customers → 403 Forbidden
 *  - 15.4 Request without any token to protected endpoint → 401/403
 *  - 15.5 Malformed token returns 401/403
 *  - 15.6 Customer role cannot create products → 403 Forbidden
 *  - 15.7 Customer role cannot view employees → 403 Forbidden
 *  - 15.8 Public registration accessible without token → 201 Created
 *  - 15.9 Public payment webhook accessible without token → not 401/403
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityIsolationFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private JwtService jwtService;

    private Organization orgA;
    private Organization orgB;
    private String tokenOrgA;
    private String tokenOrgB;
    private String customerTokenA;

    @BeforeAll
    void setup() throws Exception {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();

        orgA = organizationRepository.save(Organization.builder()
                .name("Sec Org A")
                .slug("sec-org-a-" + UUID.randomUUID().toString().substring(0, 6))
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());

        orgB = organizationRepository.save(Organization.builder()
                .name("Sec Org B")
                .slug("sec-org-b-" + UUID.randomUUID().toString().substring(0, 6))
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());

        userRepository.save(User.builder()
                .email("owner-a@sec.com")
                .passwordHash(enc.encode("Pass@1!"))
                .firstName("OwnerA").lastName("Sec")
                .role(Role.ORGANIZATION_OWNER)
                .organization(orgA)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

        userRepository.save(User.builder()
                .email("owner-b@sec.com")
                .passwordHash(enc.encode("Pass@1!"))
                .firstName("OwnerB").lastName("Sec")
                .role(Role.ORGANIZATION_OWNER)
                .organization(orgB)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

        tokenOrgA = loginAs("owner-a@sec.com", "Pass@1!", orgA.getSlug());
        tokenOrgB = loginAs("owner-b@sec.com", "Pass@1!", orgB.getSlug());

        // Register and login a customer under Org A
        CustomerRegisterRequest custReq = new CustomerRegisterRequest();
        custReq.setEmail("sec-cust@flow.com");
        custReq.setPassword("CustPass@1!");
        custReq.setFirstName("Sec");
        custReq.setLastName("Cust");

        mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/customer/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReq)))
                .andExpect(status().isCreated());

        LoginRequest custLogin = new LoginRequest();
        custLogin.setEmail("sec-cust@flow.com");
        custLogin.setPassword("CustPass@1!");

        MvcResult custLoginRes = mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/customer/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custLogin)))
                .andExpect(status().isOk())
                .andReturn();

        customerTokenA = objectMapper.readValue(custLoginRes.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();
    }

    private String loginAs(String email, String pass, String slug) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(pass);

        MvcResult r = mockMvc.perform(post("/" + slug + "/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(r.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();
    }

    @Test
    @Order(1)
    void tc15_1_orgATokenCannotAccessOrgBProducts_returns403() throws Exception {
        mockMvc.perform(get("/" + orgB.getSlug() + "/api/v1/products")
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(2)
    void tc15_2_orgATokenCannotAccessOrgBEmployees_returns403() throws Exception {
        mockMvc.perform(get("/" + orgB.getSlug() + "/api/v1/employees")
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void tc15_3_orgATokenCannotAccessOrgBCustomers_returns403() throws Exception {
        mockMvc.perform(get("/" + orgB.getSlug() + "/api/v1/customers")
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    void tc15_4_unauthenticatedRequest_returns401Or403() throws Exception {
        mockMvc.perform(get("/" + orgA.getSlug() + "/api/v1/products"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    void tc15_5_malformedToken_returns401Or403() throws Exception {
        mockMvc.perform(get("/" + orgA.getSlug() + "/api/v1/products")
                        .header("Authorization", "Bearer malformed.invalid.token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    void tc15_6_customerRoleCannotCreateProduct_returns403() throws Exception {
        CreateProductRequest req = CreateProductRequest.builder()
                .sku("CUST-FORBIDDEN")
                .name("Illegal Product")
                .price(BigDecimal.TEN)
                .tags(Collections.emptyList())
                .imageUrls(Collections.emptyList())
                .attributes(Collections.emptyMap())
                .build();

        mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/products")
                        .header("Authorization", "Bearer " + customerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    void tc15_7_customerRoleCannotViewEmployees_returns403() throws Exception {
        mockMvc.perform(get("/" + orgA.getSlug() + "/api/v1/employees")
                        .header("Authorization", "Bearer " + customerTokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    void tc15_8_publicRegistrationWithoutToken_returns201() throws Exception {
        CustomerRegisterRequest req = new CustomerRegisterRequest();
        req.setEmail("public-cust-" + UUID.randomUUID().toString().substring(0, 6) + "@flow.com");
        req.setPassword("Pass1234!");
        req.setFirstName("Public");
        req.setLastName("User");

        mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/customer/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(9)
    void tc15_9_publicWebhookEndpointWithoutToken_notBlockedBySecurity() throws Exception {
        WebhookPayload payload = new WebhookPayload("evt-dummy-sec", "payment.unknown", "dummy-ref");

        // Webhooks are public callbacks; unauthenticated is permitted by SecurityConfig
        // It might return 500 or 400 for unknown event type, but definitely NOT 401/403
        MvcResult res = mockMvc.perform(post("/api/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andReturn();

        int status = res.getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
    }
}
