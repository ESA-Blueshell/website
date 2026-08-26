# ADR-001: Application Modules Replace Layers

## Status
Accepted

## Context

The API is organised as four top-level packages — `domain`, `platform`, `shared`,
`infrastructure` — with a seven-layer dependency rule enforced by
`LayeredArchitectureTest` (Web, Application, Command, Domain, Persistence,
Infrastructure, Shared).

The layering is enforced and the boundaries between features are not. Nothing
stops `event` reaching into `survey`'s repositories, and nothing has:

| Cycle | imports each way |
|-------|------------------|
| `auth` ↔ `user` | 29 / 4 |
| `event` ↔ `survey` | 23 / 1 |
| `contribution` ↔ `user` | 9 / 1 |
| `event` ↔ `file` | 4 / 1 |
| `committee` ↔ `user` | 3 / 4 |
| `event` ↔ `user` | 3 / 2 |
| `file` ↔ `user` | 3 / 1 |

103 imports cross a feature boundary, and **45 of them reach directly into
another feature's `persistence` package** — its JPA entities. A layered rule
permits all of this, because `event.persistence` reading `survey.persistence` is
same-layer access.

[ADR-018](../api/ADR-018-data-ownership-in-modular-monolith.md) already declares
this a modular monolith. Nothing verifies the modules.

## Decision

**Feature modules are the primary structure, verified by Spring Modulith. Layer
rules inside a module are a matter of convention, not of enforcement.**

Spring Modulith 2.0 GA baselines on Spring Boot 4 and Framework 7, which matches
this codebase, and its event serialisation supports Jackson 3, which this project
already uses (`tools.jackson`).

### What a module is

Each direct sub-package of `net.blueshell.api` is an application module. Its
`api` sub-package is its published surface; everything else is internal and
unreachable from other modules. The convention is registered through a
detection strategy rather than annotated per module:

```kotlin
class BlueshellModuleDetection : ApplicationModuleDetectionStrategy {
    override fun detectNamedInterfaces(
        basePackage: JavaPackage,
        information: ApplicationModuleInformation,
    ) = NamedInterfaces.builder().recursive().matching("api").build()
}
```

Kotlin has no `package-info.java`, so module metadata uses the `@PackageInfo`
idiom — a class carrying the annotations, one per module.

### Cycles are not permitted

`ApplicationModules.verify()` fails on a cycle, and a cycle cannot be waived:
`allowedDependencies` narrows what a module may reach, it does not authorise a
loop. The seven cycles above are a precondition for adopting Modulith at all,
which is why they are phase 2 of
[ADR-006](ADR-006-migration-sequencing.md) and why nothing downstream can be
verified before they are gone.

Each reverse direction is between one and four imports, so breaking them is
removing a handful of references rather than reorganising modules. The
inversions use domain events, which is what
[ADR-006 on events](../api/ADR-006-event-driven-architecture.md) already
prescribes for cross-domain coordination.

### Cross-module coordination

Direct calls into another module's internals are replaced by two mechanisms:
a published type in the callee's `api` package, or a domain event consumed with
`@ApplicationModuleListener`. The latter runs on the Event Publication Registry —
transactional, retried, and republished on restart — which is the durable
substrate [ADR-004](ADR-004-deferred-execution-surface.md) builds on.

### What this replaces

`LayeredArchitectureTest`'s seven-layer rules are deleted. Module structure
verification replaces them, and Modulith 2.0 can additionally verify structure on
startup. The remaining ArchUnit tests — naming, JPA mapping, access rules,
Spring practices — are unaffected and stay.

## Implementation status

Decided, none of it built.

- Spring Modulith is not a dependency. The exact 2.0.x patch version is chosen
  when the dependency lands.
- The Event Publication Registry requires an `event_publication` table, so a
  Flyway migration is a prerequisite for any `@ApplicationModuleListener`.
- The seven cycles all exist today. `ApplicationModules.verify()` cannot pass
  until every one is broken.
- The `@PackageInfo` classes do not exist — roughly twenty of them, one per
  module.

## Consequences

### Positive
- **The boundary that matters is the one enforced.** Feature coupling is what
  has actually decayed here; layer direction never did.
- **Cycles become impossible rather than merely discouraged**, and the tool
  reports them by name.
- **Coordination gets a durable default.** `@ApplicationModuleListener` on the
  publication registry replaces hand-rolled async with framework code that
  survives a restart.
- **Layer rules stop being enforced where they do not pay.** A four-file module
  does not need a seven-layer proof.

### Negative
- **Seven cycles block everything.** Until they are broken, none of this can be
  verified, and breaking `auth ↔ user` at 29 imports one way is not trivial.
- **Nothing enforces layering inside a module any more.** A controller reaching
  a repository directly becomes a review matter rather than a build failure.
- **A new framework to learn**, with a Kotlin-specific metadata idiom that has
  no compile-time check — a missing `@PackageInfo` silently changes what is
  considered published.

### Neutral
- **Modules are not bounded contexts.** They are compile-time boundaries.
  [ADR-017](../api/ADR-017-bounded-context-relationships-and-context-map.md)
  keeps describing the domain relationships; this ADR describes only what the
  build checks.
- **This is not hexagonal architecture.** JPA entities remain the model. A
  domain model independent of persistence was considered and rejected in
  [ADR-002](ADR-002-use-case-services-replace-the-command-bus.md).

## Related ADRs
- [ADR-002: Use-Case Services Replace the Command Bus](ADR-002-use-case-services-replace-the-command-bus.md) — what fills the modules
- [ADR-003: Package Topology and Placement Rules](ADR-003-package-topology-and-placement-rules.md) — the physical layout
- [ADR-006: Migration Sequencing](ADR-006-migration-sequencing.md) — the order, and why cycles come early
- [API ADR-016: Layer Dependency Rules](../api/ADR-016-layer-dependency-rules.md) — superseded by this record
- [API ADR-018: Data Ownership in Modular Monolith](../api/ADR-018-data-ownership-in-modular-monolith.md) — the intent this finally verifies
