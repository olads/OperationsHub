# ADR 003: Multi-tenancy Strategy

## Status
Accepted

## Context
OpsHub requires strict data isolation between multiple organizations (tenants). We evaluated options including separate databases, separate schemas, and shared database with discriminator columns.

## Decision
We will use a **Shared Database, Shared Schema** approach with a discriminator column (`organization_id`) on every tenant-owned table. 
- A `Membership` table explicitly maps a `User` to an `Organization`.
- All tenant-owned records will include an `organization_id` foreign key.
- Primary keys will be `UUID` for exposed resources to deter enumeration and IDOR attacks.

## Consequences
- **Pros:** Easiest to maintain schemas and run migrations.
- **Cons:** Risk of cross-tenant data leaks if queries forget to append `WHERE organization_id = ?`.
