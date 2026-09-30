package com.migia.OperationsHub.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sandbox payment provider for development and testing.
 * Returns deterministic, opaque provider references.
 * Never stores or transmits real payment credentials.
 */
@Slf4j
@Component
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public String createIntent(UUID orderId, BigDecimal amount, String currency) {
        String providerRef = "pi_mock_" + UUID.randomUUID().toString().replace("-", "");
        log.info("[MOCK PAYMENT] Created intent: providerRef={}, orderId={}, amount={} {}",
                providerRef, orderId, amount, currency);
        return providerRef;
    }

    @Override
    public String refund(String providerRef, BigDecimal amount) {
        String refundRef = "rf_mock_" + UUID.randomUUID().toString().replace("-", "");
        log.info("[MOCK PAYMENT] Refund issued: refundRef={}, originalRef={}, amount={}",
                refundRef, providerRef, amount);
        return refundRef;
    }
}
