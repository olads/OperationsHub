package com.migia.OperationsHub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateOrderRequest {

    @NotNull
    private UUID customerId;

    @NotEmpty
    @Valid
    private List<OrderLineItem> items;

    @Data
    public static class OrderLineItem {
        @NotNull
        private UUID productId;

        @NotNull
        private Integer quantity;
    }
}
