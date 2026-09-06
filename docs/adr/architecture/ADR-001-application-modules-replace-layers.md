# ADR-001: Application Modules Replace Layers

## Status
Accepted

## Context

The API is organised as four top-level packages — `domain`, `platform`, `shared`,
`infrastructure` — with a seven-layer dependency rule enforced by
`LayeredArchitectureTest` (Web, Application, Command, Domain, Persistence,
Infrastructure, Shared).

The layering is enforced and the boundaries between features are not. Nothing
stops `event` reaching into `survey`'s repositories, and nothing has.

Counting only the twelve `domain/*` features against each other understates it,
because the capability packages under `platform/integration` are peers of those
features rather than a layer beneath them. Measured across every module the
topology in [ADR-003](ADR-003-package-topology-and-placement-rules.md) names,
with `shared` and `security` excluded for the reason given below, there are
**fourteen cycles**:

| Cycle | imports each way | smaller side |
|-------|------------------|--------------|
| `cohort` ↔ `jobs` | 40 / 3 | 3 |
| `auth` ↔ `user` | 29 / 2 | 2 |
| `survey` ↔ `event` | 1 / 23 | 1 |
| `user` ↔ `contribution` | 1 / 9 | 1 |
| `contact` ↔ `sync` | 2 / 8 | 2 |
| `committee` ↔ `user` | 3 / 4 | 3 |
| `jobs` ↔ `event` | 4 / 2 | 2 |
| `file` ↔ `event` | 1 / 4 | 1 |
| `user` ↔ `event` | 2 / 3 | 2 |
| `email` ↔ `contribution` | 3 / 1 | 1 |
| `email` ↔ `event` | 2 / 1 | 1 |
| `email` ↔ `auth` | 1 / 2 | 1 |
| `file` ↔ `user` | 3 / 1 | 1 |
| `jobs` ↔ `contribution` | 2 / 2 | 2 |

731 imports cross a module boundary. **66 of them reach directly into another
module's `persistence` package**, naming 24 distinct entities, repositories and
specifications. A layered rule permits all of this, because `event.persistence`
reading `survey.persistence` is same-layer access.

[ADR-018](../api/ADR-018-data-ownership-in-modular-monolith.md) already declares
this a modular monolith. Nothing verifies the modules.

## Decision

**Feature modules are the primary structure, verified by Spring Modulith. Layer
rules inside a module are a matter of convention, not of enforcement.**

Spring Modulith is already a dependency — `spring-modulith-bom:2.1.0` with
`spring-modulith-starter-jdbc`, present since the sync work. Its 2.x line
baselines on Spring Boot 4 and Framework 7, matching this codebase, and its event
serialisation supports Jackson 3, which this project already uses
(`tools.jackson`). What this ADR adds is **module verification**, which is not
enabled.

### What a module is

A module is a package nominated by the detection strategy, not necessarily a
direct sub-package of `net.blueshell.api`.
`ApplicationModuleDetectionStrategy.getModuleBasePackages` returns an arbitrary
`Stream<JavaPackage>`, so the strategy can nominate `domain.user`,
`platform.integration.cohort` and the rest while the packages stay nested. The
same class declares the named-interface convention:

```kotlin
class BlueshellModuleDetection : ApplicationModuleDetectionStrategy {
    override fun getModuleBasePackages(basePackage: JavaPackage): Stream<JavaPackage> =
        // domain.*, platform.integration.*, platform.oidc,
        // shared, infrastructure.security
        ...

    override fun detectNamedInterfaces(
        basePackage: JavaPackage,
        information: ApplicationModuleInformation,
    ) = NamedInterfaces.builder().recursive().matching("api").build()
}
```

**Verification therefore does not depend on the flat topology.** An earlier
version of this record said flattening was a requirement rather than a
preference; that was asserted without checking the interface. The flattening in
[ADR-003](ADR-003-package-topology-and-placement-rules.md) is worth doing for
findability and shorter imports, and it is sequenced last precisely because
nothing verifiable waits on it.

A module's `api` sub-package is its published surface. `persistence` is
additionally published as a second named interface, `entities`, reachable only
by modules that name it — see ADR-003.

Kotlin has no `package-info.java`, so module metadata uses the `@PackageInfo`
idiom — a class carrying the annotations, one per module.

### Cycles, and the one waiver

