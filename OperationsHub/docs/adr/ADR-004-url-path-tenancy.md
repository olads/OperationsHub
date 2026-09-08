# URL-Path Multi-Tenancy — Implementation Plan

## Goal
Introduce a URL-path tenant discriminator (`/{orgSlug}/api/v1/...`) so every request
is scoped to a tenant before reaching any controller. A thread-local `TenantContext`
holds the resolved `organizationId` for the lifetime of each request.

## Architecture

```
Client → /{orgSlug}/api/v1/...
              ↓
    TenantResolutionFilter
     - Extracts orgSlug from path
     - Resolves to Organization (DB / cache)
     - Sets TenantContext.currentTenant = organizationId
     - 404 if slug unknown
              ↓
    Spring Security Filter Chain
              ↓
    Controller (reads TenantContext, never uses orgSlug directly)
              ↓
    Service → WHERE organization_id = TenantContext.getCurrentTenant()
              ↓
    TenantResolutionFilter finally: TenantContext.clear()
```

## Security Properties
| Property                    | Enforcement                                       |
|-----------------------------|---------------------------------------------------|
| Tenant isolation            | TenantContext + WHERE organization_id = ? on all queries |
| Cross-tenant token abuse    | JWT org claim validated against URL slug (future) |
| Audit log separation        | AuditEvent.organizationId set from TenantContext   |
| No org enumeration          | 404 (not 403) returned for unknown slugs           |
| Thread safety               | TenantContext.clear() in filter finally block      |

## Files to Create / Modify

### New Files
| File | Purpose |
|------|---------|
| `tenancy/TenantContext.java` | Thread-local organizationId holder |
| `tenancy/TenantResolutionFilter.java` | Servlet Filter — resolves slug, sets context |
| `exception/TenantNotFoundException.java` | 404 for unknown slugs |
| `test/.../tenancy/TenantResolutionFilterTest.java` | Filter unit tests |

### Modified Files
| File | Change |
|------|--------|
| `docs/adr/ADR-003-multi-tenancy.md` | Update to URL-path strategy |
| `Repository/OrganizationRepository.java` | Add `findBySlug()` |
| `Security/SecurityConfig.java` | Permit `/{orgSlug}/api/v1/auth/**`, register filter |
| `RestControllers/AuthController.java` | Prefix `/{orgSlug}/api/v1/auth` |
| `RestControllers/UserController.java` | Prefix `/{orgSlug}/api/v1`, scope /me to tenant |
| `dto/UserProfileResponse.java` | Remove organizations list, add role field |
| `test/.../controller/AuthControllerTest.java` | Update URL paths |

## Endpoint Map (After)
```
POST  /{orgSlug}/api/v1/auth/register   → 201 (public)
POST  /{orgSlug}/api/v1/auth/login      → 200 + JWT (public, future)
GET   /{orgSlug}/api/v1/me              → 200 profile scoped to tenant (authenticated)
GET   /{orgSlug}/api/v1/customers       → tenant-scoped (future)
```

## Definition of Done
- [ ] `TenantContext` stores and clears organizationId per request thread
- [ ] `TenantResolutionFilter` returns 404 for unknown slugs
- [ ] All controllers use `/{orgSlug}/api/v1` prefix
- [ ] `GET /me` returns role within current tenant only (no org list)
- [ ] Tests pass: filter unit test, updated controller tests
