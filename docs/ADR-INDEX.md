# Architecture Decision Records (ADR) Index

This document provides an index of all Architecture Decision Records for the Blueshell API.

## ADR List

### Core Architecture

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [001](ADR-001-multi-layered-domain-driven-architecture.md) | Multi-Layered Domain-Driven Architecture | Accepted | Adopts DDD with package-by-feature organization, enforced by ArchUnit |
| [002](ADR-002-command-pattern-with-command-bus.md) | Command Pattern with CommandBus | Accepted | Uses Command pattern with CommandBus for all write operations |
| [013](ADR-013-entity-association-pattern.md) | Entity Association Pattern | Accepted | Entity references as single source of truth with computed ID properties |

### Data & Persistence

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [007](ADR-007-repository-pattern-and-jpa.md) | Repository Pattern and JPA | Accepted | Spring Data JPA with repository pattern in persistence layer |
| [010](ADR-010-database-migrations-with-flyway.md) | Database Migrations with Flyway | Accepted | Versioned SQL migrations with Flyway |

### Validation & Mapping

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [003](ADR-003-validation-layer-separation.md) | Validation Layer Separation | Accepted | Layered validation: web (structural), application (business rules), domain (invariants) |
| [004](ADR-004-mapping-strategy-with-mappie.md) | Mapping Strategy with Mappie | Accepted | Mappie for API boundaries, manual mapping for internal transformations |

### Patterns

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [005](ADR-005-factory-pattern-for-entity-creation.md) | Factory Pattern for Entity Creation | Accepted | Factories for complex entity creation, direct construction for simple cases |
| [006](ADR-006-event-driven-architecture.md) | Event-Driven Architecture | Accepted | Spring events for cross-domain communication and side effects |

### Security & API

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [008](ADR-008-exception-handling-strategy.md) | Exception Handling Strategy | Accepted | Problem Details (RFC 7807) with domain-specific exception hierarchies |
| [009](ADR-009-jwt-authentication-strategy.md) | JWT Authentication Strategy | Accepted | Stateless JWT authentication with Spring Security |
| [012](ADR-012-api-documentation-with-openapi.md) | API Documentation with OpenAPI | Accepted | SpringDoc OpenAPI 3 (Swagger UI) with code-first approach |

### Testing

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [011](ADR-011-testing-strategy.md) | Testing Strategy | Accepted | Multi-layered testing: ArchUnit, unit tests, Testcontainers, REST Assured |

## ADR Status Definitions

- **Proposed**: Under discussion
- **Accepted**: Approved and implemented
- **Deprecated**: No longer recommended
- **Superseded**: Replaced by another ADR

## How to Read ADRs

Each ADR follows this structure:
1. **Status**: Current state of the decision
2. **Context**: Problem being addressed
3. **Decision**: The chosen solution
4. **Consequences**: Positive and negative impacts
5. **Guidelines**: Best practices and anti-patterns
6. **Examples**: Code examples demonstrating the pattern
7. **References**: External resources

## Creating New ADRs

When making significant architectural decisions:

1. **Number**: Use next available number (ADR-014, ADR-015, etc.)
2. **Title**: Use kebab-case format: `ADR-###-descriptive-title.md`
3. **Template**: Follow existing ADR structure
4. **Single Concern**: One decision per ADR
5. **Update Index**: Add entry to this index

### ADR Template

```markdown
# ADR-XXX: Title

## Status
[Proposed | Accepted | Deprecated | Superseded]

## Context
[Describe the problem and constraints]

## Decision
[Describe the solution]

## Consequences

### Positive
- [Benefits]

### Negative
- [Drawbacks]

## Guidelines

### DO:
- ✅ [Best practices]

### DON'T:
- ❌ [Anti-patterns]

## Examples
[Code examples]

## References
[External resources]
```

## Related Documentation

- [CLAUDE.md](../CLAUDE.md) - Development guidelines
- [README.md](../README.md) - Project overview
- [association-refactor-checklist.md](association-refactor-checklist.md) - Entity association refactoring guide

## Maintenance

ADRs should be:
- ✅ Reviewed during architectural discussions
- ✅ Updated when patterns evolve
- ✅ Referenced in code reviews
- ✅ Used for onboarding new developers
- ✅ Superseded rather than deleted when outdated

Last Updated: 2026-02-13
