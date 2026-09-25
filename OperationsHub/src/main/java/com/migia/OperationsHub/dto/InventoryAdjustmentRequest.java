package com.migia.OperationsHub.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class InventoryAdjustmentRequest {
    @NotNull
    @Positive
    private Integer quantity;
    
    @NotNull
    private String reason;
    
    private String referenceId;
}
