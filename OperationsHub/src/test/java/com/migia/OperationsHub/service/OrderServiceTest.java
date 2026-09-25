package com.migia.OperationsHub.service;

import com.migia.OperationsHub.Repository.*;
import com.migia.OperationsHub.Service.InventoryService;
import com.migia.OperationsHub.Service.OrderService;
import com.migia.OperationsHub.dto.CreateOrderRequest;
import com.migia.OperationsHub.dto.OrderResponse;
import com.migia.OperationsHub.event.OrderCreatedEvent;
import com.migia.OperationsHub.exception.InsufficientStockException;
import com.migia.OperationsHub.exception.InvalidOrderStateException;
import com.migia.OperationsHub.model.*;
import com.migia.OperationsHub.model.enums.OrderStatus;
import com.migia.OperationsHub.model.enums.OrganizationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(OrderServiceTest.EventCaptor.class)
class OrderServiceTest {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
    @Autowired private EventCaptor eventCaptor;

    private UUID orgId;
    private UUID customerId;
    private UUID productId;

    @BeforeEach
    void setup() {
        eventCaptor.clear();

        Organization org = organizationRepository.save(Organization.builder()
                .name("Test Org")
                .slug("test-org-" + UUID.randomUUID())
                .status(OrganizationStatus.ACTIVE)
                .build());
        orgId = org.getId();

        Customer customer = customerRepository.save(Customer.builder()
                .organization(org)
                .name("Test Customer")
                .email("test-" + UUID.randomUUID() + "@example.com")
                .build());
        customerId = customer.getId();

        Product product = productRepository.save(Product.builder()
                .organization(org)
                .name("Widget")
                .sku("WGT-" + UUID.randomUUID())
                .price(new BigDecimal("25.00"))
                .build());
        productId = product.getId();

        inventoryItemRepository.save(InventoryItem.builder()
                .product(product)
                .quantityOnHand(10)
                .build());
    }

    // ──────────────────────────────────────────────────────────────
    // Create Order — Happy path
    // ──────────────────────────────────────────────────────────────

    @Test
    void testCreateOrder_success() {
        CreateOrderRequest req = buildRequest(productId, 3);
        String key = UUID.randomUUID().toString();

        OrderResponse response = orderService.createOrder(orgId, key, req);

        assertNotNull(response.getId());
        assertEquals(OrderStatus.DRAFT, response.getStatus());
        // 3 × £25.00 = £75.00
        assertEquals(0, new BigDecimal("75.00").compareTo(response.getTotals()));
        assertEquals(key, response.getIdempotencyKey());

        // Verify inventory was reserved
        InventoryItem item = inventoryItemRepository.findByProductId(productId).orElseThrow();
        assertEquals(3, item.getReservedQuantity());
        assertEquals(10, item.getQuantityOnHand()); // on-hand unchanged — only reserved
    }

    // ──────────────────────────────────────────────────────────────
    // Idempotency
    // ──────────────────────────────────────────────────────────────

    @Test
    void testCreateOrder_idempotency_returnsSameOrder() {
        String key = UUID.randomUUID().toString();
        CreateOrderRequest req = buildRequest(productId, 1);

        OrderResponse first = orderService.createOrder(orgId, key, req);
        OrderResponse second = orderService.createOrder(orgId, key, req);

        assertEquals(first.getId(), second.getId());
        assertEquals(first.getTotals(), second.getTotals());

        // Only one idempotency record should exist
        long count = idempotencyRecordRepository.findByOrganizationIdAndIdempotencyKey(orgId, key)
                .stream().count();
        assertEquals(1, count);

        // Inventory should only have been reserved once
        InventoryItem item = inventoryItemRepository.findByProductId(productId).orElseThrow();
        assertEquals(1, item.getReservedQuantity());
    }

    // ──────────────────────────────────────────────────────────────
    // Lifecycle Transitions — Happy path
    // ──────────────────────────────────────────────────────────────

