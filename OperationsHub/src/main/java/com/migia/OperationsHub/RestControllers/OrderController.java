package com.migia.OperationsHub.RestControllers;

import com.migia.OperationsHub.Service.OrderService;
import com.migia.OperationsHub.dto.CreateOrderRequest;
import com.migia.OperationsHub.dto.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Creates a new order. The Idempotency-Key header is required.
     * Duplicate requests with the same key return the original order.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Organization-Id") UUID organizationId,
            @Valid @RequestBody CreateOrderRequest request) {

        OrderResponse response = orderService.createOrder(organizationId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** DRAFT → PENDING_PAYMENT */
    @PostMapping("/{orderId}/submit")
    public ResponseEntity<OrderResponse> submitForPayment(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.submitForPayment(orderId));
    }

    /** PENDING_PAYMENT → PAID */
    @PostMapping("/{orderId}/pay")
    public ResponseEntity<OrderResponse> markPaid(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.markPaid(orderId));
    }

    /** PAID → PROCESSING */
    @PostMapping("/{orderId}/process")
    public ResponseEntity<OrderResponse> startProcessing(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.startProcessing(orderId));
    }

    /** PROCESSING → COMPLETED */
    @PostMapping("/{orderId}/complete")
    public ResponseEntity<OrderResponse> complete(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.complete(orderId));
    }

    /** DRAFT | PENDING_PAYMENT → CANCELLED (releases inventory reservation) */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancel(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.cancel(orderId));
    }

    /** PENDING_PAYMENT → PAYMENT_FAILED */
    @PostMapping("/{orderId}/fail-payment")
    public ResponseEntity<OrderResponse> failPayment(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.failPayment(orderId));
    }

    /** PAID | COMPLETED → REFUNDED */
    @PostMapping("/{orderId}/refund")
    public ResponseEntity<OrderResponse> refund(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.refund(orderId));
    }
}
