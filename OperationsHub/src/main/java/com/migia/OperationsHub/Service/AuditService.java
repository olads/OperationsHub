package com.migia.OperationsHub.Service;

import com.migia.OperationsHub.Repository.AuditEventRepository;
import com.migia.OperationsHub.model.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(UUID organizationId, UUID actorUserId, String action, String entityType, String entityId, String metadata) {
        try {
            AuditEvent event = AuditEvent.builder()
                    .organizationId(organizationId)
                    .actorUserId(actorUserId) // Can be null if system action
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .timestamp(Instant.now())
                    .metadata(metadata)
                    .build();
            auditEventRepository.save(event);
            log.debug("Audit event saved: {} on {} {}", action, entityType, entityId);
        } catch (Exception e) {
            // Append-only audit shouldn't fail the main transaction
            log.error("Failed to save audit event: {} on {} {}", action, entityType, entityId, e);
        }
    }
}
