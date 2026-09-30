package com.migia.OperationsHub.event;

import com.migia.OperationsHub.config.RabbitMQConfig;
import com.migia.OperationsHub.event.dto.OrderCreatedEventV1;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("[ORDER EVENT] OrderCreated: orderId={}, orgId={}, total={}",
                event.getOrderId(), event.getOrganizationId(), event.getTotalAmount());
        
        OrderCreatedEventV1 dto = new OrderCreatedEventV1(
                UUID.randomUUID().toString(),
                1,
                event.getOrderId(),
                event.getOrganizationId(),
                event.getTotalAmount(),
                Instant.now()
        );
        
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_CREATED, dto);
        log.info("Published OrderCreatedEventV1 for orderId={} to RabbitMQ", event.getOrderId());
    }
}
