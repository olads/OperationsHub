package com.migia.OperationsHub.Repository;

import com.migia.OperationsHub.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrganizationIdAndProviderRef(UUID organizationId, String providerRef);

    Optional<Payment> findByProviderRef(String providerRef);

    Optional<Payment> findByOrderId(UUID orderId);
}
