package com.migia.OperationsHub.RestControllers;

import com.migia.OperationsHub.Service.PaymentService;
import com.migia.OperationsHub.dto.PaymentResponse;
import com.migia.OperationsHub.dto.WebhookPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives payment provider webhooks.
 *
 * <h3>Idempotency</h3>
 * Duplicate webhook deliveries (same {@code eventId}) produce no side effects.
 * The {@code processed_events} table prevents duplicate processing.
 *
 * <h3>Security</h3>
 * This endpoint is unauthenticated (permitAll in SecurityConfig) because
 * it receives callbacks from external payment providers. In production,
 * verify the provider's webhook signature before processing.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentService paymentService;

    @PostMapping("/payments")
    public ResponseEntity<PaymentResponse> handlePaymentWebhook(@RequestBody WebhookPayload payload) {
        log.info("Received payment webhook: eventId={}, eventType={}, providerRef={}",
                payload.getEventId(), payload.getEventType(), payload.getProviderRef());

        PaymentResponse response = paymentService.handleWebhook(
                payload.getEventId(),
                payload.getEventType(),
                payload.getProviderRef()
        );

        return ResponseEntity.ok(response);
    }
}
