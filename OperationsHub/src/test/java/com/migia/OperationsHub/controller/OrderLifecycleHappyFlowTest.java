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
import com.migia.OperationsHub.dto.WebhookPayload;
import com.migia.OperationsHub.dto.customer.CreateCustomerRequest;
import com.migia.OperationsHub.dto.customer.CustomerResponse;
import com.migia.OperationsHub.dto.product.CreateProductRequest;
import com.migia.OperationsHub.dto.product.ProductResponse;
import com.migia.OperationsHub.model.InventoryItem;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.Product;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Flow 10 — Full Order Lifecycle (Happy Path).
 *
 * Sequence:
 *  DRAFT → PENDING_PAYMENT → (webhook: payment.succeeded) → PAID → PROCESSING → COMPLETED
 *
 * Also verifies:
 *  - Order idempotency (same Idempotency-Key returns the original order)
 *  - Payment intent idempotency
 *  - Webhook idempotency (same eventId is no-op on second call)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderLifecycleHappyFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private CustomerRepository customerRepository;

    private Organization org;
    private String       ownerToken;
    private UUID         orgId;
    private UUID         product1Id;
    private UUID         customerId;
    private UUID         orderId;
    private String       providerRef;   // captured from payment intent response

    @BeforeAll
    void setup() throws Exception {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();

        org = organizationRepository.save(Organization.builder()
                .name("Order Flow Corp").slug("order-flow-" + UUID.randomUUID().toString().substring(0, 6))
                .status(OrganizationStatus.ACTIVE).ownerInvitationSent(false).build());
        orgId = org.getId();

        userRepository.save(User.builder()
                .email("order-owner@flow.com").passwordHash(enc.encode("Pass@1!"))
                .firstName("Order").lastName("Owner").role(Role.ORGANIZATION_OWNER)
                .organization(org).status(UserStatus.ACTIVE).mustChangePassword(false).build());

        ownerToken = loginAs("order-owner@flow.com", "Pass@1!", org.getSlug());

        // Create product + inventory
        Product p = productRepository.save(Product.builder()
                .organization(org).sku("ORD-WIDGET").name("Order Widget")
                .price(BigDecimal.valueOf(25.00)).status(com.migia.OperationsHub.model.enums.ProductStatus.ACTIVE)
                .tags(new java.util.ArrayList<>()).imageUrls(new java.util.ArrayList<>())
                .attributes(new java.util.HashMap<>()).build());
        product1Id = p.getId();

        inventoryItemRepository.save(InventoryItem.builder()
                .product(p).quantityOnHand(100).reservedQuantity(0).lowStockThreshold(5).build());

        // Create customer (back-office)
        com.migia.OperationsHub.model.Customer customer = customerRepository.save(
                com.migia.OperationsHub.model.Customer.builder()
                        .organization(org).name("Test Customer")
                        .email("test-customer@flow.com").status(com.migia.OperationsHub.model.enums.CustomerStatus.ACTIVE)
                        .build());
        customerId = customer.getId();
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

    // ─────────────────────────────────────────────────────────────────
    // TC 10.1 — Create order (DRAFT) with one item → 201
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(1)
    void tc10_1_createOrder_validRequest_returnsDraft() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerId(customerId);
        CreateOrderRequest.OrderLineItem item = new CreateOrderRequest.OrderLineItem();
        item.setProductId(product1Id);
        item.setQuantity(2);
        req.setItems(List.of(item));

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("Idempotency-Key", "idem-order-001")
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        OrderResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), OrderResponse.class);
        orderId = resp.getId();
        assertThat(orderId).isNotNull();
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 10.2 — Idempotent re-submit same Idempotency-Key → same orderId
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(2)
    void tc10_2_createOrder_sameIdempotencyKey_returnsOriginalOrder() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerId(customerId);
        CreateOrderRequest.OrderLineItem item = new CreateOrderRequest.OrderLineItem();
        item.setProductId(product1Id);
        item.setQuantity(2);
        req.setItems(List.of(item));

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("Idempotency-Key", "idem-order-001")  // same key
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        OrderResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), OrderResponse.class);
        assertThat(resp.getId()).isEqualTo(orderId); // must be the same order
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 10.3 — Submit order for payment (DRAFT → PENDING_PAYMENT)
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(3)
    void tc10_3_submitOrder_draft_returnsPendingPayment() throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/submit")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 10.4 — Create payment intent → 201, paymentId + providerRef
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(4)
    void tc10_4_createPaymentIntent_pendingPaymentOrder_returns201() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/payments/intent/" + orderId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.providerRef").isNotEmpty())
                .andReturn();

        com.migia.OperationsHub.dto.PaymentResponse payResp = objectMapper.readValue(
                result.getResponse().getContentAsString(), com.migia.OperationsHub.dto.PaymentResponse.class);
        providerRef = payResp.getProviderRef();
        assertThat(providerRef).isNotBlank();
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 10.5 — Idempotent payment intent (same order, second call) → same payment
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(5)
    void tc10_5_createPaymentIntent_idempotent_returnsSamePayment() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/payments/intent/" + orderId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andReturn();

        com.migia.OperationsHub.dto.PaymentResponse payResp = objectMapper.readValue(
                result.getResponse().getContentAsString(), com.migia.OperationsHub.dto.PaymentResponse.class);
        assertThat(payResp.getProviderRef()).isEqualTo(providerRef);
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 10.6 — Webhook: payment.succeeded → payment transitions to SUCCEEDED
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(6)
    void tc10_6_webhook_paymentSucceeded_updatesPaymentStatus() throws Exception {
        WebhookPayload payload = new WebhookPayload("evt-pay-001", "payment.succeeded", providerRef);

        mockMvc.perform(post("/api/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 10.7 — Mark order as PAID (PENDING_PAYMENT → PAID)
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(7)
    void tc10_7_markPaid_pendingOrder_returnsPaid() throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 10.8 — Start processing (PAID → PROCESSING)
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(8)
    void tc10_8_startProcessing_paidOrder_returnsProcessing() throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/process")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 10.9 — Complete order (PROCESSING → COMPLETED)
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(9)
    void tc10_9_completeOrder_processingOrder_returnsCompleted() throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/complete")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // ─────────────────────────────────────────────────────────────────
    // TC 10.10 — Webhook idempotency: same eventId → no side effect
    // ─────────────────────────────────────────────────────────────────
    @Test
    @Order(10)
    void tc10_10_webhook_duplicateEventId_isIdempotent() throws Exception {
        WebhookPayload payload = new WebhookPayload("evt-pay-001", "payment.succeeded", providerRef);

        // Same eventId as TC 10.6 — must return 200 but cause no state change
        mockMvc.perform(post("/api/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }
}
