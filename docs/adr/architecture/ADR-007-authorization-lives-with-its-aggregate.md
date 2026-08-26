# ADR-007: Authorization Lives With Its Aggregate

## Status
Accepted

## Context

[API ADR-014](../api/ADR-014-permission-evaluation-strategy.md) established
domain-specific permission evaluators dispatched through Spring Security's
`PermissionEvaluator`, and placed every evaluator in
`infrastructure/security/permission`. An ArchUnit rule enforces the location:
any class whose name ends in `Permission` must reside in that package.

The mechanism has worked. The location has not aged well, and
[ADR-003](ADR-003-package-topology-and-placement-rules.md) rule 4 —
authorization lives beside what it authorizes — contradicts it directly. That
conflict blocked a migration workstream, which is why it is being settled rather
than worked around.

ADR-014 gave three reasons for the infrastructure layer. Each is now either
false or inapplicable:

**"Follow hexagonal architecture: adapters in outer layer."** Textbook hexagonal
was considered for this codebase and rejected in
[ADR-002](ADR-002-use-case-services-replace-the-command-bus.md): a domain model
independent of JPA would need a mirror type and a mapper per aggregate. The
principle that put evaluators in an outer layer is no longer the one this
codebase follows.

**"Can be used from multiple interfaces (REST, GraphQL, messaging, CLI)."**
There is one interface. There has only ever been one. A location chosen for
interfaces that do not exist is paying rent on a hypothetical.

**"Permission evaluators are Spring Security adapters."** This is the strongest
of the three and it is half true. `BasePermissionEvaluator` and
`CompositePermissionEvaluator` genuinely are framework adapters — they implement
Spring's interface and perform dispatch. The domain evaluators are not: each one
is a set of business rules about who may do what to one aggregate.
`BlogPermission` depends on `BlogService` and `Blog` and on nothing else.

A fourth consideration did not exist when ADR-014 was written.
[ADR-001](ADR-001-application-modules-replace-layers.md) adopts Spring Modulith,
under which a module's sub-packages are internal. Keeping the evaluators central
would require **every module to publish its service and its entity** so that
`infrastructure` could reach them — inverting the encapsulation module
verification exists to enforce, for seventeen classes that each belong to exactly
one module.

## Decision

**A domain permission evaluator belongs to the module whose aggregate it
governs. The dispatch mechanism stays where it is.**

### What moves

Seventeen evaluators, to the module of the domain type each already imports —
determined from the code, not from the class name:

| Module | Evaluators |
|--------|-----------|
| `user` | Address, Membership, User |
| `event` | Event, EventBanner, EventSignUp, Guest |
| `contribution` | Contribution, ContributionPeriod, ContributionReminder |
| `blog`, `board`, `committee`, `sponsor`, `telemetry` | one each |
| `email`, `job` | Email, JobExecution |

### What stays

`BasePermissionEvaluator` and `CompositePermissionEvaluator` remain in
`infrastructure/security/permission`. They are the mechanism, not a rule about
any one domain, and they are the part of ADR-014's "framework adapter" argument
that holds.

### Why this is safe

`CompositePermissionEvaluator` injects
`MutableList<BasePermissionEvaluator<*, *, *>>`. Spring resolves that by **bean
type**, so any `@Component` under the component-scan root is collected whatever
its package. `hasPermission(targetId, targetType, permission)` then matches on
`domainType.simpleName`. Neither discovery nor dispatch depends on package
location, so nothing needs registering and no `@PreAuthorize` expression changes.

Everything ADR-014 decided about the *mechanism* is carried forward unchanged:
the `PermissionEvaluator` interface, the composite, the generic base class and
its `domainType` resolution, and the `hasPermission('id', 'Type', 'action')`
call shape.

### The rule that enforced the old location

`AccessArchitectureTest`'s "permission evaluators in infrastructure layer" rule
is replaced by its inverse: a class named `*Permission` must **not** reside in
`infrastructure.security.permission` unless it is the base or the composite.
`DecoratorsArchitectureTest`'s requirement that evaluators be `@Component` is
retained and widened to wherever they now live — it is what makes type-based
discovery work.

## Implementation status

Decided; the moves and the ArchUnit change follow in the next pull request, as
the constitution requires the ADR to land first.

## Consequences

### Positive
- **Adding a domain stops editing a central package.** A new aggregate brings
  its own authorization with it.
- **Modules stop having to publish their internals** for an outside package to
  reach — a precondition for module verification under ADR-001.
- **Authorization is where a reader looks for it**: beside the service and the
  entity it constrains.

### Negative
- **There is no longer one place to read every authorization rule.** That was a
  real auditing convenience and it is being given up; the answer is a search for
  `*Permission` rather than a directory listing.
- **The tests do not follow.** The five existing test classes are grouped by
  evaluator *style* — `SimplePermissionEvaluatorsTest` alone covers seven
  modules — so they stay put and become cross-cutting. Splitting them per module
  is worth doing and is not part of this decision.
- **A rule reverses.** Anyone who learned "permissions go in infrastructure" has
  to unlearn it, and the ArchUnit rule that taught them now teaches the opposite.

### Neutral
- **Nothing changes at runtime.** Discovery is by bean type and dispatch is by
  simple name; both are package-independent.

## Related ADRs
- [API ADR-014: Permission Evaluation Strategy](../api/ADR-014-permission-evaluation-strategy.md) — superseded by this record; its mechanism is carried forward
- [ADR-003: Package Topology and Placement Rules](ADR-003-package-topology-and-placement-rules.md) — rule 4, which this settles
- [ADR-001: Application Modules Replace Layers](ADR-001-application-modules-replace-layers.md) — why a central package would invert module encapsulation
- [ADR-002: Use-Case Services Replace the Command Bus](ADR-002-use-case-services-replace-the-command-bus.md) — where hexagonal was rejected
