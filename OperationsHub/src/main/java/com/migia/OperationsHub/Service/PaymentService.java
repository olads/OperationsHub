package com.migia.OperationsHub.Service;

import com.migia.OperationsHub.Repository.OrderRepository;
import com.migia.OperationsHub.Repository.PaymentRepository;
import com.migia.OperationsHub.Repository.ProcessedEventRepository;
import com.migia.OperationsHub.dto.PaymentResponse;
import com.migia.OperationsHub.exception.InvalidPaymentStateException;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import com.migia.OperationsHub.model.Order;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.Payment;
import com.migia.OperationsHub.model.ProcessedEvent;
import com.migia.OperationsHub.model.enums.OrderStatus;
import com.migia.OperationsHub.model.enums.PaymentStatus;
import com.migia.OperationsHub.payment.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the payment lifecycle for orders.
 *
 * <h3>State machine</h3>
 * <pre>
 *   INITIATED → PENDING → SUCCEEDED → REFUNDED
 *                       ↘ FAILED
 * </pre>
 *
 * <h3>Key invariants</h3>
 * <ul>
 *   <li>No sensitive credentials are ever persisted — only opaque provider references.</li>
 *   <li>Webhook handling is idempotent via the {@code processed_events} table.</li>
 *   <li>All records are tenant-scoped by {@code organization_id}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final PaymentProvider paymentProvider;
    private final OrderService orderService;

    /** Legal transitions: key = current state, value = allowed next states */
    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = Map.of(
            PaymentStatus.INITIATED, EnumSet.of(PaymentStatus.PENDING),
            PaymentStatus.PENDING,   EnumSet.of(PaymentStatus.SUCCEEDED, PaymentStatus.FAILED),
            PaymentStatus.SUCCEEDED, EnumSet.of(PaymentStatus.REFUNDED),
            PaymentStatus.FAILED,    EnumSet.noneOf(PaymentStatus.class),
            PaymentStatus.REFUNDED,  EnumSet.noneOf(PaymentStatus.class)
    );

    // ──────────────────────────────────────────────────────────────
    // Create Payment Intent
    // ──────────────────────────────────────────────────────────────

    /**
     * Creates a payment intent for an order.
     * The order must be in PENDING_PAYMENT status.
     * Returns a Payment in PENDING status with the provider reference.
     */
    @Transactional
    public PaymentResponse createPaymentIntent(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidPaymentStateException(
                    "Order must be in PENDING_PAYMENT to create a payment intent, current=" + order.getStatus());
        }

        // Check if a payment already exists for this order (idempotent)
        return paymentRepository.findByOrderId(orderId)
                .map(existing -> {
                    log.info("Payment intent already exists for orderId={}, paymentId={}", orderId, existing.getId());
                    return PaymentResponse.from(existing);
                })
                .orElseGet(() -> doCreatePaymentIntent(order));
    }

    private PaymentResponse doCreatePaymentIntent(Order order) {
        String currency = order.getOrganization().getDefaultCurrency();
        if (currency == null || currency.isBlank()) {
            currency = "USD";
        }

        // Call provider — never stores credentials, only the opaque ref
        String providerRef = paymentProvider.createIntent(order.getId(), order.getTotals(), currency);

        Payment payment = Payment.builder()
                .organization(order.getOrganization())
                .order(order)
                .providerRef(providerRef)
                .status(PaymentStatus.PENDING) // skip INITIATED → go straight to PENDING after provider call
                .amount(order.getTotals())
                .build();

        paymentRepository.save(payment);
        log.info("Payment intent created: paymentId={}, providerRef={}, orderId={}",
                payment.getId(), providerRef, order.getId());

        return PaymentResponse.from(payment);
    }

    // ──────────────────────────────────────────────────────────────
    // Webhook — Idempotent Payment Status Update
    // ──────────────────────────────────────────────────────────────

    /**
     * Processes a webhook callback from the payment provider.
     * Idempotent: duplicate calls with the same eventId produce no effect.
     *
     * @param eventId     unique event identifier from the provider
     * @param eventType   "payment.succeeded" or "payment.failed"
     * @param providerRef the provider's payment intent reference
     * @return the updated payment, or the existing payment if already processed
     */
    @Transactional
    public PaymentResponse handleWebhook(String eventId, String eventType, String providerRef) {
        String dedupKey = eventId + "-webhook";

        // Idempotency check — if already processed, return current state
        if (processedEventRepository.existsById(dedupKey)) {
            log.info("Webhook event {} already processed, skipping", eventId);
            Payment existing = paymentRepository.findByProviderRef(providerRef)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found for providerRef: " + providerRef));
            return PaymentResponse.from(existing);
        }

        Payment payment = paymentRepository.findByProviderRef(providerRef)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for providerRef: " + providerRef));

        PaymentStatus targetStatus = resolveWebhookStatus(eventType);
        validateTransition(payment.getStatus(), targetStatus);

        payment.setStatus(targetStatus);
        paymentRepository.save(payment);

        // Cascade status change to the order
        cascadeToOrder(payment, targetStatus);

        // Mark this webhook event as processed
        processedEventRepository.save(new ProcessedEvent(dedupKey, "PaymentWebhook", Instant.now()));

        log.info("Webhook processed: eventId={}, paymentId={}, status={}",
                eventId, payment.getId(), targetStatus);

        return PaymentResponse.from(payment);
    }

    // ──────────────────────────────────────────────────────────────
    // Refund
    // ──────────────────────────────────────────────────────────────

    /**
     * Refunds a succeeded payment.
     * Also transitions the associated order to REFUNDED.
     */
    @Transactional
    public PaymentResponse refundPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        validateTransition(payment.getStatus(), PaymentStatus.REFUNDED);

        // Call the provider to issue the refund
        paymentProvider.refund(payment.getProviderRef(), payment.getAmount());

        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        // Cascade to order
        orderService.refund(payment.getOrder().getId());

        log.info("Payment refunded: paymentId={}, orderId={}", paymentId, payment.getOrder().getId());
        return PaymentResponse.from(payment);
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private PaymentStatus resolveWebhookStatus(String eventType) {
        return switch (eventType) {
            case "payment.succeeded" -> PaymentStatus.SUCCEEDED;
            case "payment.failed"    -> PaymentStatus.FAILED;
            default -> throw new IllegalArgumentException("Unknown webhook event type: " + eventType);
        };
    }

    private void validateTransition(PaymentStatus current, PaymentStatus target) {
        Set<PaymentStatus> allowed = TRANSITIONS.getOrDefault(current, EnumSet.noneOf(PaymentStatus.class));
        if (!allowed.contains(target)) {
            throw new InvalidPaymentStateException(current, target);
        }
    }

    private void cascadeToOrder(Payment payment, PaymentStatus paymentStatus) {
        UUID orderId = payment.getOrder().getId();
        switch (paymentStatus) {
            case SUCCEEDED -> orderService.markPaid(orderId);
            case FAILED    -> orderService.failPayment(orderId);
            default -> { /* no cascade for other statuses */ }
        }
    }
}
