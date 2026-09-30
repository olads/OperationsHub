package com.migia.OperationsHub.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Represents an incoming webhook callback from the payment provider.
 * The provider sends this when a payment intent's status changes.
 *
 * Fields mirror what Stripe/PayPal webhooks typically contain.
 * No sensitive credentials are included — only opaque references.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookPayload {
    /** Unique event ID from the provider — used for idempotent dedup */
    private String eventId;

    /** The type of event, e.g. "payment.succeeded", "payment.failed" */
    private String eventType;

    /** The provider's reference to the payment intent */
    private String providerRef;
}
