package com.migia.OperationsHub.service;

import com.migia.OperationsHub.Repository.OrderRepository;
import com.migia.OperationsHub.Repository.PaymentRepository;
import com.migia.OperationsHub.Repository.ProcessedEventRepository;
import com.migia.OperationsHub.Service.OrderService;
import com.migia.OperationsHub.Service.PaymentService;
import com.migia.OperationsHub.dto.PaymentResponse;
import com.migia.OperationsHub.exception.InvalidPaymentStateException;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import com.migia.OperationsHub.model.Order;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.Payment;
import com.migia.OperationsHub.model.enums.OrderStatus;
import com.migia.OperationsHub.model.enums.PaymentStatus;
import com.migia.OperationsHub.payment.PaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private PaymentProvider paymentProvider;
    @Mock private OrderService orderService;

    @InjectMocks
    private PaymentService paymentService;

    private Organization org;
    private Order order;
    private Payment payment;

    @BeforeEach
    void setup() {
        org = Organization.builder().id(UUID.randomUUID()).defaultCurrency("GBP").build();
        order = Order.builder()
                .id(UUID.randomUUID())
                .organization(org)
                .totals(new BigDecimal("100.00"))
                .status(OrderStatus.PENDING_PAYMENT)
                .build();
        payment = Payment.builder()
                .id(UUID.randomUUID())
                .organization(org)
                .order(order)
                .providerRef("pi_123")
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("100.00"))
                .build();
    }

    @Test
    void createPaymentIntent_success() {
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.empty());
        when(paymentProvider.createIntent(order.getId(), order.getTotals(), "GBP")).thenReturn("pi_123");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PaymentResponse response = paymentService.createPaymentIntent(order.getId());
        assertEquals("pi_123", response.getProviderRef());
        assertEquals(PaymentStatus.PENDING, response.getStatus());
        verify(paymentProvider).createIntent(order.getId(), order.getTotals(), "GBP");
    }

    @Test
    void handleWebhook_success_idempotent() {
        String eventId = "evt_123";
        String providerRef = "pi_123";
        String dedupKey = eventId + "-webhook";

        when(processedEventRepository.existsById(dedupKey)).thenReturn(false);
        when(paymentRepository.findByProviderRef(providerRef)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.handleWebhook(eventId, "payment.succeeded", providerRef);

        assertEquals(PaymentStatus.SUCCEEDED, response.getStatus());
        verify(paymentRepository).save(payment);
        verify(orderService).markPaid(order.getId());
        verify(processedEventRepository).save(any());

        // Test duplicate
        when(processedEventRepository.existsById(dedupKey)).thenReturn(true);
        PaymentResponse duplicateResponse = paymentService.handleWebhook(eventId, "payment.succeeded", providerRef);
        assertEquals(PaymentStatus.SUCCEEDED, duplicateResponse.getStatus());
        // save should not be called again
        verify(paymentRepository, times(1)).save(any());
    }

    @Test
    void handleWebhook_invalidTransition_throws() {
        payment.setStatus(PaymentStatus.SUCCEEDED); // already succeeded
        String eventId = "evt_fail";

        when(processedEventRepository.existsById(anyString())).thenReturn(false);
        when(paymentRepository.findByProviderRef(payment.getProviderRef())).thenReturn(Optional.of(payment));

        assertThrows(InvalidPaymentStateException.class, () -> {
            paymentService.handleWebhook(eventId, "payment.failed", payment.getProviderRef());
        });
    }
}
