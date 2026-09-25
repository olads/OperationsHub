package com.migia.OperationsHub.RestControllers;

import com.migia.OperationsHub.Service.InventoryService;
import com.migia.OperationsHub.dto.InventoryAdjustmentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/{productId}/add")
    public ResponseEntity<Void> addStock(
            @PathVariable UUID productId,
            @Valid @RequestBody InventoryAdjustmentRequest request) {
        
        inventoryService.addStock(productId, request.getQuantity(), request.getReason(), request.getReferenceId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{productId}/remove")
    public ResponseEntity<Void> removeStock(
            @PathVariable UUID productId,
            @Valid @RequestBody InventoryAdjustmentRequest request) {
        
        inventoryService.removeStock(productId, request.getQuantity(), request.getReason(), request.getReferenceId());
        return ResponseEntity.ok().build();
    }
}
