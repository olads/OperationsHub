package com.migia.OperationsHub.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for OrderCreatedEvent and publishes it downstream (e.g., Kafka, SQS).
 *
 * IMPORTANT: @TransactionalEventListener with phase = AFTER_COMMIT guarantees
 * this handler is ONLY invoked after the originating transaction successfully commits.
 * If the transaction rolls back (e.g., insufficient stock), this handler is never called,
 * satisfying the requirement that OrderCreated is not published for rolled-back transactions.
 */
@Slf4j
@Component
public class OrderEventPublisher {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("[ORDER EVENT] OrderCreated: orderId={}, orgId={}, total={}",
                event.getOrderId(), event.getOrganizationId(), event.getTotalAmount());
        // TODO: publish to message broker (Kafka/SQS) in a real implementation
    }
}
