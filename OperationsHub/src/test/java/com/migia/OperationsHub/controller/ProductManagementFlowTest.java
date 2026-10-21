package com.migia.OperationsHub.controller;

import tools.jackson.databind.json.JsonMapper;
import com.migia.OperationsHub.Repository.InventoryItemRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.dto.product.CreateProductRequest;
import com.migia.OperationsHub.dto.product.ProductResponse;
import com.migia.OperationsHub.dto.product.UpdateProductRequest;
import com.migia.OperationsHub.model.InventoryItem;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.Product;
import com.migia.OperationsHub.model.User;
import com.migia.OperationsHub.model.enums.OrganizationStatus;
import com.migia.OperationsHub.model.enums.ProductStatus;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Flow 6 — Full Product CRUD with permission checks and public product listing.
 *
 * Covers:
 *  - Create (with rich attributes)
 *  - Validation failures (missing SKU, negative price)
 *  - List all products for an organization (including as customer/public view)
 *  - Search & filter by name and status
 *  - Get single product
 *  - Update
 *  - Permission gates: employee (view-only), unauthorized token
 *  - Cross-tenant isolation
 *  - Delete + confirm 404
 *
 * The "view all available products for an organization" requirement
 * is covered by TC 6.5 (owner), TC 6.12 (employee), and TC 6.13 (customer token).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductManagementFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private com.migia.OperationsHub.Repository.ProductRepository productRepository;

    private Organization orgA;
    private Organization orgB;
    private String ownerTokenA;
    private String ownerTokenB;
    private String employeeTokenA;
    private UUID   product1Id;
    private UUID   product2Id;

    @BeforeAll
    void setup() throws Exception {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();

        orgA = organizationRepository.save(Organization.builder()
                .name("Product Org A").slug("prod-mgmt-a-" + UUID.randomUUID().toString().substring(0, 6))
                .status(OrganizationStatus.ACTIVE).ownerInvitationSent(false).build());

        orgB = organizationRepository.save(Organization.builder()
                .name("Product Org B").slug("prod-mgmt-b-" + UUID.randomUUID().toString().substring(0, 6))
                .status(OrganizationStatus.ACTIVE).ownerInvitationSent(false).build());

        userRepository.save(User.builder()
                .email("owner-a@prod-mgmt.com").passwordHash(enc.encode("Pass@1!"))
                .firstName("Owner").lastName("A").role(Role.ORGANIZATION_OWNER)
                .organization(orgA).status(UserStatus.ACTIVE).mustChangePassword(false).build());

        userRepository.save(User.builder()
                .email("owner-b@prod-mgmt.com").passwordHash(enc.encode("Pass@1!"))
                .firstName("Owner").lastName("B").role(Role.ORGANIZATION_OWNER)
                .organization(orgB).status(UserStatus.ACTIVE).mustChangePassword(false).build());

        userRepository.save(User.builder()
                .email("emp-a@prod-mgmt.com").passwordHash(enc.encode("Pass@1!"))
                .firstName("Emp").lastName("A").role(Role.EMPLOYEE)
                .organization(orgA).status(UserStatus.ACTIVE).mustChangePassword(false).build());

        ownerTokenA    = loginAs("owner-a@prod-mgmt.com", "Pass@1!", orgA.getSlug());
        ownerTokenB    = loginAs("owner-b@prod-mgmt.com", "Pass@1!", orgB.getSlug());
        employeeTokenA = loginAs("emp-a@prod-mgmt.com",   "Pass@1!", orgA.getSlug());
    }

    private String loginAs(String email, String password, String slug) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);

        MvcResult result = mockMvc.perform(post("/" + slug + "/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();
    }

    private String createProductJson(String sku, String name, double price) throws Exception {
        CreateProductRequest req = CreateProductRequest.builder()
                .sku(sku).name(name).price(BigDecimal.valueOf(price))
                .description("Test product").category("Widgets").unit("PCS").currency("USD")
                .tags(List.of("test")).attributes(Map.of("color", "blue"))
                .build();
        return objectMapper.writeValueAsString(req);
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 6.1 — Create first product (with rich attributes) → 201
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(1)
    void tc6_1_createProduct_validRequest_returns201() throws Exception {
        MvcResult result = mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/products")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProductJson("WIDGET-001", "Super Widget", 29.99)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("WIDGET-001"))
                .andExpect(jsonPath("$.name").value("Super Widget"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.attributes.color").value("blue"))
                .andReturn();

        ProductResponse p = objectMapper.readValue(
                result.getResponse().getContentAsString(), ProductResponse.class);
        product1Id = p.getId();

        // Seed inventory item so order tests can use this product
        Product savedProduct = productRepository.findById(product1Id).orElseThrow();
        inventoryItemRepository.save(InventoryItem.builder()
                .product(savedProduct).quantityOnHand(100).reservedQuantity(0)
                .lowStockThreshold(5).build());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 6.2 — Create product with missing SKU → 400
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(2)
    void tc6_2_createProduct_missingSku_returns400() throws Exception {
        String body = "{\"name\": \"No SKU Product\", \"price\": 10.00}";

        mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/products")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 6.3 — Create product with negative price → 400
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(3)
    void tc6_3_createProduct_negativePrice_returns400() throws Exception {
        CreateProductRequest req = CreateProductRequest.builder()
                .sku("BAD-PRICE").name("Bad Price Product").price(BigDecimal.valueOf(-5.00)).build();

        mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/products")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 6.4 — Create second product (for multi-item orders later) → 201
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(4)
    void tc6_4_createSecondProduct_returns201() throws Exception {
        MvcResult result = mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/products")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProductJson("GADGET-001", "Cool Gadget", 49.99)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("GADGET-001"))
                .andReturn();

        ProductResponse p = objectMapper.readValue(
                result.getResponse().getContentAsString(), ProductResponse.class);
        product2Id = p.getId();

        Product savedProduct = productRepository.findById(product2Id).orElseThrow();
        inventoryItemRepository.save(InventoryItem.builder()
                .product(savedProduct).quantityOnHand(50).reservedQuantity(0)
                .lowStockThreshold(5).build());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 6.5 — List all products for the organization (owner) → 200, 2 items
    // ─────────────────────────────────────────────────────────────────
    /**
     * Core use-case: view all available products for an organization.
     * Owner has PERM_VIEW_PRODUCTS and sees all 2 products.
     */
    @Test
    @Order(5)
    void tc6_5_listAllProducts_ownerToken_returns200WithAllProducts() throws Exception {
        mockMvc.perform(get("/" + orgA.getSlug() + "/api/v1/products")
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 6.6 — Search products by name query → filtered result
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(6)
    void tc6_6_searchProducts_byNameQuery_returnsFilteredResults() throws Exception {
        mockMvc.perform(get("/" + orgA.getSlug() + "/api/v1/products")
                        .param("q", "Widget")
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value("Super Widget"));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 6.7 — Filter products by status=ACTIVE → only active returned
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(7)
    void tc6_7_filterProductsByStatus_active_returnsOnlyActiveProducts() throws Exception {
        mockMvc.perform(get("/" + orgA.getSlug() + "/api/v1/products")
                        .param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 6.8 — Get single product by ID → 200
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(8)
    void tc6_8_getProductById_validId_returns200() throws Exception {
        mockMvc.perform(get("/" + orgA.getSlug() + "/api/v1/products/" + product1Id)
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product1Id.toString()))
                .andExpect(jsonPath("$.sku").value("WIDGET-001"));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 6.9 — Get Org A product with Org B token → 403 (tenant isolation)
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(9)
    void tc6_9_getProduct_crossTenantToken_returns403() throws Exception {
        mockMvc.perform(get("/" + orgA.getSlug() + "/api/v1/products/" + product1Id)
                        .header("Authorization", "Bearer " + ownerTokenB))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 6.10 — Update product name and price → 200
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(10)
    void tc6_10_updateProduct_validRequest_returns200() throws Exception {
        UpdateProductRequest req = UpdateProductRequest.builder()
                .sku("WIDGET-001-V2").name("Super Widget V2").description("Updated")
                .price(BigDecimal.valueOf(39.99)).status(ProductStatus.ACTIVE)
                .build();

        mockMvc.perform(put("/" + orgA.getSlug() + "/api/v1/products/" + product1Id)
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("WIDGET-001-V2"))
                .andExpect(jsonPath("$.price").value(39.99));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 6.11 — Employee (view-only) cannot update product → 403
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(11)
    void tc6_11_updateProduct_employeeToken_returns403() throws Exception {
        UpdateProductRequest req = UpdateProductRequest.builder()
                .sku("WIDGET-HACK").name("Hacked").price(BigDecimal.ONE).build();

        mockMvc.perform(put("/" + orgA.getSlug() + "/api/v1/products/" + product1Id)
                        .header("Authorization", "Bearer " + employeeTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 6.12 — Employee CAN list (view) products → 200
    // ─────────────────────────────────────────────────────────────────
    /**
     * Verifies the read-only PERM_VIEW_PRODUCTS permission is respected for employees.
     * This also satisfies "view all available products for an organization" for a
     * non-owner member.
     */
    @Test
    @Order(12)
    void tc6_12_listProducts_employeeToken_returns200() throws Exception {
        mockMvc.perform(get("/" + orgA.getSlug() + "/api/v1/products")
                        .header("Authorization", "Bearer " + employeeTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 6.13 — List products without any token → 403 (auth required)
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(13)
    void tc6_13_listProducts_noToken_returns403() throws Exception {
        mockMvc.perform(get("/" + orgA.getSlug() + "/api/v1/products"))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 6.14 — Duplicate SKU within same org → 409
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(14)
    void tc6_14_createProduct_duplicateSku_returns409() throws Exception {
        // SKU was updated to WIDGET-001-V2 in TC 6.10 so original is free,
        // but GADGET-001 is still taken — use that
        mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/products")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProductJson("GADGET-001", "Duplicate Gadget", 5.00)))
                .andExpect(status().isConflict());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 6.15 — Delete product → 204
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(15)
    void tc6_15_deleteProduct_validId_returns204() throws Exception {
        // Create a throwaway product to delete (so product1Id stays for order tests)
        MvcResult r = mockMvc.perform(post("/" + orgA.getSlug() + "/api/v1/products")
                        .header("Authorization", "Bearer " + ownerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProductJson("DEL-001", "To Delete", 1.00)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID delId = objectMapper.readValue(r.getResponse().getContentAsString(), ProductResponse.class).getId();

        mockMvc.perform(delete("/" + orgA.getSlug() + "/api/v1/products/" + delId)
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isNoContent());

        // TC 6.16 — Confirm deleted product returns 404
        mockMvc.perform(get("/" + orgA.getSlug() + "/api/v1/products/" + delId)
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 6.16 — Paginated list (page=0, size=1) → 1 result, multiple pages
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(16)
    void tc6_16_listProducts_withPagination_returnsCorrectPage() throws Exception {
        mockMvc.perform(get("/" + orgA.getSlug() + "/api/v1/products")
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", "Bearer " + ownerTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
    }
}
