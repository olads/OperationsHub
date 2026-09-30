package com.migia.OperationsHub.event;

import com.migia.OperationsHub.Repository.ProcessedEventRepository;
import com.migia.OperationsHub.config.RabbitMQConfig;
import com.migia.OperationsHub.event.dto.OrderCreatedEventV1;
import com.migia.OperationsHub.model.ProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderNotificationConsumer {

    private final ProcessedEventRepository processedEventRepository;

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void consumeNotification(OrderCreatedEventV1 event) {
        String eventId = event.getEventId();
        String consumerId = eventId + "-notification";

        if (processedEventRepository.existsById(consumerId)) {
            log.info("Event {} already processed by NotificationConsumer, skipping.", eventId);
            return;
        }

        try {
            log.info("Processing notification for order {}", event.getOrderId());
            // Simulate processing
            // ...

            processedEventRepository.save(new ProcessedEvent(consumerId, "NotificationConsumer", Instant.now()));
            log.info("Notification processed for order {}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to process notification for event {}", eventId, e);
            throw e; // throw exception to trigger Spring retry and eventually DLQ
        }
    }
}
