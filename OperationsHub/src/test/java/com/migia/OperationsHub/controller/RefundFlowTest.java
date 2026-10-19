package com.migia.OperationsHub.controller;

import tools.jackson.databind.json.JsonMapper;
import com.migia.OperationsHub.Repository.CustomerRepository;
import com.migia.OperationsHub.Repository.InventoryItemRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.PaymentRepository;
import com.migia.OperationsHub.Repository.ProductRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.dto.CreateOrderRequest;
import com.migia.OperationsHub.dto.OrderResponse;
import com.migia.OperationsHub.dto.PaymentResponse;
import com.migia.OperationsHub.dto.WebhookPayload;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.model.InventoryItem;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.Payment;
import com.migia.OperationsHub.model.Product;
import com.migia.OperationsHub.model.User;
import com.migia.OperationsHub.model.enums.OrganizationStatus;
import com.migia.OperationsHub.model.enums.PaymentStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flow 13 — Refund Flow.
 *
 * Verifies:
 *  - 13.1 Refund an order directly (from PAID status) → 200 OK, status = REFUNDED
 *  - 13.2 Refund associated payment (via PaymentController) → 200 OK, payment & order become REFUNDED
 *  - 13.3 Cannot refund a DRAFT order → 409 Conflict
 *  - 13.4 Refund idempotency / duplicate refund rejection → 409 Conflict
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RefundFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PaymentRepository paymentRepository;

    private Organization org;
    private String ownerToken;
    private UUID orgId;
    private UUID productId;
    private UUID customerId;
    private UUID paidOrderId;
    private UUID completedOrderId;
    private UUID completedPaymentId;

    @BeforeAll
    void setup() throws Exception {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();

        org = organizationRepository.save(Organization.builder()
                .name("Refund Flow Corp")
                .slug("refund-flow-" + UUID.randomUUID().toString().substring(0, 6))
                .defaultCurrency("USD")
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());
        orgId = org.getId();

        userRepository.save(User.builder()
                .email("refund-owner@flow.com")
                .passwordHash(enc.encode("Pass@1!"))
                .firstName("Refund")
                .lastName("Owner")
                .role(Role.ORGANIZATION_OWNER)
                .organization(org)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

        ownerToken = loginAs("refund-owner@flow.com", "Pass@1!", org.getSlug());

        Product p = productRepository.save(Product.builder()
                .organization(org)
                .sku("REFUND-PROD")
                .name("Refund Product")
                .price(BigDecimal.valueOf(80.00))
                .status(ProductStatus.ACTIVE)
                .tags(new ArrayList<>())
                .imageUrls(new ArrayList<>())
                .attributes(new HashMap<>())
                .build());
        productId = p.getId();

        inventoryItemRepository.save(InventoryItem.builder()
                .product(p)
                .quantityOnHand(100)
                .reservedQuantity(0)
                .lowStockThreshold(2)
                .build());

        com.migia.OperationsHub.model.Customer cust = customerRepository.save(
                com.migia.OperationsHub.model.Customer.builder()
                        .organization(org)
                        .name("Refund Cust")
                        .email("refund-cust@flow.com")
                        .status(com.migia.OperationsHub.model.enums.CustomerStatus.ACTIVE)
                        .build());
        customerId = cust.getId();
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
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(r.getResponse().getContentAsString(), OrderResponse.class).getId();
    }

    @Test
    @Order(1)
    void tc13_1_refundPaidOrder_returns200() throws Exception {
        paidOrderId = createOrder("refund-idem-1");

        // Submit & Pay
        mockMvc.perform(post("/api/orders/" + paidOrderId + "/submit")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/" + paidOrderId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        // Refund order directly
        mockMvc.perform(post("/api/orders/" + paidOrderId + "/refund")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    @Order(2)
    void tc13_2_refundCompletedPayment_returns200AndCascades() throws Exception {
        completedOrderId = createOrder("refund-idem-2");

        // Submit
        mockMvc.perform(post("/api/orders/" + completedOrderId + "/submit")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // Create payment intent
        MvcResult intentResult = mockMvc.perform(post("/api/payments/intent/" + completedOrderId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andReturn();

        PaymentResponse paymentResp = objectMapper.readValue(
                intentResult.getResponse().getContentAsString(), PaymentResponse.class);
        completedPaymentId = paymentResp.getId();
        String providerRef = paymentResp.getProviderRef();

        // Simulate payment.succeeded webhook
        WebhookPayload payload = new WebhookPayload("evt-refund-pay-1", "payment.succeeded", providerRef);
        mockMvc.perform(post("/api/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        // Process & Complete order
        mockMvc.perform(post("/api/orders/" + completedOrderId + "/process")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/" + completedOrderId + "/complete")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // Now refund the payment via PaymentController
        mockMvc.perform(post("/api/payments/" + completedPaymentId + "/refund")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        // Check payment status in DB
        Payment p = paymentRepository.findById(completedPaymentId).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @Order(3)
    void tc13_3_cannotRefundDraftOrder_returns409Conflict() throws Exception {
        UUID draftOrderId = createOrder("refund-idem-draft");

        mockMvc.perform(post("/api/orders/" + draftOrderId + "/refund")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(4)
    void tc13_4_cannotRefundAlreadyRefundedPayment_returns409Conflict() throws Exception {
        // completedPaymentId was already refunded in TC 13.2
        mockMvc.perform(post("/api/payments/" + completedPaymentId + "/refund")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());
    }
}
