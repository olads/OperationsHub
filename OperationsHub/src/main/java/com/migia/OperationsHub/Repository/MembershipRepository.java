package com.migia.OperationsHub.Repository;

import com.migia.OperationsHub.model.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, Long> {
    List<Membership> findByUser_Email(String email);
    Optional<Membership> findByUser_EmailAndOrganization_Id(String email, UUID organizationId);
}
