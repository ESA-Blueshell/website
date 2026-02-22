# ADR-002: API Client Boundary and Domain Mapping

## Status
Accepted

## Context
OpenAPI clients are generated from backend contracts and can change when schema details change. Generated types are transport types, not stable UI models. Leaking them into components couples frontend behavior to backend serialization details and nullability quirks.

## Decision
Treat generated API clients as infrastructure and enforce domain adapter boundaries.

### Boundary Rules
- Generated code remains under `src/services/api/*` and is never manually edited.
- Each domain owns adapter modules that wrap generated operations.
- Components/composables consume domain models, not generated response models.
- Mapping functions normalize backend nullability and optional fields once at the boundary.

### Domain Adapter Contract
Each domain adapter exposes:
- `queries`: read operations
- `commands`: write operations
- `mapping`: transport <-> domain transformations
- `errors`: backend error -> domain/UI error mapping

## Consequences

### Positive
- API regenerations have limited UI impact
- Domain language stays stable in feature code
- Nullability handling becomes explicit and testable

### Negative
- More mapping code to maintain
- Requires enforcement to prevent direct generated-client imports

## Guidelines

### DO
- Normalize transport nullability at adapter boundaries
- Keep generated-client imports inside adapter modules
- Map backend problem responses to typed domain errors

### DO NOT
- Import generated request/response types in page components
- Pass generated models through multiple UI layers unchanged
- Mix transport naming directly into domain models

## References
- OpenAPI Generator: https://openapi-generator.tech/
- Anti-Corruption Layer pattern: https://martinfowler.com/bliki/AntiCorruptionLayer.html
