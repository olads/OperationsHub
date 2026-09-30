package com.migia.OperationsHub.Repository;

import com.migia.OperationsHub.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByOrderId(UUID orderId);

    Optional<Invoice> findByOrganizationIdAndNumber(UUID organizationId, String number);

    boolean existsByOrderId(UUID orderId);
}
