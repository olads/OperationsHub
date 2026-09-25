package com.migia.OperationsHub.Repository;

import com.migia.OperationsHub.model.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByOrganizationIdAndIdempotencyKey(UUID organizationId, String idempotencyKey);
}
