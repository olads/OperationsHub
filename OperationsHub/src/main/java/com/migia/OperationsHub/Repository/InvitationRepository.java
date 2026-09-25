package com.migia.OperationsHub.Repository;

import com.migia.OperationsHub.model.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    Optional<Invitation> findByToken(String token);
    List<Invitation> findByOrganization_Id(UUID organizationId);
    Optional<Invitation> findByEmailAndOrganization_IdAndAcceptedFalse(String email, UUID organizationId);
}
