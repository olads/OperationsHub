package com.migia.OperationsHub.dto;

import com.migia.OperationsHub.model.Order;
import com.migia.OperationsHub.model.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class OrderResponse {
    private UUID id;
    private OrderStatus status;
    private BigDecimal totals;
    private String idempotencyKey;
    private Instant createdAt;

    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .totals(order.getTotals())
                .idempotencyKey(order.getIdempotencyKey())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
