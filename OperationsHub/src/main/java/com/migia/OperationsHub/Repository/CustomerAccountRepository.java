package com.migia.OperationsHub.Repository;

import com.migia.OperationsHub.model.CustomerAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, UUID> {
    Optional<CustomerAccount> findByEmailAndOrganization_Id(String email, UUID organizationId);
    boolean existsByEmailAndOrganization_Id(String email, UUID organizationId);
}