    @Test
    void testFullLifecycle() {
        OrderResponse order = orderService.createOrder(orgId, UUID.randomUUID().toString(), buildRequest(productId, 1));
        UUID id = order.getId();

        assertEquals(OrderStatus.PENDING_PAYMENT, orderService.submitForPayment(id).getStatus());
        assertEquals(OrderStatus.PAID,            orderService.markPaid(id).getStatus());
        assertEquals(OrderStatus.PROCESSING,      orderService.startProcessing(id).getStatus());
        assertEquals(OrderStatus.COMPLETED,       orderService.complete(id).getStatus());
        assertEquals(OrderStatus.COMPLETED,       orderRepository.findById(id).orElseThrow().getStatus());
    }

    // ──────────────────────────────────────────────────────────────
    // Invalid Transitions
    // ──────────────────────────────────────────────────────────────

    @Test
    void testInvalidTransition_draftToCompleted_throws() {
        OrderResponse order = orderService.createOrder(orgId, UUID.randomUUID().toString(), buildRequest(productId, 1));

        assertThrows(InvalidOrderStateException.class, () -> orderService.complete(order.getId()));
    }

    @Test
    void testInvalidTransition_cancelFromCompleted_throws() {
        OrderResponse order = orderService.createOrder(orgId, UUID.randomUUID().toString(), buildRequest(productId, 1));
        UUID id = order.getId();
        orderService.submitForPayment(id);
        orderService.markPaid(id);
        orderService.startProcessing(id);
        orderService.complete(id);

        assertThrows(InvalidOrderStateException.class, () -> orderService.cancel(id));
    }

    // ──────────────────────────────────────────────────────────────
    // Cancel releases inventory
    // ──────────────────────────────────────────────────────────────

    @Test
    void testCancel_releasesInventoryReservation() {
        OrderResponse order = orderService.createOrder(orgId, UUID.randomUUID().toString(), buildRequest(productId, 4));
        InventoryItem before = inventoryItemRepository.findByProductId(productId).orElseThrow();
        assertEquals(4, before.getReservedQuantity());

        orderService.cancel(order.getId());

        InventoryItem after = inventoryItemRepository.findByProductId(productId).orElseThrow();
        assertEquals(0, after.getReservedQuantity());
    }

    // ──────────────────────────────────────────────────────────────
    // Insufficient stock rolls back — OrderCreatedEvent NOT published
    // ──────────────────────────────────────────────────────────────

    @Test
    void testOrderCreatedEvent_notPublishedOnRollback() {
        // Request 999 items but only 10 in stock
        CreateOrderRequest req = buildRequest(productId, 999);
        String key = UUID.randomUUID().toString();

        assertThrows(InsufficientStockException.class,
                () -> orderService.createOrder(orgId, key, req));

        // Transaction rolled back — no event should have been captured
        assertTrue(eventCaptor.getEvents().isEmpty(),
                "OrderCreatedEvent must not be published when transaction rolls back");
    }

    // ──────────────────────────────────────────────────────────────
    // Helper
    // ──────────────────────────────────────────────────────────────

    private CreateOrderRequest buildRequest(UUID productId, int qty) {
        CreateOrderRequest.OrderLineItem line = new CreateOrderRequest.OrderLineItem();
        line.setProductId(productId);
        line.setQuantity(qty);

        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerId(customerId);
        req.setItems(List.of(line));
        return req;
    }

    // ──────────────────────────────────────────────────────────────
    // Event Captor bean — captures events in the test context
    // ──────────────────────────────────────────────────────────────

   @org.springframework.boot.test.context.TestComponent
    static class EventCaptor {
        private final List<OrderCreatedEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        public void onOrderCreated(OrderCreatedEvent event) {
            events.add(event);
        }

        List<OrderCreatedEvent> getEvents() { return events; }
        void clear() { events.clear(); }
    }
}
