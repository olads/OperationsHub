package com.migia.OperationsHub.controller;

import tools.jackson.databind.json.JsonMapper;
import com.migia.OperationsHub.Repository.InventoryItemRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.ProductRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.dto.InventoryAdjustmentRequest;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flow 7 — Inventory Management.
 *
 * Verifies:
 *  - 7.1 Add initial stock for product 1 → 200 OK
 *  - 7.2 Add more stock (cumulative) → 200 OK
 *  - 7.3 Remove some stock → 200 OK
 *  - 7.4 Remove more than available stock fails → 4xx or 5xx error
 *  - 7.5 Add stock for product 2 → 200 OK
 *  - 7.6 Add stock with zero / negative quantity fails validation → 400 Bad Request
 *  - 7.7 Add stock with missing reason fails validation → 400 Bad Request
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InventoryManagementFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;

    private Organization org;
    private String ownerToken;
    private UUID product1Id;
    private UUID product2Id;

    @BeforeAll
    void setup() throws Exception {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();

        org = organizationRepository.save(Organization.builder()
                .name("Inventory Flow Corp")
                .slug("inv-flow-" + UUID.randomUUID().toString().substring(0, 6))
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());

        userRepository.save(User.builder()
                .email("inv-owner@flow.com")
                .passwordHash(enc.encode("Pass@1!"))
                .firstName("Inventory")
                .lastName("Owner")
                .role(Role.ORGANIZATION_OWNER)
                .organization(org)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

        ownerToken = loginAs("inv-owner@flow.com", "Pass@1!", org.getSlug());

        Product p1 = productRepository.save(Product.builder()
                .organization(org)
                .sku("INV-SKU-001")
                .name("Inventory Product 1")
                .price(BigDecimal.valueOf(25.00))
                .status(ProductStatus.ACTIVE)
                .tags(new ArrayList<>())
                .imageUrls(new ArrayList<>())
                .attributes(new HashMap<>())
                .build());
        product1Id = p1.getId();

        inventoryItemRepository.save(InventoryItem.builder()
                .product(p1)
                .quantityOnHand(0)
                .reservedQuantity(0)
                .lowStockThreshold(5)
                .build());

        Product p2 = productRepository.save(Product.builder()
                .organization(org)
                .sku("INV-SKU-002")
                .name("Inventory Product 2")
                .price(BigDecimal.valueOf(50.00))
                .status(ProductStatus.ACTIVE)
                .tags(new ArrayList<>())
                .imageUrls(new ArrayList<>())
                .attributes(new HashMap<>())
                .build());
        product2Id = p2.getId();

        inventoryItemRepository.save(InventoryItem.builder()
                .product(p2)
                .quantityOnHand(0)
                .reservedQuantity(0)
                .lowStockThreshold(2)
                .build());
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
    void tc7_1_addInitialStock_returns200() throws Exception {
        InventoryAdjustmentRequest req = new InventoryAdjustmentRequest();
        req.setQuantity(50);
        req.setReason("Initial stock load");
        req.setReferenceId("PO-1001");

        mockMvc.perform(post("/api/inventory/" + product1Id + "/add")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        InventoryItem item = inventoryItemRepository.findByProductId(product1Id).orElseThrow();
        assertThat(item.getQuantityOnHand()).isEqualTo(50);
        assertThat(item.getAvailableQuantity()).isEqualTo(50);
    }

    @Test
    @Order(2)
    void tc7_2_addMoreStockCumulative_returns200() throws Exception {
        InventoryAdjustmentRequest req = new InventoryAdjustmentRequest();
        req.setQuantity(25);
        req.setReason("Restock shipment");
        req.setReferenceId("PO-1002");

        mockMvc.perform(post("/api/inventory/" + product1Id + "/add")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        InventoryItem item = inventoryItemRepository.findByProductId(product1Id).orElseThrow();
        assertThat(item.getQuantityOnHand()).isEqualTo(75);
    }

    @Test
    @Order(3)
    void tc7_3_removeSomeStock_returns200() throws Exception {
        InventoryAdjustmentRequest req = new InventoryAdjustmentRequest();
        req.setQuantity(15);
        req.setReason("Damaged goods removed");
        req.setReferenceId("DMG-001");

        mockMvc.perform(post("/api/inventory/" + product1Id + "/remove")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        InventoryItem item = inventoryItemRepository.findByProductId(product1Id).orElseThrow();
        assertThat(item.getQuantityOnHand()).isEqualTo(60);
    }

    @Test
    @Order(4)
    void tc7_4_removeMoreThanAvailableStock_fails() throws Exception {
        InventoryAdjustmentRequest req = new InventoryAdjustmentRequest();
        req.setQuantity(100); // Only 60 in stock!
        req.setReason("Over-deduction attempt");
        req.setReferenceId("ERR-001");

        mockMvc.perform(post("/api/inventory/" + product1Id + "/remove")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is5xxServerError());

        // Verify stock remained unchanged at 60
        InventoryItem item = inventoryItemRepository.findByProductId(product1Id).orElseThrow();
        assertThat(item.getQuantityOnHand()).isEqualTo(60);
    }

    @Test
    @Order(5)
    void tc7_5_addStockForProduct2_returns200() throws Exception {
        InventoryAdjustmentRequest req = new InventoryAdjustmentRequest();
        req.setQuantity(30);
        req.setReason("Stocking product 2");
        req.setReferenceId("PO-2001");

        mockMvc.perform(post("/api/inventory/" + product2Id + "/add")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        InventoryItem item = inventoryItemRepository.findByProductId(product2Id).orElseThrow();
        assertThat(item.getQuantityOnHand()).isEqualTo(30);
    }

    @Test
    @Order(6)
    void tc7_6_addStockZeroOrNegativeQuantity_failsValidation() throws Exception {
        InventoryAdjustmentRequest req = new InventoryAdjustmentRequest();
        req.setQuantity(0); // violates @Positive
        req.setReason("Zero quantity test");

        mockMvc.perform(post("/api/inventory/" + product1Id + "/add")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    void tc7_7_addStockMissingReason_failsValidation() throws Exception {
        InventoryAdjustmentRequest req = new InventoryAdjustmentRequest();
        req.setQuantity(10);
        req.setReason(null); // violates @NotNull

        mockMvc.perform(post("/api/inventory/" + product1Id + "/add")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
