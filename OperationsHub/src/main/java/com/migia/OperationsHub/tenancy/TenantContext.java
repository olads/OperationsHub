package com.migia.OperationsHub.tenancy;

import java.util.UUID;

/**
 * Thread-local holder for the current tenant's organizationId.
 * Set by TenantResolutionFilter at the start of every request
 * and ALWAYS cleared in the finally block after the request completes.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setCurrentTenant(UUID organizationId) {
        CURRENT_TENANT.set(organizationId);
    }

    public static UUID getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    /**
     * Must be called in the filter's finally block to prevent
     * organizationId leaking across requests on reused threads.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
