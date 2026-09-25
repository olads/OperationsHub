package com.migia.OperationsHub.service;

import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.ProductRepository;
import com.migia.OperationsHub.Service.ProductService;
import com.migia.OperationsHub.dto.common.PagedResponse;
import com.migia.OperationsHub.dto.product.CreateProductRequest;
import com.migia.OperationsHub.dto.product.ProductResponse;
import com.migia.OperationsHub.dto.product.UpdateProductRequest;
import com.migia.OperationsHub.exception.ConflictException;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.Product;
import com.migia.OperationsHub.model.enums.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProductService}.
 * All dependencies are mocked via Mockito — no Spring context or database needed.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private OrganizationRepository organizationRepository;

    @InjectMocks
    private ProductService productService;

    private UUID orgId;
    private Organization organization;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        organization = Organization.builder()
                .id(orgId)
                .name("Acme Corp")
                .slug("acme-corp")
                .build();
    }

    /**
     * Test Case: Create Product — Success
     * Verifies that a valid product creation request results in:
     * - The product being saved to the repository
     * - The response reflecting correct SKU, name, price, and ACTIVE status
     */
    @Test
    void createProduct_success() {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("SKU-001")
                .name("Widget")
                .description("A high quality widget")
                .price(new BigDecimal("19.99"))
                .category("Hardware")
                .unit("piece")
                .currency("USD")
                .attributes(Map.of("color", "red", "weight_kg", 0.5))
                .build();

        when(productRepository.existsByOrganization_IdAndSku(orgId, "SKU-001")).thenReturn(false);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        ProductResponse response = productService.createProduct(request, orgId);

        assertThat(response).isNotNull();
        assertThat(response.getSku()).isEqualTo("SKU-001");
        assertThat(response.getName()).isEqualTo("Widget");
        assertThat(response.getPrice()).isEqualByComparingTo(new BigDecimal("19.99"));
        assertThat(response.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(response.getCategory()).isEqualTo("Hardware");
        assertThat(response.getAttributes()).containsEntry("color", "red");
        verify(productRepository).save(any(Product.class));
    }

    /**
     * Test Case: Create Product — Duplicate SKU Within Same Org
     * Verifies that attempting to create a product with an already-used SKU
     * in the same organization throws a ConflictException and never calls save().
     */
    @Test
    void createProduct_duplicateSku_throwsConflictException() {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("SKU-DUP")
                .name("Widget")
                .price(new BigDecimal("9.99"))
                .build();

        when(productRepository.existsByOrganization_IdAndSku(orgId, "SKU-DUP")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request, orgId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists in this organization");

        verify(productRepository, never()).save(any());
    }

    /**
     * Test Case: Update Product — Success with New Fields
     * Verifies that an update request modifies the product's fields including
     * flexible attributes (JSONB map) and saves correctly.
     */
    @Test
    void updateProduct_success() {
        UUID productId = UUID.randomUUID();
        Product existing = Product.builder()
                .id(productId)
                .organization(organization)
                .sku("SKU-OLD")
                .name("Old Widget")
                .price(new BigDecimal("10.00"))
                .status(ProductStatus.ACTIVE)
                .build();

        UpdateProductRequest updateReq = UpdateProductRequest.builder()
                .sku("SKU-NEW")
                .name("Updated Widget")
                .description("New desc")
                .price(new BigDecimal("25.50"))
                .status(ProductStatus.ACTIVE)
                .category("Electronics")
                .attributes(Map.of("voltage", "220V"))
                .build();

        when(productRepository.findByIdAndOrganization_Id(productId, orgId))
                .thenReturn(Optional.of(existing));
        when(productRepository.existsByOrganization_IdAndSkuAndIdNot(orgId, "SKU-NEW", productId))
                .thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.updateProduct(productId, updateReq, orgId);

        assertThat(response.getSku()).isEqualTo("SKU-NEW");
        assertThat(response.getName()).isEqualTo("Updated Widget");
        assertThat(response.getPrice()).isEqualByComparingTo(new BigDecimal("25.50"));
        assertThat(response.getCategory()).isEqualTo("Electronics");
        assertThat(response.getAttributes()).containsEntry("voltage", "220V");
    }

    /**
     * Test Case: Update Product — SKU Taken by Another Product
     * Verifies that updating a product to use an SKU already taken by a different
     * product in the same org throws a ConflictException.
     */
    @Test
    void updateProduct_duplicateSku_throwsConflictException() {
        UUID productId = UUID.randomUUID();
        Product existing = Product.builder()
                .id(productId)
                .organization(organization)
                .sku("SKU-OLD")
                .name("Old Widget")
                .build();

        UpdateProductRequest updateReq = UpdateProductRequest.builder()
                .sku("SKU-TAKEN")
                .name("Widget")
                .price(new BigDecimal("15.00"))
                .build();

        when(productRepository.findByIdAndOrganization_Id(productId, orgId))
                .thenReturn(Optional.of(existing));
        when(productRepository.existsByOrganization_IdAndSkuAndIdNot(orgId, "SKU-TAKEN", productId))
                .thenReturn(true);

        assertThatThrownBy(() -> productService.updateProduct(productId, updateReq, orgId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists in this organization");
    }

    /**
     * Test Case: Get Product — Not Found
     * Verifies that requesting a product that doesn't exist (or belongs to a
     * different org) throws ResourceNotFoundException.
     */
    @Test
    void getProduct_notFound_throwsResourceNotFoundException() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findByIdAndOrganization_Id(productId, orgId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(productId, orgId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");
    }

    /**
     * Test Case: Delete Product — Success
     * Verifies that deleteProduct finds the product and calls repository.delete().
     */
    @Test
    void deleteProduct_success() {
        UUID productId = UUID.randomUUID();
        Product existing = Product.builder()
                .id(productId)
                .organization(organization)
                .sku("SKU-DEL")
                .build();

        when(productRepository.findByIdAndOrganization_Id(productId, orgId))
                .thenReturn(Optional.of(existing));

        productService.deleteProduct(productId, orgId);

        verify(productRepository).delete(existing);
    }

    /**
     * Test Case: Search Products — Returns Paginated Results
     * Verifies that searchProducts correctly delegates to the repository and
     * wraps results in a PagedResponse with correct metadata.
     */
    @Test
    void searchProducts_returnsPagedResponse() {
        Product p1 = Product.builder().id(UUID.randomUUID()).sku("S1").name("Item 1")
                .price(BigDecimal.TEN).status(ProductStatus.ACTIVE).build();
        Product p2 = Product.builder().id(UUID.randomUUID()).sku("S2").name("Item 2")
                .price(BigDecimal.ONE).status(ProductStatus.ACTIVE).build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of(p1, p2), pageable, 2);

        when(productRepository.searchProducts(orgId, "Item", ProductStatus.ACTIVE, pageable))
                .thenReturn(page);

        PagedResponse<ProductResponse> response =
                productService.searchProducts("Item", ProductStatus.ACTIVE, pageable, orgId);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getTotalPages()).isEqualTo(1);
    }
}
