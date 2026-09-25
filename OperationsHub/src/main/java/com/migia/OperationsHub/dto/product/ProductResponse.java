package com.migia.OperationsHub.dto.product;

import com.migia.OperationsHub.model.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private UUID id;
    private String sku;
    private String name;
    private String description;
    private BigDecimal price;
    private ProductStatus status;
    private String category;
    private String unit;
    private String currency;
    private java.util.List<String> tags;
    private java.util.List<String> imageUrls;
    private java.util.Map<String, Object> attributes;
    private Instant createdAt;
    private Instant updatedAt;
}
