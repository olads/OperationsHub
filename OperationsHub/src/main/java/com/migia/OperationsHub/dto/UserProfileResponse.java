package com.migia.OperationsHub.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Profile response scoped to the current tenant context.
 * Only returns the user's role within the organization they authenticated against.
 * No cross-tenant data is ever returned.
 */
@Getter
@Builder
public class UserProfileResponse {
    private final UUID id;
    private final String email;
    private final String firstName;
    private final String lastName;
    /** The user's role within the current tenant (from TenantContext). */
    private final String role;
}
