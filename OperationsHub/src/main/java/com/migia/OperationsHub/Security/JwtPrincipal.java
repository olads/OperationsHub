package com.migia.OperationsHub.Security;

import com.migia.OperationsHub.model.enums.Role;

import java.util.UUID;

/**
 * Lightweight, immutable principal stored in the SecurityContext after
 * stateless JWT authentication.
 *
 * <p>Because all authentication data comes directly from the signed token,
 * this record works uniformly for every principal type: org staff (User),
 * customer portal users (CustomerAccount), platform admins — and any
 * future user type — without touching the database.
 *
 * <p>Usage in controllers / services:
 * <pre>{@code
 *   JwtPrincipal principal = (JwtPrincipal)
 *       SecurityContextHolder.getContext().getAuthentication().getPrincipal();
 *   UUID orgId = principal.organizationId();
 * }</pre>
 *
 * @param email          The authenticated user's email address (JWT subject).
 * @param organizationId The tenant this token was issued for; {@code null} for
 *                       platform admins who are not bound to a single org.
 * @param role           The parsed {@link Role}; {@code null} if the token
 *                       carried an unrecognised role claim.
 */
public record JwtPrincipal(
        String email,
        UUID   organizationId,
        Role   role
) {}
