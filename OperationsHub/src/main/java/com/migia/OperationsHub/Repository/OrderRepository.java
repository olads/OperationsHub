package com.migia.OperationsHub.Repository;

import com.migia.OperationsHub.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByOrganizationIdAndIdempotencyKey(UUID organizationId, String idempotencyKey);
}
