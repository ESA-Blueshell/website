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
- A mutating call arranges to notice a refusal: it passes `throwOnError: true`, or it reads the
  returned `error`/`data` and answers with a refusal of its own.

### Refusals Are Not Exceptions
The generated client resolves rather than throws on 4xx and 5xx unless a call passes
`throwOnError: true`, so a `try`/`catch` around a bare call is dead code and the caller carries on
as though the write landed. `domains/boards/adapters/boards.ts` is the shape to copy: every mutator
reads `res.error || !res.data` and returns a `Refused` carrying the API's own words.

This is enforced by `tests/unit/architecture/uncheckedSdkWrites.test.ts`, which sweeps `src` for
mutating calls that make no such arrangement. The call sites that predate the rule are pinned there
against the ticket that removes each one.

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
- Return a refusal from an adapter mutator, and act on it at the call site

### DO NOT
- Import generated request/response types in page components
- Pass generated models through multiple UI layers unchanged
- Mix transport naming directly into domain models
- Wrap a generated call in `try`/`catch` and assume a refusal reaches it

## References
- OpenAPI Generator: https://openapi-generator.tech/
- Anti-Corruption Layer pattern: https://martinfowler.com/bliki/AntiCorruptionLayer.html
