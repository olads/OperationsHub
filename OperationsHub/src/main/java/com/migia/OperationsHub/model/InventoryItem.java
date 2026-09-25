package com.migia.OperationsHub.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantityOnHand = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer reservedQuantity = 0;

    @Version
    private Long version;

    @Column(nullable = false)
    @Builder.Default
    private Integer lowStockThreshold = 0;

    public void adjustStock(int quantity) {
        if (this.quantityOnHand + quantity < 0) {
            throw new com.migia.OperationsHub.exception.InsufficientStockException("Adjustment would result in negative stock.");
        }
        this.quantityOnHand += quantity;
    }

    public void reserveStock(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Cannot reserve negative quantity.");
        }
        if (getAvailableQuantity() < quantity) {
            throw new com.migia.OperationsHub.exception.InsufficientStockException("Insufficient stock to reserve " + quantity);
        }
        this.reservedQuantity += quantity;
    }

    public int getAvailableQuantity() {
        return this.quantityOnHand - this.reservedQuantity;
    }

    /**
     * Releases a reservation (e.g., on order cancellation).
     * Reduces reservedQuantity but does not change quantityOnHand.
     */
    public void releaseReservation(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Cannot release negative quantity.");
        }
        if (this.reservedQuantity < quantity) {
            throw new com.migia.OperationsHub.exception.InsufficientStockException(
                    "Cannot release " + quantity + " — only " + this.reservedQuantity + " reserved.");
        }
        this.reservedQuantity -= quantity;
    }

    public boolean isLowStock() {
        return getAvailableQuantity() <= this.lowStockThreshold;
    }
}
