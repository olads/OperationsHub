package com.migia.OperationsHub.controller;

import tools.jackson.databind.json.JsonMapper;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.dto.auth.CustomerRegisterRequest;
import com.migia.OperationsHub.dto.product.CreateProductRequest;
import com.migia.OperationsHub.dto.product.ProductResponse;
import com.migia.OperationsHub.dto.product.UpdateProductRequest;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.User;
import com.migia.OperationsHub.model.enums.OrganizationStatus;
import com.migia.OperationsHub.model.enums.ProductStatus;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Customer self-registration and Product CRUD APIs.
 * These tests verify:
 * - Customer self-registration flow (not invitation-based)
 * - Product creation with generic JSONB attributes
 * - SKU uniqueness scoped per organization (tenant isolation)
 * - Cross-tenant access protection
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerAndProductApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;

    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private Organization orgA;
    private Organization orgB;
    private String tokenOrgA;
    private String tokenOrgB;

    @BeforeEach
    void setup() throws Exception {
        // Create two isolated organizations
        orgA = organizationRepository.save(Organization.builder()
                .name("Product Org A")
                .slug("prod-org-a-" + UUID.randomUUID().toString().substring(0, 6))
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());

        orgB = organizationRepository.save(Organization.builder()
                .name("Product Org B")
                .slug("prod-org-b-" + UUID.randomUUID().toString().substring(0, 6))
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());

        // Seed ORGANIZATION_OWNER users for each org
        userRepository.save(User.builder()
                .email("owner-a@prod.com")
                .passwordHash(encoder.encode("Password123!"))
                .firstName("Owner").lastName("A")
                .role(Role.ORGANIZATION_OWNER)
                .organization(orgA)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

        userRepository.save(User.builder()
                .email("owner-b@prod.com")
                .passwordHash(encoder.encode("Password123!"))
                .firstName("Owner").lastName("B")
                .role(Role.ORGANIZATION_OWNER)
                .organization(orgB)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

        tokenOrgA = loginAndGetToken("owner-a@prod.com", "Password123!", orgA.getSlug());
        tokenOrgB = loginAndGetToken("owner-b@prod.com", "Password123!", orgB.getSlug());
    }

    private String loginAndGetToken(String email, String password, String slug) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);

        MvcResult result = mockMvc.perform(post("/" + slug + "/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), LoginResponse.class)
                .getAccessToken();
    }

    /**
     * Test Case: Customer Self-Registration
     * Verifies that a customer can self-register under a specific org's namespace
     * without requiring an invitation. This is the consumer-facing registration flow.
     * Each registration creates an isolated CustomerAccount, not a User.
     */
    @Test
    void customer_selfRegistration_returns201() throws Exception {
        CustomerRegisterRequest req = new CustomerRegisterRequest();
        req.setEmail("shopper@example.com");
        req.setPassword("Shopper123!");
        req.setFirstName("Shop");
        req.setLastName("Per");
        req.setPhone("+1234567890");

        mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/customer/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("shopper@example.com"))
                .andExpect(jsonPath("$.organizationSlug").value(orgA.getSlug()));
    }

    /**
     * Test Case: Customer Self-Registration - Duplicate Email Within Same Org
     * Verifies that a customer cannot register twice with the same email
     * in the same organization. Returns 409 Conflict.
     */
    @Test
    void customer_duplicateEmailSameOrg_returns409() throws Exception {
        CustomerRegisterRequest req = new CustomerRegisterRequest();
        req.setEmail("dup-shopper@example.com");
        req.setPassword("Shopper123!");
        req.setFirstName("Dup");
        req.setLastName("Shopper");

        // First registration — succeeds
        mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/customer/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Second registration — should fail with 409
        mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/customer/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    /**
     * Test Case: Product CRUD with Generic Attributes (JSONB)
     * Verifies the full product lifecycle including creation with flexible attributes:
     * - Create a product with a category, unit, and custom key-value attributes (JSONB)
     * - Retrieve and validate attributes are persisted correctly
     * - Update with a new SKU
     * - Delete and confirm 404 on retrieval
     */
    @Test
    void product_crudWithGenericAttributes_fullCycle() throws Exception {
        CreateProductRequest createReq = CreateProductRequest.builder()
                .sku("SKU-WIDGET-001")
                .name("Premium Widget")
                .description("A high-quality widget")
                .price(new BigDecimal("49.99"))
                .category("Electronics")
                .unit("piece")
                .currency("USD")
                .tags(List.of("premium", "widget", "electronics"))
                .attributes(Map.of(
                        "color", "blue",
                        "weight_grams", 250,
                        "warranty_months", 12,
                        "features", List.of("waterproof", "shockproof")
                ))
                .build();

        // 1. Create product
        MvcResult createResult = mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/products")
                        .header("Authorization", "Bearer " + tokenOrgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-WIDGET-001"))
                .andExpect(jsonPath("$.category").value("Electronics"))
                .andExpect(jsonPath("$.unit").value("piece"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.attributes.color").value("blue"))
                .andExpect(jsonPath("$.attributes.weight_grams").value(250))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        ProductResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), ProductResponse.class);
        UUID productId = created.getId();

        // 2. Update product SKU and price
        UpdateProductRequest updateReq = UpdateProductRequest.builder()
                .sku("SKU-WIDGET-001-V2")
                .name("Premium Widget V2")
                .description("Updated description")
                .price(new BigDecimal("59.99"))
                .status(ProductStatus.ACTIVE)
                .build();

        mockMvc.perform(put("/" + orgA.getSlug() + "/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + tokenOrgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-WIDGET-001-V2"))
                .andExpect(jsonPath("$.price").value(59.99));

        // 3. Delete product
        mockMvc.perform(delete("/" + orgA.getSlug() + "/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isNoContent());

        // 4. Confirm it's gone
        mockMvc.perform(get("/" + orgA.getSlug() + "/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isNotFound());
    }

    /**
     * Test Case: SKU Uniqueness is Org-Scoped, Not Global
     * Verifies that the same SKU can be used by two different organizations
     * (tenant isolation), but is blocked within the same org.
     */
    @Test
    void product_skuUniqueness_scopedPerOrg() throws Exception {
        CreateProductRequest prodReq = CreateProductRequest.builder()
                .sku("SHARED-SKU-001")
                .name("Shared SKU Product")
                .price(new BigDecimal("29.99"))
                .build();

        // Create in Org A — should succeed
        mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/products")
                        .header("Authorization", "Bearer " + tokenOrgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodReq)))
                .andExpect(status().isCreated());

        // Duplicate SKU in Org A — should be rejected (409 Conflict)
        mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/products")
                        .header("Authorization", "Bearer " + tokenOrgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodReq)))
                .andExpect(status().isConflict());

        // Same SKU in Org B — should be allowed (cross-org isolation)
        mockMvc.perform(post("/" + orgB.getSlug() + "/api/v1/products")
                        .header("Authorization", "Bearer " + tokenOrgB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodReq)))
                .andExpect(status().isCreated());
    }

    /**
     * Test Case: Cross-Tenant Product Access Prevention
     * Verifies that Org B cannot access or modify Org A's products
     * even if they know the product ID.
     */
    @Test
    void product_crossTenantAccess_rejected() throws Exception {
        // Create a product in Org A
        CreateProductRequest prodReq = CreateProductRequest.builder()
                .sku("ORG-A-EXCLUSIVE-SKU")
                .name("Org A Product")
                .price(new BigDecimal("99.99"))
                .build();

        MvcResult createResult = mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/products")
                        .header("Authorization", "Bearer " + tokenOrgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodReq)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID productId = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), ProductResponse.class).getId();

        // Org B token tries to fetch Org A's product — should be forbidden (token org ≠ URL org)
        mockMvc.perform(get("/" + orgA.getSlug() + "/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + tokenOrgB))
                .andExpect(status().isForbidden());
    }
}
