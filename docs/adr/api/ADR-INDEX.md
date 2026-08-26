# API Architecture Decision Records Index

This index tracks architecture decisions for the Kotlin/Spring API.

## ADR List

### Core Architecture

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [001](ADR-001-multi-layered-domain-driven-architecture.md) | Multi-Layered Domain-Driven Architecture | Superseded | Replaced by the [architecture ADR set](../architecture/ADR-INDEX.md) |
| [002](ADR-002-command-pattern-with-command-bus.md) | Command Pattern with CommandBus | Superseded | Replaced by use-case services; query/command split carried forward |
| [013](ADR-013-entity-association-pattern.md) | Entity Association Pattern | Accepted | Association ownership and reference consistency rules |
| [016](ADR-016-layer-dependency-rules.md) | Layer Dependency Rules and Clean Architecture | Superseded | Replaced by Spring Modulith module verification |
| [022](ADR-022-platform-infrastructure-shared-organization.md) | Platform, Infrastructure, and Shared Organization | Superseded | Replaced by the flat-module package topology |

### Strategic Domain-Driven Design

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [017](ADR-017-bounded-context-relationships-and-context-map.md) | Bounded Context Relationships and Context Map | Accepted | Domain relationships and context mapping |
| [018](ADR-018-data-ownership-in-modular-monolith.md) | Data Ownership in Modular Monolith | Accepted | Data ownership boundaries across domains |
| [019](ADR-019-anti-corruption-layers-for-external-integration.md) | Anti-Corruption Layers for External Integration | Accepted | External integration isolation via ACL adapters |
| [020](ADR-020-shared-kernel-governance.md) | Shared Kernel Governance | Superseded | Replaced by the fan-in placement rule |
| [021](ADR-021-observability-and-distributed-tracing.md) | Observability and Distributed Tracing | Proposed | Correlation, tracing, and observability standards |

### Data and Persistence

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [007](ADR-007-repository-pattern-and-jpa.md) | Repository Pattern and JPA | Accepted | Spring Data repositories and persistence-layer rules |
| [010](ADR-010-database-migrations-with-flyway.md) | Database Migrations with Flyway | Accepted | Versioned SQL migration workflow |
| [015](ADR-015-jpa-specifications-dynamic-queries.md) | JPA Specifications and Dynamic Queries | Accepted | Query-object driven dynamic filtering |

### Validation and Mapping

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [003](ADR-003-validation-layer-separation.md) | Validation Layer Separation | Accepted (amended) | Layered validation responsibilities; database-dependent rules move to the use case |
| [004](ADR-004-manual-mapping-at-api-boundaries.md) | Manual Mapping at API Boundaries | Accepted | Explicit manual mapping strategy for request/response and command/entity boundaries |

### Patterns

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [005](ADR-005-factory-pattern-for-entity-creation.md) | Factory Pattern for Entity Creation | Accepted | Factories for complex object creation workflows |
| [006](ADR-006-event-driven-architecture.md) | Event-Driven Architecture | Accepted (amended) | Event-based cross-domain coordination, on the Event Publication Registry |
| [025](ADR-025-membership-commit-rendezvous.md) | Membership Commit Rendezvous | Accepted | Membership commits when the last of email confirmation and application submission lands |

### Security and API

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [008](ADR-008-exception-handling-strategy.md) | Exception Handling Strategy | Accepted | Problem Details and exception handling conventions |
| [009](ADR-009-jwt-authentication-strategy.md) | JWT Authentication Strategy | Accepted | Stateless JWT-based authentication |
| [012](ADR-012-api-documentation-with-openapi.md) | API Documentation with OpenAPI | Accepted | OpenAPI-first API documentation workflow |
| [014](ADR-014-permission-evaluation-strategy.md) | Permission Evaluation Strategy | Accepted | Permission evaluators and authorization design |
| [024](ADR-024-scoped-signup-continuation-tokens.md) | Scoped Signup Continuation Tokens | Accepted | Header-borne capability for unauthenticated signup writes, deliberately not a JWT or a principal |

### Job System

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [023](ADR-023-job-consolidation-and-reliable-execution.md) | Job Consolidation and Reliable Execution | Accepted (amended) | @Async + RetryTemplate, consolidated job types, dedup; gains scheduledFor |

### Testing

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [011](ADR-011-testing-strategy.md) | Testing Strategy | Superseded | Replaced by the [testing ADR set](../testing/ADR-INDEX.md) |

## Related Documentation
- [Flow documentation](../../flows/README.md)
- [ADR umbrella index](../ADR-INDEX.md)
- [Frontend ADR index](../frontend/ADR-INDEX.md)
- [Testing ADR index](../testing/ADR-INDEX.md)
- [Architecture ADR index](../architecture/ADR-INDEX.md)
- [CLAUDE.md](../../../CLAUDE.md)
