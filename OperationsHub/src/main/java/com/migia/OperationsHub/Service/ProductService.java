package com.migia.OperationsHub.Service;

import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.ProductRepository;
import com.migia.OperationsHub.dto.common.PagedResponse;
import com.migia.OperationsHub.dto.product.CreateProductRequest;
import com.migia.OperationsHub.dto.product.ProductResponse;
import com.migia.OperationsHub.dto.product.UpdateProductRequest;
import com.migia.OperationsHub.exception.ConflictException;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.Product;
import com.migia.OperationsHub.model.enums.ProductStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request, UUID orgId) {
        if (productRepository.existsByOrganization_IdAndSku(orgId, request.getSku())) {
            throw new ConflictException("Product with SKU '" + request.getSku() + "' already exists in this organization");
        }

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        Product product = Product.builder()
                .organization(org)
                .sku(request.getSku())
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .status(ProductStatus.ACTIVE)
                .category(request.getCategory())
                .unit(request.getUnit())
                .currency(request.getCurrency())
                .tags(request.getTags() != null ? request.getTags() : new java.util.ArrayList<>())
                .imageUrls(request.getImageUrls() != null ? request.getImageUrls() : new java.util.ArrayList<>())
                .attributes(request.getAttributes() != null ? request.getAttributes() : new java.util.HashMap<>())
                .build();

        Product saved = productRepository.save(product);
        return toProductResponse(saved);
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, UpdateProductRequest request, UUID orgId) {
        Product product = productRepository.findByIdAndOrganization_Id(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (productRepository.existsByOrganization_IdAndSkuAndIdNot(orgId, request.getSku(), id)) {
            throw new ConflictException("Product with SKU '" + request.getSku() + "' already exists in this organization");
        }

        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        if (request.getStatus() != null) {
            product.setStatus(request.getStatus());
        }
        if (request.getCategory() != null) product.setCategory(request.getCategory());
        if (request.getUnit() != null) product.setUnit(request.getUnit());
        if (request.getCurrency() != null) product.setCurrency(request.getCurrency());
        if (request.getTags() != null) product.setTags(request.getTags());
        if (request.getImageUrls() != null) product.setImageUrls(request.getImageUrls());
        if (request.getAttributes() != null) product.setAttributes(request.getAttributes());

        Product updated = productRepository.save(product);
        return toProductResponse(updated);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID id, UUID orgId) {
        Product product = productRepository.findByIdAndOrganization_Id(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return toProductResponse(product);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductResponse> searchProducts(String query, ProductStatus status, Pageable pageable, UUID orgId) {
        Page<Product> page = productRepository.searchProducts(orgId, query, status, pageable);
        return PagedResponse.from(page, this::toProductResponse);
    }

    @Transactional
    public void deleteProduct(UUID id, UUID orgId) {
        Product product = productRepository.findByIdAndOrganization_Id(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        productRepository.delete(product);
    }

    private ProductResponse toProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .status(product.getStatus())
                .category(product.getCategory())
                .unit(product.getUnit())
                .currency(product.getCurrency())
                .tags(product.getTags())
                .imageUrls(product.getImageUrls())
                .attributes(product.getAttributes())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
