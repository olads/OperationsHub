package com.migia.OperationsHub.Service;

import com.migia.OperationsHub.Repository.InventoryItemRepository;
import com.migia.OperationsHub.Repository.StockMovementRepository;
import com.migia.OperationsHub.model.InventoryItem;
import com.migia.OperationsHub.model.StockMovement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final StockMovementRepository stockMovementRepository;

    @Transactional
    public void addStock(UUID productId, int quantity, String reason, String referenceId) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive for adding stock");
        }
        
        InventoryItem item = inventoryItemRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found for product: " + productId));

        item.adjustStock(quantity);
        inventoryItemRepository.save(item);

        StockMovement movement = StockMovement.builder()
                .inventoryItem(item)
                .quantityChanged(quantity)
                .reason(reason)
                .referenceId(referenceId)
                .createdAt(Instant.now())
                .build();
        stockMovementRepository.save(movement);
    }

    @Transactional
    public void removeStock(UUID productId, int quantity, String reason, String referenceId) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive for removing stock");
        }
        
        InventoryItem item = inventoryItemRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found for product: " + productId));

        item.adjustStock(-quantity); // Negative quantity reduces stock
        inventoryItemRepository.save(item);

        StockMovement movement = StockMovement.builder()
                .inventoryItem(item)
                .quantityChanged(-quantity)
                .reason(reason)
                .referenceId(referenceId)
                .createdAt(Instant.now())
                .build();
        stockMovementRepository.save(movement);
    }

    @Transactional
    public void reserveStock(UUID productId, int quantity, String orderId) {
        InventoryItem item = inventoryItemRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found for product: " + productId));

        item.reserveStock(quantity);
        inventoryItemRepository.save(item);

        StockMovement movement = StockMovement.builder()
                .inventoryItem(item)
                .quantityChanged(quantity)
                .reason("RESERVATION")
                .referenceId(orderId)
                .createdAt(Instant.now())
                .build();
        stockMovementRepository.save(movement);
    }

    @Transactional
    public void releaseReservation(UUID productId, int quantity, String orderId) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive to release reservation");
        }

        InventoryItem item = inventoryItemRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found for product: " + productId));

        item.releaseReservation(quantity);
        inventoryItemRepository.save(item);

        StockMovement movement = StockMovement.builder()
                .inventoryItem(item)
                .quantityChanged(-quantity)
                .reason("RESERVATION_RELEASED")
                .referenceId(orderId)
                .createdAt(Instant.now())
                .build();
        stockMovementRepository.save(movement);
    }
}
