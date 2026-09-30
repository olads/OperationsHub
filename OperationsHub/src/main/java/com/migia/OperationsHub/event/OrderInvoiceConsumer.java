package com.migia.OperationsHub.event;

import com.migia.OperationsHub.Repository.ProcessedEventRepository;
import com.migia.OperationsHub.Service.InvoiceService;
import com.migia.OperationsHub.config.RabbitMQConfig;
import com.migia.OperationsHub.event.dto.OrderCreatedEventV1;
import com.migia.OperationsHub.model.ProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Consumes OrderCreatedEvent messages and generates invoices asynchronously.
 *
 * <h3>Idempotency</h3>
 * Uses the {@code processed_events} table to ensure each event is processed
 * exactly once, even if RabbitMQ redelivers the message.
 *
 * <h3>Historical stability</h3>
 * The invoice total is snapshotted from the order at creation time
 * (inside {@link InvoiceService#createInvoice}), ensuring historical values
 * remain stable even if the order is later modified.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderInvoiceConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final InvoiceService invoiceService;

    @RabbitListener(queues = RabbitMQConfig.INVOICE_QUEUE)
    public void consumeInvoice(OrderCreatedEventV1 event) {
        String eventId = event.getEventId();
        String consumerId = eventId + "-invoice";

        if (processedEventRepository.existsById(consumerId)) {
            log.info("Event {} already processed by InvoiceConsumer, skipping.", eventId);
            return;
        }

        try {
            log.info("Processing invoice for order {}", event.getOrderId());

            // Create the invoice — snapshots order totals for historical stability
            invoiceService.createInvoice(event.getOrderId(), event.getOrganizationId());

            processedEventRepository.save(new ProcessedEvent(consumerId, "InvoiceConsumer", Instant.now()));
            log.info("Invoice processed for order {}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to process invoice for event {}", eventId, e);
            throw e; // throw exception to trigger Spring retry and eventually DLQ
        }
    }
}
