package com.migia.OperationsHub.Repository;

import com.migia.OperationsHub.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailAndOrganization_Id(String email, UUID organizationId);
    boolean existsByEmailAndOrganization_Id(String email, UUID organizationId);
    
    // For platform admin where organization is null
    Optional<User> findByEmailAndOrganizationIsNull(String email);
    boolean existsByEmailAndOrganizationIsNull(String email);
    
    // For listing employees in an org
    Page<User> findByOrganization_Id(UUID organizationId, Pageable pageable);
}
