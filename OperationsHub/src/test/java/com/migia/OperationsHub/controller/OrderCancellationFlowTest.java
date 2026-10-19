package com.migia.OperationsHub.controller;

import tools.jackson.databind.json.JsonMapper;
import com.migia.OperationsHub.Repository.CustomerRepository;
import com.migia.OperationsHub.Repository.InventoryItemRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.ProductRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.dto.CreateOrderRequest;
import com.migia.OperationsHub.dto.OrderResponse;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Flow 11 — Order Cancellation.
 *
 * Tests:
 *  - Cancel in DRAFT state → inventory released
 *  - Cancel in PENDING_PAYMENT state → inventory released
 *  - Cannot cancel a COMPLETED order → 409
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderCancellationFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private CustomerRepository customerRepository;

    private Organization org;
    private String ownerToken;
    private UUID orgId;
    private UUID productId;
    private UUID customerId;
    private UUID draftOrderId;
    private UUID pendingOrderId;

    @BeforeAll
    void setup() throws Exception {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();

        org = organizationRepository.save(Organization.builder()
                .name("Cancel Flow Corp").slug("cancel-flow-" + UUID.randomUUID().toString().substring(0, 6))
                .status(OrganizationStatus.ACTIVE).ownerInvitationSent(false).build());
        orgId = org.getId();

        userRepository.save(User.builder()
                .email("cancel-owner@flow.com").passwordHash(enc.encode("Pass@1!"))
                .firstName("Cancel").lastName("Owner").role(Role.ORGANIZATION_OWNER)
                .organization(org).status(UserStatus.ACTIVE).mustChangePassword(false).build());

        ownerToken = loginAs("cancel-owner@flow.com", "Pass@1!", org.getSlug());

        Product p = productRepository.save(Product.builder()
                .organization(org).sku("CANCEL-PROD").name("Cancel Product")
                .price(BigDecimal.valueOf(10.00)).status(ProductStatus.ACTIVE)
                .tags(new ArrayList<>()).imageUrls(new ArrayList<>()).attributes(new HashMap<>()).build());
        productId = p.getId();

        inventoryItemRepository.save(InventoryItem.builder()
                .product(p).quantityOnHand(50).reservedQuantity(0).lowStockThreshold(2).build());

        com.migia.OperationsHub.model.Customer cust = customerRepository.save(
                com.migia.OperationsHub.model.Customer.builder()
                        .organization(org).name("Cancel Cust").email("cancel-cust@flow.com")
                        .status(com.migia.OperationsHub.model.enums.CustomerStatus.ACTIVE).build());
        customerId = cust.getId();
    }

    private String loginAs(String email, String pass, String slug) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(pass);

        MvcResult r = mockMvc.perform(post("/" + slug + "/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andReturn();

        return objectMapper.readValue(r.getResponse().getContentAsString(), LoginResponse.class).getAccessToken();
    }

    private UUID createOrder(String idemKey) throws Exception {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerId(customerId);
        CreateOrderRequest.OrderLineItem item = new CreateOrderRequest.OrderLineItem();
        item.setProductId(productId);
        item.setQuantity(1);
        req.setItems(List.of(item));

        MvcResult r = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("Idempotency-Key", idemKey)
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();

        return objectMapper.readValue(r.getResponse().getContentAsString(), OrderResponse.class).getId();
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 11.1 — Create draft order → 201
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(1)
    void tc11_1_createDraftOrder_returns201() throws Exception {
        draftOrderId = createOrder("cancel-idem-draft");
        assertThat(draftOrderId).isNotNull();
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 11.2 — Cancel DRAFT order → CANCELLED, inventory released
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(2)
    void tc11_2_cancelDraftOrder_returnsCancelled() throws Exception {
        mockMvc.perform(post("/api/orders/" + draftOrderId + "/cancel")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Verify inventory reservation was released
        InventoryItem inv = inventoryItemRepository.findByProductId(productId).orElseThrow();
        assertThat(inv.getReservedQuantity()).isZero();
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 11.3 — Create + submit order → PENDING_PAYMENT, then cancel
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(3)
    void tc11_3_cancelPendingPaymentOrder_returnsCancelled() throws Exception {
        pendingOrderId = createOrder("cancel-idem-pending");

        // Submit to PENDING_PAYMENT
        mockMvc.perform(post("/api/orders/" + pendingOrderId + "/submit")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

        // Cancel
        mockMvc.perform(post("/api/orders/" + pendingOrderId + "/cancel")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 11.4 — Cannot cancel a COMPLETED order → 409
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(4)
    void tc11_4_cancelCompletedOrder_returns409() throws Exception {
        // Build a fully completed order
        UUID completedOrderId = createOrder("cancel-idem-completed");

        mockMvc.perform(post("/api/orders/" + completedOrderId + "/submit")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/" + completedOrderId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/" + completedOrderId + "/process")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/" + completedOrderId + "/complete")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // Now try to cancel — must fail
        mockMvc.perform(post("/api/orders/" + completedOrderId + "/cancel")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());
    }
}
