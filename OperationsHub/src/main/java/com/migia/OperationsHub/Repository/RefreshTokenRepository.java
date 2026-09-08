package com.migia.OperationsHub.Repository;

import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.RefreshToken;
import com.migia.OperationsHub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUserAndOrganization(User user, Organization organization);
}
