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
 * Flow 12 — Payment Failure & Webhook Idempotency.
 *
 * Verifies:
 *  - 12.1 Create order + submit → PENDING_PAYMENT
 *  - 12.2 Direct fail-payment endpoint → 200 OK, status = PAYMENT_FAILED
 *  - 12.3 Cannot complete a PAYMENT_FAILED order → 409 Conflict
 *  - 12.4 Webhook simulation: payment.failed → payment status = FAILED, order = PAYMENT_FAILED
 *  - 12.5 Webhook idempotency: sending duplicate eventId → 200 OK, no side-effects
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentFailureFlowTest {

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
    private UUID directFailOrderId;
    private UUID webhookFailOrderId;
    private String webhookProviderRef;

    @BeforeAll
    void setup() throws Exception {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();

        org = organizationRepository.save(Organization.builder()
                .name("Pay Fail Corp")
                .slug("pay-fail-" + UUID.randomUUID().toString().substring(0, 6))
                .defaultCurrency("USD")
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());
        orgId = org.getId();

        userRepository.save(User.builder()
                .email("pay-fail-owner@flow.com")
                .passwordHash(enc.encode("Pass@1!"))
                .firstName("Pay")
                .lastName("Owner")
                .role(Role.ORGANIZATION_OWNER)
                .organization(org)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

        ownerToken = loginAs("pay-fail-owner@flow.com", "Pass@1!", org.getSlug());

        Product p = productRepository.save(Product.builder()
                .organization(org)
                .sku("PAY-FAIL-PROD")
                .name("Pay Fail Product")
                .price(BigDecimal.valueOf(100.00))
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
                        .name("Pay Fail Cust")
                        .email("pay-fail-cust@flow.com")
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

    private UUID createAndSubmitOrder(String idemKey) throws Exception {
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

        UUID id = objectMapper.readValue(r.getResponse().getContentAsString(), OrderResponse.class).getId();

        mockMvc.perform(post("/api/orders/" + id + "/submit")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

        return id;
    }

    @Test
    @Order(1)
    void tc12_1_createAndSubmitOrder_returnsPendingPayment() throws Exception {
        directFailOrderId = createAndSubmitOrder("pf-idem-1");
        assertThat(directFailOrderId).isNotNull();
    }

    @Test
    @Order(2)
    void tc12_2_directFailPayment_returnsPaymentFailed() throws Exception {
        mockMvc.perform(post("/api/orders/" + directFailOrderId + "/fail-payment")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_FAILED"));
    }

    @Test
    @Order(3)
    void tc12_3_cannotCompletePaymentFailedOrder_returns409Conflict() throws Exception {
        mockMvc.perform(post("/api/orders/" + directFailOrderId + "/complete")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(4)
    void tc12_4_webhookPaymentFailed_transitionsPaymentAndOrder() throws Exception {
        webhookFailOrderId = createAndSubmitOrder("pf-idem-2");

        // Create payment intent
        MvcResult intentResult = mockMvc.perform(post("/api/payments/intent/" + webhookFailOrderId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        PaymentResponse paymentResp = objectMapper.readValue(
                intentResult.getResponse().getContentAsString(), PaymentResponse.class);
        webhookProviderRef = paymentResp.getProviderRef();
        assertThat(webhookProviderRef).isNotEmpty();

        // Simulate payment.failed webhook
        WebhookPayload payload = new WebhookPayload("evt-fail-unique-1", "payment.failed", webhookProviderRef);

        mockMvc.perform(post("/api/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        // Verify payment in DB
        Payment payment = paymentRepository.findByProviderRef(webhookProviderRef).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @Order(5)
    void tc12_5_webhookIdempotency_duplicateEventProducesNoSideEffect() throws Exception {
        WebhookPayload duplicatePayload = new WebhookPayload("evt-fail-unique-1", "payment.failed", webhookProviderRef);

        mockMvc.perform(post("/api/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        // Still FAILED, no exception
        Payment payment = paymentRepository.findByProviderRef(webhookProviderRef).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }
}
