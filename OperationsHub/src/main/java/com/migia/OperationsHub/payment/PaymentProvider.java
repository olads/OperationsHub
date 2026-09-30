package com.migia.OperationsHub.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Abstraction over an external payment gateway (Stripe, PayPal, etc.).
 * Implementations must NEVER store sensitive credentials — only opaque
 * provider references are persisted in the Payment table.
 */
public interface PaymentProvider {

    /**
     * Creates a payment intent with the external provider.
     *
     * @param orderId the order being paid
     * @param amount  the amount to charge
     * @param currency the ISO 4217 currency code (e.g. "USD")
     * @return an opaque provider reference (e.g. Stripe PaymentIntent ID)
     */
    String createIntent(UUID orderId, BigDecimal amount, String currency);

    /**
     * Issues a refund through the external provider.
     *
     * @param providerRef the original payment's provider reference
     * @param amount      the amount to refund
     * @return the provider's refund reference
     */
    String refund(String providerRef, BigDecimal amount);
}
