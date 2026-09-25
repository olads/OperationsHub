package com.migia.OperationsHub.Service;

import com.migia.OperationsHub.Repository.*;
import com.migia.OperationsHub.dto.CreateOrderRequest;
import com.migia.OperationsHub.dto.OrderResponse;
import com.migia.OperationsHub.event.OrderCreatedEvent;
import com.migia.OperationsHub.exception.InvalidOrderStateException;
import com.migia.OperationsHub.model.*;
import com.migia.OperationsHub.model.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Creates a new order transactionally. The operation is idempotent:
     * if an IdempotencyRecord already exists for (orgId, idempotencyKey),
     * the original order is returned without creating a duplicate.
     *
     * Price is snapshotted from the product at creation time.
     * Inventory is reserved atomically within the same transaction.
     * OrderCreatedEvent is published AFTER_COMMIT only (never on rollback).
     */
    @Transactional
    public OrderResponse createOrder(UUID organizationId, String idempotencyKey, CreateOrderRequest request) {
        // 1. Idempotency check — return existing result if key already processed
        return idempotencyRecordRepository
                .findByOrganizationIdAndIdempotencyKey(organizationId, idempotencyKey)
                .map(record -> {
                    log.info("Idempotent replay for key={}, returning orderId={}", idempotencyKey, record.getOrderId());
                    Order existing = orderRepository.findById(record.getOrderId())
                            .orElseThrow(() -> new IllegalStateException("Idempotency record references missing order"));
                    return OrderResponse.from(existing);
                })
                .orElseGet(() -> doCreateOrder(organizationId, idempotencyKey, request));
    }

    private OrderResponse doCreateOrder(UUID organizationId, String idempotencyKey, CreateOrderRequest request) {
        // 2. Load customer (validates it belongs to the org)
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + request.getCustomerId()));

        // 3. Build order with snapshotted prices and server-calculated totals
        Order order = Order.builder()
                .organization(Organization.builder().id(organizationId).build()) // reference by ID
                .customer(customer)
                .status(OrderStatus.DRAFT)
                .totals(BigDecimal.ZERO)
                .idempotencyKey(idempotencyKey)
                .build();
        orderRepository.save(order); // save first to get the ID for FK on OrderItem

        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderRequest.OrderLineItem lineRequest : request.getItems()) {
            Product product = productRepository.findById(lineRequest.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + lineRequest.getProductId()));

            // Snapshot price — insulate the order from future price changes
            BigDecimal unitPrice = product.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(lineRequest.getQuantity()));

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(lineRequest.getQuantity())
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .build();
            orderItemRepository.save(item);

            // 4. Reserve inventory atomically in this transaction
            inventoryService.reserveStock(product.getId(), lineRequest.getQuantity(), order.getId().toString());

            total = total.add(lineTotal);
        }

        // 5. Persist final totals
        order.setTotals(total);
        orderRepository.save(order);

        // 6. Persist idempotency record
        IdempotencyRecord record = IdempotencyRecord.builder()
                .organizationId(organizationId)
                .idempotencyKey(idempotencyKey)
                .orderId(order.getId())
                .responseStatus(201)
                .build();
        idempotencyRecordRepository.save(record);

        // 7. Publish event — fires AFTER_COMMIT via @TransactionalEventListener
        eventPublisher.publishEvent(new OrderCreatedEvent(order.getId(), organizationId, total));

        log.info("Order created: orderId={}, total={}", order.getId(), total);
        return OrderResponse.from(order);
    }

    // ──────────────────────────────────────────────────────────────
    // Lifecycle Transitions
    // ──────────────────────────────────────────────────────────────

    @Transactional
    public OrderResponse submitForPayment(UUID orderId) {
        return transition(orderId, OrderStatus.DRAFT, OrderStatus.PENDING_PAYMENT);
    }

    @Transactional
    public OrderResponse markPaid(UUID orderId) {
        return transition(orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID);
    }

    @Transactional
    public OrderResponse startProcessing(UUID orderId) {
        return transition(orderId, OrderStatus.PAID, OrderStatus.PROCESSING);
    }

    @Transactional
    public OrderResponse complete(UUID orderId) {
        return transition(orderId, OrderStatus.PROCESSING, OrderStatus.COMPLETED);
    }

    @Transactional
    public OrderResponse cancel(UUID orderId) {
        Order order = loadOrder(orderId);
        if (order.getStatus() != OrderStatus.DRAFT && order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidOrderStateException(
                    "Order can only be cancelled from DRAFT or PENDING_PAYMENT, current=" + order.getStatus());
        }

        // Release reserved inventory when cancelling
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : items) {
            inventoryService.releaseReservation(item.getProduct().getId(), item.getQuantity(), orderId.toString());
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse failPayment(UUID orderId) {
        return transition(orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED);
    }

    @Transactional
    public OrderResponse refund(UUID orderId) {
        Order order = loadOrder(orderId);
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.COMPLETED) {
            throw new InvalidOrderStateException(
                    "Order can only be refunded from PAID or COMPLETED, current=" + order.getStatus());
        }
        order.setStatus(OrderStatus.REFUNDED);
        orderRepository.save(order);
        return OrderResponse.from(order);
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private OrderResponse transition(UUID orderId, OrderStatus required, OrderStatus next) {
        Order order = loadOrder(orderId);
        if (order.getStatus() != required) {
            throw new InvalidOrderStateException(order.getStatus(), required);
        }
        order.setStatus(next);
        orderRepository.save(order);
        return OrderResponse.from(order);
    }

    private Order loadOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }
}