`ApplicationModules.verify()` fails on a cycle between closed modules.
`allowedDependencies` does not authorise a loop — it only narrows what a module
may reach. But a cycle **can** be waived, by exactly one mechanism:
`@ApplicationModule(type = OPEN)`. Modulith's `ApplicationModulesSliceAssignment`
returns `SliceIdentifier.ignore()` for an open module, so its types never enter
ArchUnit's `beFreeOfCycles` check. An earlier version of this record said no
waiver existed; that was wrong.

**`shared` and `security` are open. Every other module is closed.** Both are
mechanism rather than capability: `shared` is the kernel that survived the
fan-in test, and `security` holds `BasePermissionEvaluator`,
`CompositePermissionEvaluator` and `CurrentUserProvider`. Making them open costs
nothing that matters, because a cycle through a kernel everything depends on
reports coupling that is already known and unavoidable, while feature-to-feature
coupling — the decay this record exists to catch — stays fully checked. It takes
the count from eighteen cycles to the fourteen tabulated above.

Openness is not extended further. `jobs` and `email` were considered and
rejected: each has an entity, a repository and a controller, so each is a
capability, and exempting them would hide coupling in the places it is most
likely to appear.

Breaking the fourteen means inverting **23 imports** — the smaller side of each
pair, and a single import in seven of them. Eleven are cross-module JPA
associations, which no domain event can break; the rule that governs them is in
[API ADR-013](../api/ADR-013-entity-association-pattern.md). Five are direct
service calls and four are misfiled types. The instrument is therefore chosen per
cycle, and [ADR-006](ADR-006-migration-sequencing.md) names it for each.

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

Partly built. This section originally said "none of it built" and that Modulith
was not a dependency; both were wrong, asserted without checking. What is
already in place:

- **The dependency**: `spring-modulith-bom:2.1.0` and
  `spring-modulith-starter-jdbc`.
- **The Event Publication Registry**: `V59__modulith_event_publication.sql`
  creates the table, and two listeners already run on it —
  `CalendarSyncListener` and `ContactSyncListener`, both
  `@ApplicationModuleListener`. Durable cross-module coordination is therefore
  not a future capability here; it is the existing mechanism for sync.

What is genuinely outstanding:

- **Module verification is not enabled.** No `ApplicationModules.verify()`
  anywhere, so nothing checks module boundaries today.
- **The `@PackageInfo` classes do not exist** — twenty, one per module,
  carrying the metadata Kotlin cannot put in a `package-info.java`.
- **The detection strategy does not exist.** Nominating the nested packages is
  what lets verification run before the flattening in ADR-003.
- **All fourteen cycles exist**, re-measured on `main` and reduced only where the
  command-bus slices happened to delete a handler that held a reverse import.
  `ApplicationModules.verify()` cannot pass until every one is broken.

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
- **Fourteen cycles block everything.** Until they are broken, none of this can
  be verified. The count is twice what a `domain/*`-only measurement suggested,
  though the work is smaller than the raw import counts imply: `auth ↔ user`
  reads as 29 imports and is 2 to invert.
- **Nothing enforces layering inside a module any more.** A controller reaching
  a repository directly becomes a review matter rather than a build failure.
- **A new framework to learn**, with a Kotlin-specific metadata idiom that has
  no compile-time check — a missing `@PackageInfo` silently changes what is
  considered published.

### Neutral
- **Modules are not bounded contexts.** They are compile-time boundaries. This
  ADR describes what the build checks — which module may reach which, and
  through what.
  [ADR-017](../api/ADR-017-bounded-context-relationships-and-context-map.md)
  keeps what it cannot: what a relationship means, and the anti-corruption
  layers in front of systems we do not own. It no longer carries a context map,
  because the module metadata is the one the build reads.
- **This is not hexagonal architecture.** JPA entities remain the model. A
  domain model independent of persistence was considered and rejected in
  [ADR-002](ADR-002-use-case-services-replace-the-command-bus.md).

## Related ADRs
- [ADR-002: Use-Case Services Replace the Command Bus](ADR-002-use-case-services-replace-the-command-bus.md) — what fills the modules
- [ADR-003: Package Topology and Placement Rules](ADR-003-package-topology-and-placement-rules.md) — the physical layout
- [ADR-006: Migration Sequencing](ADR-006-migration-sequencing.md) — the order, and why cycles come early
- [API ADR-016: Layer Dependency Rules](../api/ADR-016-layer-dependency-rules.md) — superseded by this record
- [API ADR-018: Data Ownership in Modular Monolith](../api/ADR-018-data-ownership-in-modular-monolith.md) — the intent this finally verifies
