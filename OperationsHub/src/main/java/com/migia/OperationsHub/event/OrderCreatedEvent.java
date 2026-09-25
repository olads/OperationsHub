package com.migia.OperationsHub.event;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class OrderCreatedEvent {
    private final UUID orderId;
    private final UUID organizationId;
    private final BigDecimal totalAmount;

    public OrderCreatedEvent(UUID orderId, UUID organizationId, BigDecimal totalAmount) {
        this.orderId = orderId;
        this.organizationId = organizationId;
        this.totalAmount = totalAmount;
    }
}
