package com.migia.OperationsHub.Repository;

import com.migia.OperationsHub.model.Customer;
import com.migia.OperationsHub.model.enums.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    boolean existsByOrganization_IdAndEmail(UUID organizationId, String email);

    boolean existsByOrganization_IdAndEmailAndIdNot(UUID organizationId, String email, UUID id);

    @Query("SELECT c FROM Customer c WHERE c.organization.id = :orgId " +
           "AND (:status IS NULL OR c.status = :status) " +
           "AND (:query IS NULL OR :query = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Customer> searchCustomers(
            @Param("orgId") UUID orgId,
            @Param("query") String query,
            @Param("status") CustomerStatus status,
            Pageable pageable
    );
}
