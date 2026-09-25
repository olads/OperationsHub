package com.migia.OperationsHub.Repository;

import com.migia.OperationsHub.model.Product;
import com.migia.OperationsHub.model.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    boolean existsByOrganization_IdAndSku(UUID organizationId, String sku);

    boolean existsByOrganization_IdAndSkuAndIdNot(UUID organizationId, String sku, UUID id);

    @Query("SELECT p FROM Product p WHERE p.organization.id = :orgId " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:query IS NULL OR :query = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Product> searchProducts(
            @Param("orgId") UUID orgId,
            @Param("query") String query,
            @Param("status") ProductStatus status,
            Pageable pageable
    );
}
