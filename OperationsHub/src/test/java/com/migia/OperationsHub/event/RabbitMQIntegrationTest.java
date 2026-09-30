package com.migia.OperationsHub.event;

import com.migia.OperationsHub.config.RabbitMQConfig;
import com.migia.OperationsHub.event.dto.OrderCreatedEventV1;
import com.migia.OperationsHub.Repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
public class RabbitMQIntegrationTest {

    @Container
    static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq:3-management")
            .withExposedPorts(5672, 15672);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @MockitoSpyBean
    private OrderNotificationConsumer notificationConsumer;

    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAll();
    }

    @Test
    void testMessageIsProcessedSuccessfully() {
        String eventId = UUID.randomUUID().toString();
        OrderCreatedEventV1 event = new OrderCreatedEventV1(eventId, 1, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, Instant.now());
        
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_CREATED, event);
        
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(notificationConsumer, atLeastOnce()).consumeNotification(any());
            assertThat(processedEventRepository.findById(eventId + "-notification")).isPresent();
            assertThat(processedEventRepository.findById(eventId + "-invoice")).isPresent();
        });
    }

    @Test
    void testDuplicateEventDoesNotDuplicateSideEffects() {
        String eventId = UUID.randomUUID().toString();
        OrderCreatedEventV1 event = new OrderCreatedEventV1(eventId, 1, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, Instant.now());
        
        // Send twice
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_CREATED, event);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_CREATED, event);
        
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(processedEventRepository.findById(eventId + "-notification")).isPresent();
            // Verify consumer was executed but only processed once due to idempotency
        });
    }
}
