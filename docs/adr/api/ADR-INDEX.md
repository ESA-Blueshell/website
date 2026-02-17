# API Architecture Decision Records Index

This index tracks architecture decisions for the Kotlin/Spring API.

## ADR List

### Core Architecture

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [001](ADR-001-multi-layered-domain-driven-architecture.md) | Multi-Layered Domain-Driven Architecture | Accepted | DDD package-by-feature with explicit layer boundaries |
| [002](ADR-002-command-pattern-with-command-bus.md) | Command Pattern with CommandBus | Accepted | Command dispatch and handler-based write flows |
| [013](ADR-013-entity-association-pattern.md) | Entity Association Pattern | Accepted | Association ownership and reference consistency rules |
| [016](ADR-016-layer-dependency-rules.md) | Layer Dependency Rules and Clean Architecture | Accepted | Strict dependency direction and ArchUnit-enforced boundaries |
| [022](ADR-022-platform-infrastructure-shared-organization.md) | Platform, Infrastructure, and Shared Organization | Accepted | Separation of shared contracts, infrastructure adapters, and platform integrations |

### Strategic Domain-Driven Design

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [017](ADR-017-bounded-context-relationships-and-context-map.md) | Bounded Context Relationships and Context Map | Accepted | Domain relationships and context mapping |
| [018](ADR-018-data-ownership-in-modular-monolith.md) | Data Ownership in Modular Monolith | Accepted | Data ownership boundaries across domains |
| [019](ADR-019-anti-corruption-layers-for-external-integration.md) | Anti-Corruption Layers for External Integration | Accepted | External integration isolation via ACL adapters |
| [020](ADR-020-shared-kernel-governance.md) | Shared Kernel Governance | Accepted | Governance rules for shared cross-domain contracts |
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
| [003](ADR-003-validation-layer-separation.md) | Validation Layer Separation | Accepted | Layered validation responsibilities |
| [004](ADR-004-mapping-strategy-with-mappie.md) | Mapping Strategy with Mappie | Accepted | Mapping strategy at API boundaries and internal layers |

### Patterns

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [005](ADR-005-factory-pattern-for-entity-creation.md) | Factory Pattern for Entity Creation | Accepted | Factories for complex object creation workflows |
| [006](ADR-006-event-driven-architecture.md) | Event-Driven Architecture | Accepted | Event-based cross-domain coordination |

### Security and API

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [008](ADR-008-exception-handling-strategy.md) | Exception Handling Strategy | Accepted | Problem Details and exception handling conventions |
| [009](ADR-009-jwt-authentication-strategy.md) | JWT Authentication Strategy | Accepted | Stateless JWT-based authentication |
| [012](ADR-012-api-documentation-with-openapi.md) | API Documentation with OpenAPI | Accepted | OpenAPI-first API documentation workflow |
| [014](ADR-014-permission-evaluation-strategy.md) | Permission Evaluation Strategy | Accepted | Permission evaluators and authorization design |

### Testing

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [011](ADR-011-testing-strategy.md) | Testing Strategy | Accepted | Test pyramid including architecture, unit, and integration coverage |

## Related Documentation
- [ADR umbrella index](../ADR-INDEX.md)
- [Frontend ADR index](../frontend/ADR-INDEX.md)
- [CLAUDE.md](../../../CLAUDE.md)
