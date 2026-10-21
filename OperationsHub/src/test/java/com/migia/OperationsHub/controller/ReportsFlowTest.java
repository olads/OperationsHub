package com.migia.OperationsHub.controller;

import tools.jackson.databind.json.JsonMapper;
import com.migia.OperationsHub.Repository.CustomerRepository;
import com.migia.OperationsHub.Repository.InventoryItemRepository;
import com.migia.OperationsHub.Repository.OrderRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.PaymentRepository;
import com.migia.OperationsHub.Repository.ProductRepository;
import com.migia.OperationsHub.Repository.UserRepository;
import com.migia.OperationsHub.dto.auth.LoginRequest;
import com.migia.OperationsHub.dto.auth.LoginResponse;
import com.migia.OperationsHub.model.Customer;
import com.migia.OperationsHub.model.InventoryItem;
import com.migia.OperationsHub.model.Order;
import com.migia.OperationsHub.model.OrderItem;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.Payment;
import com.migia.OperationsHub.model.Product;
import com.migia.OperationsHub.model.User;
import com.migia.OperationsHub.model.enums.CustomerStatus;
import com.migia.OperationsHub.model.enums.OrderStatus;
import com.migia.OperationsHub.model.enums.OrganizationStatus;
import com.migia.OperationsHub.model.enums.PaymentStatus;
import com.migia.OperationsHub.model.enums.ProductStatus;
import com.migia.OperationsHub.model.enums.Role;
import com.migia.OperationsHub.model.enums.UserStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flow 14 — Reports & Analytics.
 *
 * Verifies:
 *  - 14.1 Sales by date report → 200 OK
 *  - 14.2 Order counts by status report → 200 OK
 *  - 14.3 Top products report → 200 OK
 *  - 14.4 Low stock items report → 200 OK
 *  - 14.5 Payment success/failure stats → 200 OK
 *  - 14.6 Employee access to reports → 200 OK
 *  - 14.7 Unauthenticated request → 401/403
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReportsFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;

    private Organization org;
    private String ownerToken;
    private String employeeToken;

    @BeforeAll
    void setup() throws Exception {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();

        org = organizationRepository.save(Organization.builder()
                .name("Reports Analytics Corp")
                .slug("reports-" + UUID.randomUUID().toString().substring(0, 6))
                .defaultCurrency("USD")
                .status(OrganizationStatus.ACTIVE)
                .ownerInvitationSent(false)
                .build());

        userRepository.save(User.builder()
                .email("reports-owner@flow.com")
                .passwordHash(enc.encode("Pass@1!"))
                .firstName("Report")
                .lastName("Owner")
                .role(Role.ORGANIZATION_OWNER)
                .organization(org)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

        userRepository.save(User.builder()
                .email("reports-emp@flow.com")
                .passwordHash(enc.encode("Pass@1!"))
                .firstName("Report")
                .lastName("Emp")
                .role(Role.EMPLOYEE)
                .organization(org)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

        ownerToken = loginAs("reports-owner@flow.com", "Pass@1!", org.getSlug());
        employeeToken = loginAs("reports-emp@flow.com", "Pass@1!", org.getSlug());

        Product p1 = productRepository.save(Product.builder()
                .organization(org)
                .sku("REP-PROD-01")
                .name("Analytics Widget A")
                .price(BigDecimal.valueOf(50.00))
                .status(ProductStatus.ACTIVE)
                .tags(new ArrayList<>())
                .imageUrls(new ArrayList<>())
                .attributes(new HashMap<>())
                .build());

        Product p2 = productRepository.save(Product.builder()
                .organization(org)
                .sku("REP-PROD-02")
                .name("Analytics Widget B")
                .price(BigDecimal.valueOf(100.00))
                .status(ProductStatus.ACTIVE)
                .tags(new ArrayList<>())
                .imageUrls(new ArrayList<>())
                .attributes(new HashMap<>())
                .build());

        // Low stock item
        inventoryItemRepository.save(InventoryItem.builder()
                .product(p1)
                .quantityOnHand(2)
                .reservedQuantity(0)
                .lowStockThreshold(5)
                .build());

        // High stock item
        inventoryItemRepository.save(InventoryItem.builder()
                .product(p2)
                .quantityOnHand(100)
                .reservedQuantity(0)
                .lowStockThreshold(5)
                .build());

        Customer customer = customerRepository.save(Customer.builder()
                .organization(org)
                .name("Analytics Customer")
                .email("analytics@domain.com")
                .status(CustomerStatus.ACTIVE)
                .build());

        // Seed a completed order with items
        Order completedOrder = Order.builder()
                .organization(org)
                .customer(customer)
                .status(OrderStatus.COMPLETED)
                .totals(BigDecimal.valueOf(150.00))
                .idempotencyKey("rep-order-key-1")
                .build();

        OrderItem item1 = OrderItem.builder()
                .order(completedOrder)
                .product(p1)
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(50.00))
                .unitPrice(BigDecimal.valueOf(50.00))
                .build();

        OrderItem item2 = OrderItem.builder()
                .order(completedOrder)
                .product(p2)
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(100.00))
                .unitPrice(BigDecimal.valueOf(100.00))
                .build();

        completedOrder.setItems(new ArrayList<>(List.of(item1, item2)));
        orderRepository.save(completedOrder);

        // Seed a payment
        paymentRepository.save(Payment.builder()
                .organization(org)
                .order(completedOrder)
                .providerRef("rep-prov-ref-1")
                .status(PaymentStatus.SUCCEEDED)
                .amount(BigDecimal.valueOf(150.00))
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
    @org.junit.jupiter.api.Order(1)
    void tc14_1_salesByDate_returns200() throws Exception {
        mockMvc.perform(get("/api/reports/sales-by-date")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void tc14_2_orderCounts_returns200() throws Exception {
        mockMvc.perform(get("/api/reports/order-counts")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void tc14_3_topProducts_returns200() throws Exception {
        mockMvc.perform(get("/api/reports/top-products?limit=10")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void tc14_4_lowStock_returns200() throws Exception {
        mockMvc.perform(get("/api/reports/low-stock")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].sku").value("REP-PROD-01"));
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    void tc14_5_paymentStats_returns200() throws Exception {
        mockMvc.perform(get("/api/reports/payment-stats")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    void tc14_6_employeeCanAccessReports_returns200() throws Exception {
        mockMvc.perform(get("/api/reports/order-counts")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    void tc14_7_unauthenticatedReports_returns401Or403() throws Exception {
        mockMvc.perform(get("/api/reports/order-counts"))
                .andExpect(status().isForbidden());
    }
}
