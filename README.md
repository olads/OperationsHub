# OpsHub

### Production-Style Multi-Tenant SaaS Operations Platform

OpsHub is a production-style backend platform designed to simulate the engineering challenges involved in building, deploying, operating, and maintaining a real multi-tenant SaaS application.

The platform allows multiple independent organizations to manage customers, products, inventory, orders, invoices, payments, notifications, reporting, users, roles, and audit history through a secure versioned REST API.

The project is intentionally designed to go beyond basic CRUD development.

It focuses on:

* Multi-tenant architecture
* Authentication and authorization
* Transactional business operations
* Concurrency control
* Idempotency
* Asynchronous processing
* Message retries and dead-letter queues
* Caching
* Database performance
* API design
* Automated testing
* CI/CD
* Cloud deployment
* Observability
* Security testing
* Failure simulation
* Performance testing
* Backup and recovery
* Production troubleshooting

> **Project status:** Active development
> **Development model:** 14-day engineering sprint
> **Architecture:** Modular monolith
> **Primary language:** Java
> **Primary framework:** Spring Boot

---

# Table of Contents

* [1. Project Overview](#1-project-overview)
* [2. Why OpsHub Exists](#2-why-opshub-exists)
* [3. Product Description](#3-product-description)
* [4. Core Features](#4-core-features)
* [5. User Roles](#5-user-roles)
* [6. Architecture](#6-architecture)
* [7. Technology Stack](#7-technology-stack)
* [8. Domain Modules](#8-domain-modules)
* [9. Multi-Tenancy](#9-multi-tenancy)
* [10. Authentication and Authorization](#10-authentication-and-authorization)
* [11. Order Processing](#11-order-processing)
* [12. Inventory Concurrency](#12-inventory-concurrency)
* [13. Idempotency](#13-idempotency)
* [14. Asynchronous Processing](#14-asynchronous-processing)
* [15. Redis](#15-redis)
* [16. Database Design](#16-database-design)
* [17. API Design](#17-api-design)
* [18. Error Handling](#18-error-handling)
* [19. Audit Logging](#19-audit-logging)
* [20. Testing Strategy](#20-testing-strategy)
* [21. Observability](#21-observability)
* [22. Security](#22-security)
* [23. Failure Engineering](#23-failure-engineering)
* [24. Performance Engineering](#24-performance-engineering)
* [25. Local Development](#25-local-development)
* [26. Docker](#26-docker)
* [27. Environment Variables](#27-environment-variables)
* [28. Running the Application](#28-running-the-application)
* [29. Running Tests](#29-running-tests)
* [30. API Documentation](#30-api-documentation)
* [31. CI/CD](#31-cicd)
* [32. Production Deployment](#32-production-deployment)
* [33. Backup and Recovery](#33-backup-and-recovery)
* [34. Project Documentation](#34-project-documentation)
* [35. Architecture Decision Records](#35-architecture-decision-records)
* [36. 14-Day Development Plan](#36-14-day-development-plan)
* [37. Engineering Evidence](#37-engineering-evidence)
* [38. Known Limitations](#38-known-limitations)
* [39. Future Improvements](#39-future-improvements)
* [40. Project Philosophy](#40-project-philosophy)

---

# 1. Project Overview

OpsHub is a multi-tenant SaaS operations backend.

A single deployed instance serves multiple independent organizations.

Each organization can manage:

* Employees and members
* Customers
* Products
* Inventory
* Orders
* Invoices
* Payments
* Notifications
* Reports
* Audit history

The backend exposes a versioned REST API and uses asynchronous processing for operations that do not need to block the user's HTTP request.

The system is designed around the following principle:

> Build the system as though another engineering team will inherit and operate it.

That means correctness is only one part of the project.

The project also considers:

* How the application is deployed
* How failures are detected
* How failures are recovered
* How requests are traced
* How database performance is measured
* How concurrent operations are handled
* How tenant isolation is verified
* How background jobs are retried
* How deployments are automated
* How backups are restored
* How production incidents are investigated

---

# 2. Why OpsHub Exists

Many backend projects demonstrate that an application can perform CRUD operations.

OpsHub is intended to demonstrate something different.

The project explores what happens when a backend has to operate under realistic conditions.

Examples include:

### What happens when two users purchase the final inventory unit simultaneously?

The application must prevent overselling.

### What happens when the client sends the same order request twice?

The application must not create two orders.

### What happens when RabbitMQ goes down?

The application should fail predictably and recover without corrupting business data.

### What happens when a background worker crashes?

The message should be retried or recovered according to the messaging strategy.

### What happens when a user changes an organization ID in a request?

The application must prevent cross-tenant access.

### What happens when Redis becomes unavailable?

Core database-backed operations should continue where possible.

### What happens when a database query becomes slow as data grows?

The problem should be measurable through profiling and query analysis rather than guessed at.

These scenarios form the foundation of the engineering work behind OpsHub.

---

# 3. Product Description

OpsHub can be thought of as the backend of a small business management SaaS product.

A typical organization might use OpsHub to:

1. Register an account.
2. Create an organization.
3. Invite employees.
4. Assign roles and permissions.
5. Add customers.
6. Create products.
7. Manage inventory.
8. Create orders.
9. Reserve inventory.
10. Process payments.
11. Generate invoices.
12. Send notifications.
13. Monitor sales.
14. Review audit activity.

The same application can serve many organizations while maintaining strict data isolation between them.

---

# 4. Core Features

## Identity and Access

* User registration
* Login
* Access tokens
* Refresh tokens
* Logout
* Refresh-token revocation
* Password hashing
* Protected endpoints
* Organization membership

## Organizations

* Organization creation
* Organization management
* Organization status
* Organization memberships
* User invitations
* Invitation expiry
* Active organization context

## Authorization

* Roles
* Permissions
* Permission-based authorization
* Owner-only operations
* Employee restrictions
* Tenant-aware authorization

## Customers

* Create customers
* Update customers
* Deactivate customers
* Search customers
* Pagination
* Sorting
* Tenant isolation

## Product Catalog

* Product creation
* Product updates
* Product deactivation
* SKU management
* Product pricing
* Categories
* Search
* Filtering
* Pagination

## Inventory

* Stock levels
* Stock adjustments
* Inventory movement history
* Inventory reservation
* Low-stock detection
* Concurrency protection
* Negative-stock prevention

## Orders

* Order creation
* Order items
* Server-side price calculation
* Inventory reservation
* Order lifecycle
* Idempotent order creation
* Order cancellation
* Payment states
* Audit events

## Payments

* Payment intents
* Payment status
* Mock/sandbox payment provider
* Webhook processing
* Idempotent webhooks
* Refunds
* Payment failure handling

## Invoices

* Invoice generation
* Invoice numbers
* Invoice status
* Historical snapshots
* Asynchronous invoice generation

## Notifications

* Invitations
* Order notifications
* Payment notifications
* Invoice notifications
* Low-stock notifications
* Retry handling
* Dead-letter handling

## Reporting

* Sales by date
* Order counts
* Top products
* Revenue by product/category
* Low-stock reporting
* Customer order totals
* Payment success/failure reporting

## Auditing

* Authentication events
* Membership changes
* Product changes
* Customer changes
* Inventory adjustments
* Order events
* Payment events
* Organization state changes

---

# 5. User Roles

OpsHub uses permission-based authorization.

The initial roles are:

| Role               | Description                                       |
| ------------------ | ------------------------------------------------- |
| Platform Admin     | Operates the SaaS platform                        |
| Organization Owner | Owns and manages an organization                  |
| Organization Admin | Manages day-to-day organization operations        |
| Employee           | Performs explicitly permitted business operations |
| Customer           | Represents a business customer                    |

Roles are mapped to permissions rather than embedding authorization rules directly into business logic.

Example permissions:

```text
organization:read
organization:update

member:read
member:invite
member:update
member:remove

customer:read
customer:create
customer:update
customer:delete

product:read
product:create
product:update
product:delete

inventory:read
inventory:adjust

order:read
order:create
order:update
order:cancel

invoice:read
invoice:create

payment:read
payment:refund

report:read
audit:read
```

This allows authorization rules to evolve without scattering hard-coded role checks throughout the application.

---

# 6. Architecture

OpsHub starts as a modular monolith.

The goal is to maintain strong internal module boundaries without introducing distributed-system complexity prematurely.

```text
                         ┌─────────────────┐
                         │     Client      │
                         └────────┬────────┘
                                  │
                                  ▼
                         ┌─────────────────┐
                         │ Nginx / HTTPS   │
                         └────────┬────────┘
                                  │
                                  ▼
                    ┌─────────────────────────┐
                    │     Spring Boot API     │
                    │                         │
                    │ Auth                    │
                    │ Organizations           │
                    │ Customers               │
                    │ Products                │
                    │ Inventory               │
                    │ Orders                  │
                    │ Payments                │
                    │ Invoices                │
                    │ Notifications           │
                    │ Audit                   │
                    │ Reporting               │
                    └───────┬─────┬─────┬─────┘
                            │     │     │
                 ┌──────────┘     │     └──────────────┐
                 ▼                ▼                    ▼
          ┌─────────────┐  ┌─────────────┐     ┌──────────────┐
          │ PostgreSQL  │  │    Redis    │     │  RabbitMQ    │
          │             │  │             │     │              │
          │ Source of   │  │ Cache /     │     │ Async events │
          │ truth       │  │ rate limit  │     │ + workers    │
          └─────────────┘  └─────────────┘     └──────────────┘
                                                       │
                                                       ▼
                                                Background Workers

                    ┌────────────────────────────────────┐
                    │         Observability               │
                    │                                    │
                    │ Spring Actuator → Prometheus       │
                    │ Application Logs → Log aggregation │
                    │ Prometheus → Grafana               │
                    └────────────────────────────────────┘
```

---

# 7. Technology Stack

## Backend

* Java 21+
* Spring Boot
* Spring MVC
* Spring Security
* Spring Data JPA
* Hibernate
* Maven

## Database

* PostgreSQL
* Flyway

## Messaging

* RabbitMQ

## Caching

* Redis

## Testing

* JUnit 5
* Mockito
* Spring Boot Test
* MockMvc / REST Assured
* Testcontainers

## Infrastructure

* Docker
* Docker Compose
* Nginx
* Linux
* Cloud VPS

## CI/CD

* GitHub Actions
* Docker image builds
* Automated testing
* Deployment automation

## Observability

* Spring Boot Actuator
* Prometheus
* Grafana
* Structured logging

## Performance

* k6

---

# 8. Domain Modules

The project follows a feature-oriented modular structure.

```text
src/main/java/com/example/opshub/

├── auth/
├── organization/
├── customer/
├── product/
├── inventory/
├── order/
├── invoice/
├── payment/
├── notification/
├── audit/
└── common/
```

Each module should contain its own:

* Controllers
* Services
* Domain models
* Repositories
* DTOs
* Validation
* Tests

Shared functionality belongs in `common` only when it is genuinely cross-cutting.

The goal is to avoid creating a giant package containing unrelated business logic.

---

# 9. Multi-Tenancy

Multi-tenancy is one of the most important architectural requirements in OpsHub.

A single application serves multiple organizations.

Tenant-owned resources must never leak across organization boundaries.

## Core rule

Every tenant-owned resource must either contain `organization_id` directly or be reachable through a tenant-owned parent.

Example:

```text
Organization
     │
     ├── Customer
     │
     ├── Product
     │
     ├── Inventory
     │
     ├── Order
     │
     ├── Invoice
     │
     └── Payment
```

## Tenant isolation requirements

The application must:

1. Establish a trusted organization context from authenticated membership.
2. Never trust a client-supplied organization ID by itself.
3. Scope tenant queries to the authenticated organization.
4. Prevent resource IDs from being sufficient to access tenant resources.
5. Test cross-tenant access explicitly.
6. Separate platform-level operations from tenant operations.

Example attack:

```http
GET /api/v1/customers/123
```

If customer `123` belongs to organization B, a user from organization A must receive an authorization/not-found response rather than customer data.

Tenant isolation is treated as a security invariant rather than a convenience feature.

---

# 10. Authentication and Authorization

Authentication establishes:

> Who is making this request?

Authorization establishes:

> Is this user allowed to perform this operation in this organization?

OpsHub separates these concerns.

## Authentication flow

```text
Client
  │
  │ login
  ▼
Spring Security
  │
  ├── validate credentials
  │
  ├── generate access token
  │
  └── issue refresh credential
  │
  ▼
Authenticated API requests
```

Access tokens should be short-lived.

Refresh credentials should support revocation.

Passwords are never stored in plaintext.

---

# 11. Order Processing

Order creation is one of the most important transactional workflows.

A simplified flow is:

```text
Create Order
     │
     ▼
Authenticate user
     │
     ▼
Resolve organization
     │
     ▼
Validate customer
     │
     ▼
Validate products
     │
     ▼
Calculate totals server-side
     │
     ▼
Reserve inventory
     │
     ▼
Persist order + order items
     │
     ▼
Commit transaction
     │
     ▼
Publish OrderCreated
     │
     ▼
Async workers
 ┌───┴──────────────┐
 ▼                  ▼
Invoice           Notification
```

The client is never trusted to determine the final order total.

Product prices are snapshot into order items so historical orders do not change when the product's current price changes.

---

# 12. Inventory Concurrency

Inventory is a concurrency-sensitive domain.

Consider:

```text
Available stock = 1

User A → Buy 1
User B → Buy 1
```

Both requests may arrive at almost exactly the same time.

The system must ensure:

```text
Successful purchases ≤ Available inventory
```

OpsHub therefore uses an explicit concurrency strategy, such as optimistic locking/versioning or another deliberately selected database-level strategy.

The choice and trade-offs are documented in:

```text
docs/adr/
```

The system also includes a concurrent test attempting to purchase the final inventory unit from multiple requests.

---

# 13. Idempotency

Distributed systems frequently encounter duplicate requests.

A client may retry because:

* The network timed out.
* The response was lost.
* The client crashed.
* A proxy retried the request.
* A user clicked twice.

Order creation therefore supports an `Idempotency-Key`.

Example:

```http
POST /api/v1/orders
Idempotency-Key: 7b2e4f...
```

If the same logical request is submitted again using the same key, OpsHub returns the original result rather than creating a second order.

This protects against duplicate business operations.

The same principle is applied to payment webhooks and asynchronous consumers where appropriate.

---

# 14. Asynchronous Processing

Not every operation needs to happen during an HTTP request.

OpsHub uses RabbitMQ for non-critical asynchronous workflows.

Initial event types include:

```text
OrganizationCreated
MemberInvited
OrderCreated
OrderPaid
OrderCancelled
InvoiceGenerated
PaymentFailed
InventoryLow
```

## Message processing

```text
Application
    │
    ▼
Exchange
    │
    ▼
Queue
    │
    ▼
Consumer
    │
    ├── success → acknowledge
    │
    └── failure
          │
          ▼
       retry
          │
          ▼
      max attempts
          │
          ▼
         DLQ
```

Consumers must be idempotent.

Messages should use explicit event schemas rather than serializing internal JPA entities.

Events are published only after the corresponding database transaction has successfully committed.

---

# 15. Redis

Redis is not treated as the source of truth.

PostgreSQL remains authoritative for business data.

Redis is used only where measurement demonstrates a useful caching or infrastructure use case.

Potential uses include:

* Product lookups
* Customer lookups
* Rate limiting
* Short-lived application state

Caching requirements include:

* TTLs
* Explicit invalidation
* Consistent key naming
* Hit/miss measurement
* Graceful degradation

If Redis fails, core PostgreSQL-backed operations should continue where practical.

---

# 16. Database Design

PostgreSQL is the system of record.

Database design emphasizes correctness first and optimization based on evidence.

Requirements include:

* Foreign keys
* Unique constraints
* Appropriate indexes
* Database migrations
* Transaction boundaries
* Optimistic locking where appropriate
* UTC timestamps
* Historical snapshots
* Audit retention rules

Example indexing candidates:

```text
users(email)

organizations(slug)

memberships(user_id, organization_id)

customers(organization_id, email)

products(organization_id, sku)

orders(organization_id, created_at)

orders(organization_id, customer_id)

order_items(order_id)

payments(organization_id, provider_reference)

audit_events(organization_id, created_at)
```

Indexes are not added blindly.

When performance problems are discovered, query plans are inspected and benchmark results are captured before and after optimization.

---

# 17. API Design

All public endpoints use versioning.

Example:

```text
/api/v1/auth/login
/api/v1/customers
/api/v1/products
/api/v1/orders
/api/v1/payments
```

The API uses JSON.

API design standards include:

* Consistent HTTP status codes
* Request validation
* DTO-based contracts
* Pagination
* Filtering
* Sorting
* Stable error schemas
* Correlation/request IDs
* OpenAPI documentation
* Safe error messages
* No internal stack traces in production responses

---

# 18. Error Handling

Errors use a consistent structure.

Example:

```json
{
  "timestamp": "2026-09-06T15:30:00Z",
  "status": 409,
  "code": "ORDER_ALREADY_EXISTS",
  "message": "An order already exists for the supplied idempotency key.",
  "path": "/api/v1/orders",
  "requestId": "a6a2b9f1..."
}
```

Internal implementation details should not be exposed to clients.

For example, production responses should never contain:

```text
org.postgresql.util.PSQLException
at com.example...
```

Instead, the API returns a stable application-level error.

---

# 19. Audit Logging

Important business and security actions are recorded as append-only audit events.

An audit event contains information such as:

```text
actorUserId
organizationId
action
entityType
entityId
timestamp
metadata
```

Examples:

```text
USER_LOGIN
MEMBER_INVITED
MEMBER_ROLE_CHANGED
PRODUCT_CREATED
PRODUCT_DEACTIVATED
INVENTORY_ADJUSTED
ORDER_CREATED
ORDER_CANCELLED
PAYMENT_SUCCEEDED
PAYMENT_REFUNDED
ORGANIZATION_SUSPENDED
```

Audit records are not intended to replace application logs.

Logs answer:

> What happened inside the application?

Audit events answer:

> What important business/security action happened, and who performed it?

---

# 20. Testing Strategy

Testing is treated as part of implementation rather than final cleanup.

## Unit Tests

Test:

* Business rules
* Calculations
* State transitions
* Validation
* Authorization rules
* Inventory logic
* Idempotency logic

Tools:

```text
JUnit 5
Mockito
```

## Integration Tests

Test:

* Repositories
* Database constraints
* Transactions
* PostgreSQL behavior
* Persistence mappings

Tools:

```text
Spring Boot Test
Testcontainers PostgreSQL
```

## API Tests

Test:

* HTTP contracts
* Authentication
* Authorization
* Validation
* Error responses
* Pagination
* Tenant isolation

Tools:

```text
MockMvc
REST Assured
```

## Messaging Tests

Test:

* Event publication
* Consumers
* Retry behavior
* Dead-letter queues
* Idempotent processing

Tools:

```text
Testcontainers RabbitMQ
```

## Security Tests

Critical scenarios include:

```text
Cross-tenant access
IDOR/BOLA
Privilege escalation
Owner-only operations
Invalid credentials
Expired refresh credentials
Revoked refresh credentials
Suspended organization access
```

## Performance Tests

Test:

* Throughput
* Latency
* Concurrent requests
* Database behavior
* Reporting queries
* Product searches

Tool:

```text
k6
```

---

# 21. Observability

A production system must provide enough information to answer:

> Is the application healthy?

and:

> If it is not healthy, why?

OpsHub uses:

```text
Spring Boot Actuator
Prometheus
Grafana
Structured Logging
Request/Correlation IDs
```

Important metrics include:

### HTTP

```text
request count
error rate
p50 latency
p95 latency
p99 latency
```

### JVM

```text
heap usage
GC activity
thread usage
```

### Database

```text
connection pool usage
query latency
connection failures
```

### Redis

```text
cache hits
cache misses
connection failures
```

### RabbitMQ

```text
queue depth
consumer failures
retry count
DLQ messages
processing latency
```

### Business

```text
orders/minute
payment success rate
payment failure rate
inventory warnings
```

---

# 22. Security

Security is considered throughout the application rather than added at the end.

Key controls include:

* HTTPS in production
* Strong password hashing
* Short-lived access tokens
* Refresh-token revocation
* Permission-based authorization
* Tenant isolation
* Input validation
* Rate limiting
* Security headers
* Secret management
* No credentials in logs
* Dependency vulnerability scanning
* Audit logging
* IDOR/BOLA testing

The project also maintains a threat model documenting known risks and limitations.

---

# 23. Failure Engineering

OpsHub deliberately introduces failures.

The purpose is not to make the application look unreliable.

The purpose is to understand how it behaves when dependencies or components fail.

Planned experiments include:

## Experiment 1 — Redis outage

Stop Redis while the application is running.

Questions:

* Does the API crash?
* Which endpoints fail?
* Does PostgreSQL-backed functionality continue?
* Are errors understandable?
* Can Redis reconnect automatically?

## Experiment 2 — RabbitMQ outage

Stop RabbitMQ.

Questions:

* What happens to event publication?
* Which operations remain available?
* Are failed messages recoverable?
* Are users given appropriate responses?

## Experiment 3 — Worker crash

Terminate a worker while processing a message.

Questions:

* Is the message lost?
* Does RabbitMQ redeliver it?
* Can the consumer safely process it again?

## Experiment 4 — Database failure

Simulate database connectivity failure.

Questions:

* Does the health endpoint detect it?
* Are requests failing safely?
* Are useful logs produced?
* Can the application recover after PostgreSQL returns?

## Experiment 5 — Duplicate request

Send the same order request multiple times.

Expected:

```text
One business operation.
```

## Experiment 6 — Concurrent inventory purchase

Attempt to purchase the final unit concurrently.

Expected:

```text
No overselling.
```

## Experiment 7 — Cross-tenant attack

Attempt to access another organization's resource by changing an ID.

Expected:

```text
Access denied / resource unavailable.
```

Each significant experiment should produce an incident report under:

```text
docs/incidents/
```

---

# 24. Performance Engineering

Performance optimization follows:

```text
Measure
   ↓
Identify bottleneck
   ↓
Form hypothesis
   ↓
Implement change
   ↓
Measure again
   ↓
Compare
```

The project avoids premature optimization.

A benchmark should include realistic data.

Example initial dataset:

```text
10,000 customers
50,000 orders
100,000 products
100 concurrent virtual users
5–10 minute sustained test
```

Metrics:

```text
p50 latency
p95 latency
p99 latency
throughput
error rate
database utilization
```

Potential optimization experiments include:

* Database indexing
* Query optimization
* N+1 query elimination
* Pagination
* Connection pool tuning
* Redis caching

Every meaningful optimization should have before/after measurements.

---

# 25. Local Development

## Prerequisites

Install:

```text
Java 21+
Maven
Docker
Docker Compose
Git
```

Optional:

```text
k6
```

Verify:

```bash
java -version
mvn -version
docker --version
docker compose version
git --version
```

---

# 26. Docker

The local environment is designed to run through Docker Compose.

Expected services:

```text
opshub-api
postgres
redis
rabbitmq
prometheus
grafana
```

The goal is for a developer to clone the repository and start the complete development environment with minimal setup.

```bash
docker compose up --build
```

Stop services:

```bash
docker compose down
```

Stop and remove persistent development volumes:

```bash
docker compose down -v
```

> Do not use `docker compose down -v` against a production environment.

---

# 27. Environment Variables

Production secrets must never be committed to Git.

Example configuration:

```env
SPRING_PROFILES_ACTIVE=local

DB_HOST=localhost
DB_PORT=5432
DB_NAME=opshub
DB_USERNAME=opshub
DB_PASSWORD=change-me

REDIS_HOST=localhost
REDIS_PORT=6379

RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=opshub
RABBITMQ_PASSWORD=change-me

JWT_SECRET=change-me
JWT_ACCESS_TOKEN_EXPIRATION=900
JWT_REFRESH_TOKEN_EXPIRATION=604800
```

Production secrets should be supplied through environment configuration or an appropriate secret-management mechanism.

---

# 28. Running the Application

Clone the repository:

```bash
git clone <repository-url>
cd opshub
```

Start infrastructure:

```bash
docker compose up -d postgres redis rabbitmq
```

Run the application:

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

Or build the application:

```bash
./mvnw clean package
```

Run the generated JAR:

```bash
java -jar target/opshub-*.jar
```

---

# 29. Running Tests

Run all tests:

```bash
./mvnw test
```

Run a clean build:

```bash
./mvnw clean verify
```

Integration tests use Testcontainers where appropriate.

The goal is that the test suite can run against real PostgreSQL/RabbitMQ containers rather than relying exclusively on mocks.

---

# 30. API Documentation

The API is documented using OpenAPI/Swagger.

Expected development endpoint:

```text
/swagger-ui/index.html
```

The API is versioned under:

```text
/api/v1
```

Example endpoints:

```text
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
GET    /api/v1/me

GET    /api/v1/organizations
GET    /api/v1/organizations/{id}

POST   /api/v1/customers
GET    /api/v1/customers
GET    /api/v1/customers/{id}
PATCH  /api/v1/customers/{id}
DELETE /api/v1/customers/{id}

POST   /api/v1/products
GET    /api/v1/products

POST   /api/v1/orders
GET    /api/v1/orders/{id}

POST   /api/v1/orders/{id}/payments
GET    /api/v1/payments/{id}

POST   /api/v1/payments/webhooks/{provider}
POST   /api/v1/payments/{id}/refund
```

The exact API contract should always be considered authoritative over this README.

---

# 31. CI/CD

GitHub Actions automates the engineering pipeline.

## Pull Request Pipeline

Expected stages:

```text
Checkout
   ↓
Compile
   ↓
Unit Tests
   ↓
Integration Tests
   ↓
Static Analysis / Formatting
   ↓
Dependency Security Scan
   ↓
Docker Build
   ↓
API Smoke Tests
```

## Main Branch

Expected deployment flow:

```text
Git Push
   ↓
CI
   ↓
Tests
   ↓
Build Docker Image
   ↓
Publish Immutable Image
   ↓
Deploy
   ↓
Health Check
   ↓
Smoke Test
```

The objective is to make deployments reproducible rather than manually copying files onto a server.

---

# 32. Production Deployment

The initial production deployment uses a Linux cloud server/VPS.

High-level architecture:

```text
Internet
   │
   ▼
HTTPS
   │
   ▼
Nginx
   │
   ▼
Dockerized Spring Boot
   │
   ├── PostgreSQL
   ├── Redis
   └── RabbitMQ
```

Infrastructure responsibilities include:

* Linux server
* Docker
* Firewall
* Nginx
* HTTPS
* Environment configuration
* Database backups
* Application deployment
* Health checks
* Monitoring

Kubernetes is deliberately excluded from the initial version.

The objective is to understand the fundamentals of deployment and operations before introducing orchestration complexity.

---

# 33. Backup and Recovery

PostgreSQL is the system of record.

The deployment therefore requires:

* Automated backups
* Retention policy
* Backup verification
* Restore testing

A backup is not considered useful merely because a file exists.

The important question is:

> Can the database actually be restored?

The project therefore includes a documented restore procedure.

Example recovery process:

```text
Production database
        │
        ▼
    Backup
        │
        ▼
   Restore test
        │
        ▼
Verify schema/data
        │
        ▼
Document result
```

---

# 34. Project Documentation

The repository contains engineering documentation under:

```text
docs/
```

Expected structure:

```text
docs/
├── architecture.md
├── database.md
├── security.md
├── deployment.md
├── operations.md
├── troubleshooting.md
│
├── performance/
│   ├── baseline.md
│   ├── optimization-01.md
│   └── optimization-02.md
│
├── incidents/
│   ├── redis-outage.md
│   ├── rabbitmq-outage.md
│   └── worker-crash.md
│
└── adr/
    ├── ADR-001-modular-monolith.md
    ├── ADR-002-postgresql.md
    ├── ADR-003-multi-tenancy.md
    ├── ADR-004-authentication.md
    ├── ADR-005-authorization.md
    ├── ADR-006-redis.md
    ├── ADR-007-rabbitmq.md
    ├── ADR-008-idempotency.md
    ├── ADR-009-inventory-concurrency.md
    └── ADR-010-deployment.md
```

---

# 35. Architecture Decision Records

Important architectural decisions are documented rather than existing only in the developer's head.

Planned ADRs:

### ADR-001 — Modular Monolith

Why the project starts as a modular monolith rather than microservices.

### ADR-002 — PostgreSQL

Why PostgreSQL is the system of record.

### ADR-003 — Multi-Tenancy

How tenant isolation is implemented and why this strategy was selected.

### ADR-004 — Authentication

Why the selected access/refresh authentication model was chosen.

### ADR-005 — Permission-Based Authorization

Why permissions are preferred over hard-coded role checks.

### ADR-006 — Redis

Which problem Redis solves and why it is not the source of truth.

### ADR-007 — RabbitMQ

Why asynchronous processing is introduced and which workflows use it.

### ADR-008 — Idempotency

How duplicate requests are detected and handled.

### ADR-009 — Inventory Concurrency

How concurrent stock modifications are prevented from causing overselling.

### ADR-010 — Deployment

Why the initial production environment uses Docker + Linux + Nginx + HTTPS.

---

# 36. 14-Day Development Plan

OpsHub is being developed as a focused 14-day engineering sprint.

| Day | Primary Objective                        |
| --- | ---------------------------------------- |
| 1   | Project foundation                       |
| 2   | Architecture, ERD and database           |
| 3   | Registration and identity                |
| 4   | Authentication and security              |
| 5   | Multi-tenancy and RBAC                   |
| 6   | Customers and product catalog            |
| 7   | Inventory and concurrency                |
| 8   | Orders and idempotency                   |
| 9   | RabbitMQ, workers, retries and DLQ       |
| 10  | Payments and invoices                    |
| 11  | Redis, reporting and audit               |
| 12  | Testing, Docker and CI/CD                |
| 13  | Cloud deployment and observability       |
| 14  | Failure testing, performance and release |

The sprint intentionally prioritizes:

```text
Correctness
    ↓
Security
    ↓
Testing
    ↓
Deployment
    ↓
Observability
    ↓
Performance
    ↓
Polish
```

---

# 37. Engineering Evidence

One of the goals of OpsHub is to produce measurable evidence of engineering ability.

Evidence captured throughout the project includes:

* Architecture diagrams
* ERD
* API documentation
* CI/CD runs
* Docker builds
* Cloud deployment
* Grafana dashboards
* Load-test results
* Query plans
* Before/after benchmarks
* RabbitMQ retry demonstrations
* Dead-letter queue demonstrations
* Security test results
* Tenant-isolation tests
* Incident reports
* Backup/restore evidence
* Git history
* ADRs

The preferred format for documenting engineering work is:

```text
Problem
   ↓
Investigation
   ↓
Decision
   ↓
Implementation
   ↓
Measurement
   ↓
Result
```

For example:

> Product search became slower as the dataset increased. I inspected the query plan, identified an inefficient access pattern, introduced an index aligned with the tenant/filter query, reran the same load test, and compared p95 latency before and after the change.

This is more useful engineering evidence than simply saying:

> "Added PostgreSQL indexes."

---

# 38. Known Limitations

OpsHub is intentionally not attempting to replicate every feature of a commercial SaaS platform.

Current limitations include:

* No polished frontend
* No native mobile application
* No real banking settlement
* Payment integration uses a mock/sandbox provider
* Not a complete accounting system
* No Kubernetes in the initial version
* No full distributed microservice architecture
* No guarantee of regulatory compliance
* No production-scale multi-region deployment
* No complex enterprise SSO in the initial version

These limitations are intentional scope decisions.

The purpose of the project is to demonstrate strong backend engineering fundamentals and production thinking rather than maximize the number of technologies used.

---

# 39. Future Improvements

Possible future versions may introduce:

## Infrastructure

* Kubernetes
* Horizontal scaling
* Managed PostgreSQL
* Managed Redis
* Managed RabbitMQ
* Infrastructure as Code
* Multi-region deployment

## Security

* OAuth2/OIDC
* SSO
* MFA
* More advanced rate limiting
* Security event monitoring

## Architecture

* Service extraction based on measured bottlenecks
* Event-driven service boundaries
* Outbox pattern
* Distributed tracing

## Product

* Frontend dashboard
* Customer portal
* Advanced reporting
* File/document management
* Subscription billing
* Organization usage limits

## Observability

* OpenTelemetry
* Distributed traces
* Centralized log aggregation
* Alerting
* SLO/SLI tracking

These features should only be introduced when there is a concrete engineering reason.

---

# 40. Project Philosophy

OpsHub is not primarily an exercise in collecting technologies.

The objective is to understand why each technology exists and what problem it solves.

For example:

### PostgreSQL

Not:

> "Because production systems use PostgreSQL."

Instead:

> "PostgreSQL provides transactional persistence and strong consistency for the application's business data."

### Redis

Not:

> "Because Redis is popular."

Instead:

> "Redis is introduced for a measured read-heavy workload and rate limiting, while PostgreSQL remains the source of truth."

### RabbitMQ

Not:

> "Because I wanted to learn RabbitMQ."

Instead:

> "RabbitMQ decouples non-critical background work from synchronous API requests and provides retry/dead-letter mechanisms."

### Docker

Not:

> "Because Docker is on job descriptions."

Instead:

> "Docker makes the application and its dependencies reproducible across development and deployment environments."

### Monitoring

Not:

> "Because production systems need Grafana."

Instead:

> "Metrics allow us to observe latency, failures, resource utilization and business behavior so that problems can be diagnosed using evidence."

---

# Final Definition of Done

OpsHub is considered complete when the following are demonstrated:

* [ ] Core business workflows work end-to-end
* [ ] Authentication is implemented
* [ ] Authorization is implemented
* [ ] Tenant isolation is enforced
* [ ] Cross-tenant security tests pass
* [ ] Critical business operations are transactional
* [ ] Order creation is idempotent
* [ ] Inventory concurrency is protected
* [ ] RabbitMQ events work
* [ ] RabbitMQ retries work
* [ ] Dead-letter queues work
* [ ] Consumers are idempotent
* [ ] Redis has a measured use case
* [ ] Redis failure behavior is documented
* [ ] Unit tests pass
* [ ] Integration tests pass
* [ ] API tests pass
* [ ] Security tests pass
* [ ] Messaging tests pass
* [ ] Docker Compose works locally
* [ ] CI executes the test suite
* [ ] Docker image builds in CI
* [ ] Application is deployed to a cloud Linux environment
* [ ] HTTPS is configured
* [ ] CI/CD can deploy a new version
* [ ] Health checks are available
* [ ] Prometheus metrics are available
* [ ] Grafana dashboards are available
* [ ] Structured logging is implemented
* [ ] PostgreSQL backups exist
* [ ] Database restore has been tested
* [ ] At least five failure experiments are documented
* [ ] At least two performance optimizations have before/after measurements
* [ ] Architecture documentation is complete
* [ ] ERD is documented
* [ ] Security documentation is complete
* [ ] Deployment documentation is complete
* [ ] Operations runbook is complete
* [ ] ADRs are complete
* [ ] Incident reports are documented
* [ ] Final release is tagged

---

# Closing

OpsHub is intentionally built to answer questions that go beyond:

> "Can you build a REST API?"

The project should make it possible to answer:

> How do you prevent tenants from accessing each other's data?

> How do you prevent duplicate orders?

> How do you handle concurrent inventory purchases?

> What happens when RabbitMQ goes down?

> What happens when a worker crashes?

> How do you know whether Redis actually improved performance?

> How do you diagnose a slow endpoint?

> How do you deploy a new version?

> How do you recover your database?

> How do you know your application is healthy?

> How do you test security boundaries?

> Why did you choose this architecture?

The goal is not simply to have answers to these questions.

The goal is to have **built, tested, measured, documented, and experienced the answers firsthand.**

---

## Engineering Principle

```text
Implement
    ↓
Test
    ↓
Document
    ↓
Containerize
    ↓
Observe
    ↓
Break
    ↓
Fix
    ↓
Measure
    ↓
Repeat
```

**OpsHub — built as an engineering laboratory for production-style backend development.**
