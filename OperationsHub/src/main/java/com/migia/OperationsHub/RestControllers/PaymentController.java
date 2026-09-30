package com.migia.OperationsHub.RestControllers;

import com.migia.OperationsHub.Service.PaymentService;
import com.migia.OperationsHub.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Creates a payment intent for the given order.
     * The order must be in PENDING_PAYMENT status.
     * Idempotent — returns the existing payment if one already exists.
     */
    @PostMapping("/intent/{orderId}")
    public ResponseEntity<PaymentResponse> createPaymentIntent(@PathVariable UUID orderId) {
        PaymentResponse response = paymentService.createPaymentIntent(orderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Refunds a succeeded payment.
     * Only payments in SUCCEEDED status can be refunded.
     */
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<PaymentResponse> refundPayment(@PathVariable UUID paymentId) {
        PaymentResponse response = paymentService.refundPayment(paymentId);
        return ResponseEntity.ok(response);
    }
}
