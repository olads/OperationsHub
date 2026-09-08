package com.migia.OperationsHub.Service;

import com.migia.OperationsHub.Repository.RefreshTokenRepository;
import com.migia.OperationsHub.exception.ConflictException;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.RefreshToken;
import com.migia.OperationsHub.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration-days}")
    private long refreshExpirationDays;

    @Transactional
    public RefreshToken createRefreshToken(User user, Organization organization) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .organization(organization)
                .expiresAt(Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS))
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public RefreshToken validate(String token, UUID currentOrgId) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new ConflictException("Invalid refresh token"));

        if (refreshToken.isRevoked()) {
            throw new ConflictException("Refresh token was revoked");
        }

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new ConflictException("Refresh token was expired");
        }

        if (!refreshToken.getOrganization().getId().equals(currentOrgId)) {
            throw new ConflictException("Refresh token belongs to a different organization");
        }

        return refreshToken;
    }

    @Transactional
    public void revoke(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    @Transactional
    public void revokeAllForUser(User user, Organization organization) {
        refreshTokenRepository.deleteByUserAndOrganization(user, organization);
    }
}
