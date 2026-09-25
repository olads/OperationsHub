package com.migia.OperationsHub.RestControllers;

import com.migia.OperationsHub.Service.ProductService;
import com.migia.OperationsHub.dto.common.PagedResponse;
import com.migia.OperationsHub.dto.product.CreateProductRequest;
import com.migia.OperationsHub.dto.product.ProductResponse;
import com.migia.OperationsHub.dto.product.UpdateProductRequest;
import com.migia.OperationsHub.model.enums.ProductStatus;
import com.migia.OperationsHub.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/{orgSlug}/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_MANAGE_PRODUCTS')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        UUID currentOrgId = TenantContext.getCurrentTenant();
        ProductResponse response = productService.createProduct(request, currentOrgId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_VIEW_PRODUCTS')")
    public ResponseEntity<PagedResponse<ProductResponse>> getProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ProductStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID currentOrgId = TenantContext.getCurrentTenant();
        PagedResponse<ProductResponse> response = productService.searchProducts(q, status, pageable, currentOrgId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_VIEW_PRODUCTS')")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable UUID id) {
        UUID currentOrgId = TenantContext.getCurrentTenant();
        ProductResponse response = productService.getProduct(id, currentOrgId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MANAGE_PRODUCTS')")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        UUID currentOrgId = TenantContext.getCurrentTenant();
        ProductResponse response = productService.updateProduct(id, request, currentOrgId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MANAGE_PRODUCTS')")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        UUID currentOrgId = TenantContext.getCurrentTenant();
        productService.deleteProduct(id, currentOrgId);
        return ResponseEntity.noContent().build();
    }
}
