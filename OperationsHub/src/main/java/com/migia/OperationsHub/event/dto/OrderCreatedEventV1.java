package com.migia.OperationsHub.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEventV1 {
    private String eventId;
    private int version = 1;
    private UUID orderId;
    private UUID organizationId;
    private BigDecimal totalAmount;
    private Instant timestamp;
}
