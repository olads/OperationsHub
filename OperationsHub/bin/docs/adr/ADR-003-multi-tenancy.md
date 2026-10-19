# ADR 003: Multi-tenancy Strategy

## Status
Accepted (superseded by ADR-004 for routing details)

## Context
OpsHub requires strict data isolation between multiple organizations (tenants). We evaluated:
- Separate databases per tenant (high ops overhead)
- Separate schemas per tenant (complex migration management)
- Shared database with `organization_id` discriminator column (chosen)

## Decision
We use a **Shared Database, Shared Schema** approach combined with **URL-path tenant routing**.

### Tenant Routing
Every API request must include the tenant identifier as the first path segment:
```
POST  /{orgSlug}/api/v1/auth/register
GET   /{orgSlug}/api/v1/me
GET   /{orgSlug}/api/v1/customers
```

### Tenant Resolution
A `TenantResolutionFilter` intercepts every request matching `/{orgSlug}/api/v1/**`:
1. Extracts `orgSlug` from the URL path
2. Resolves it to an `Organization` from the database
3. Stores the `organizationId` in `TenantContext` (thread-local)
4. Returns **404** (not 403) for unknown slugs — prevents tenant enumeration
5. **Always** clears `TenantContext` in a `finally` block after the request

### Data Isolation
- Every tenant-owned table has an `organization_id` column (FK to `organizations`)
- All service-layer queries include `WHERE organization_id = TenantContext.getCurrentTenant()`
- A `Membership` table maps users to organizations with a role
- The same user can belong to multiple organizations — each relationship is independent

### Audit Logs
Each `AuditEvent` stores its own `organizationId` directly (not as a FK). This means:
- Alice's actions in Acme are stored under `organizationId = acme-uuid`
- Alice's actions in Beta are stored under `organizationId = beta-uuid`
- Neither tenant's audit trail is accessible from the other

### JWT (Future)
When JWT authentication is added:
- Login endpoint: `POST /{orgSlug}/api/v1/auth/login`
- JWT will embed an `org` claim containing the `organizationId`
- All subsequent requests will cross-validate the JWT `org` claim against the URL slug
- A token issued for Acme **cannot** be used on `/beta/api/v1/...`

## Consequences
**Pros:**
- Tenant context is established at the HTTP layer before any business logic runs
- Cross-tenant data leakage requires actively bypassing the filter — not just a forgotten WHERE clause
- Clean, RESTful URLs that make the tenant identity explicit
- No HTTP header tricks needed — tenant is in the URL

**Cons:**
- Every organization must have a globally unique slug
- Slug changes would require redirects (rare in practice)
- DB lookup per request for slug resolution (mitigated with caching in future)
