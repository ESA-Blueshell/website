# ADR-006: Migration Sequencing

## Status
Accepted

## Context

The preceding five records describe a target, not a route. Together they touch
roughly 660 main source files and 230 test files, delete about 160 of them,
break seven dependency cycles, add a framework, and change every package name.

Three constraints shape the order.

**Moving code that is about to be deleted is wasted work.** 110 handlers and 53
pass-through commands are condemned by
[ADR-002](ADR-002-use-case-services-replace-the-command-bus.md); relocating them
first costs the effort twice and inflates every intermediate diff.

**A commit that renames and edits a file loses `git blame`.** Rename detection
survives a pure move; it does not survive a move plus a body change. On a
codebase where blame is the main tool for "why is this like this", that is worth
protecting deliberately.

**Nothing can be verified until the cycles are gone.**
`ApplicationModules.verify()` fails on a cycle and a cycle cannot be waived, so
every downstream check is blocked behind
[ADR-001](ADR-001-application-modules-replace-layers.md)'s seven pairs.

A single change doing all of this would be unreviewable at that size and
unbisectable when something broke.

## Decision

**Four phases. Delete, then untangle, then move, then verify. Each phase is
independently valuable and can be stopped after without leaving the codebase in
a worse state than it started.**

### Phase 1 — Delete, and relocate what does not depend on anything else

Removes code and settles the two placement questions that have no dependency on
the command refactor:

- The `CommandBus`, `CommandHandler`, all 110 handler classes and the 53
  pass-through commands.
- `AsyncCommandDispatcher`, `AbstractCommandJobHandler`, `CommandJobDefinition`
  and their two unit tests — verified to have no production callers.
- `shared/validation/date` and `shared/jpa`, which have no cross-module
  consumer.
- `shared` narrows to the packages with real fan-in; everything at three
  consumers or fewer moves to its owning module.
- The 19 permission classes distribute to their modules, leaving the abstract
  base and the composite evaluator in `security`.
- The 26 `@SpringBootTest` files that sit in the unit source set move to
  `integrationTest`, discharging the obligation in
  [testing ADR-001](../testing/ADR-001-test-pyramid-and-layer-placement.md).

`shared` and the permissions are pulled forward into this phase deliberately.
Neither depends on the command work, both are pure moves, and both are large
contributors to the daily cost of finding things — so they should not wait
behind the riskiest phase.

### Phase 2 — Break the seven cycles

One pair per pull request, behaviour-preserving, inverting the smaller direction
— every reverse edge is between one and four imports. Domain events are the
instrument, which is what
[ADR-006](../api/ADR-006-event-driven-architecture.md) already prescribes.

Ordered by size so the technique is proven on the cheap pairs first:
`file ↔ user` (3/1), `event ↔ user` (3/2), `committee ↔ user` (3/4),
`event ↔ file` (4/1), `contribution ↔ user` (9/1), `event ↔ survey` (23/1),
`auth ↔ user` (29/4).

### Phase 3 — Move packages, rename-only

The topology in
[ADR-003](ADR-003-package-topology-and-placement-rules.md), applied as commits
that contain **renames and import updates and nothing else**. No behaviour
change, no formatting, no opportunistic fixes. One module per commit so a
reviewer can check the rename list rather than the diff.

### Phase 4 — Adopt Modulith and turn verification on

Add the dependency, write the roughly twenty `@PackageInfo` classes and the
detection strategy, add the `event_publication` Flyway migration, enable
`ApplicationModules.verify()`, and delete `LayeredArchitectureTest`.

The execution surface in
[ADR-004](ADR-004-deferred-execution-surface.md) and the validation move in
[ADR-005](ADR-005-validation-placement.md) land after phase 4, since both are
additive and neither blocks the structural work. ADR-005's unique-index
migration must precede its validator change regardless of phase.

## Implementation status

Nothing started. Phase 1 is the entry point and is the only phase whose
prerequisites are all met.

Phase 3's value depends entirely on discipline about rename-only commits; there
is no tooling that enforces it, and a reviewer cannot easily tell a mixed commit
from a clean one after the fact.

## Consequences

### Positive
- **The codebase shrinks before it moves.** Roughly 160 files never get
  relocated at all.
- **`git blame` survives** the largest package rename this repository has had.
- **Each phase stands alone.** Stopping after phase 1 leaves a smaller codebase
  with `shared` and permissions fixed; stopping after phase 2 leaves an
  acyclic one.
- **Findability improves in phase 1**, not only at the end — which matters
  because that is the cost being paid daily.

### Negative
- **Phase 1 carries the most risk and comes first.** Deleting the bus and 110
  handlers changes behaviour paths in 20 controllers; the structural phases that
  follow are safer but gated behind it.
- **Two structures coexist for the duration.** Until phase 3 completes, some
  code is in the target layout and some is not, and new work has to choose.
- **Phase 2 is open-ended.** `auth ↔ user` at 29 imports may not decompose as
  cleanly as the small pairs suggest, and its cost is not known until the cheap
  pairs are done.
- **No phase delivers Modulith's guarantees until the last one**, so the
  discipline holding phases 1 to 3 together is entirely human.

### Neutral
- **The order optimises for wasted work and blame, not for speed.** Moving
  packages first would deliver the findability win sooner, at the cost of
  carefully relocating about 160 files that are then deleted.

## Related ADRs
- [ADR-001: Application Modules Replace Layers](ADR-001-application-modules-replace-layers.md) — the cycles that gate phase 4
- [ADR-002: Use-Case Services Replace the Command Bus](ADR-002-use-case-services-replace-the-command-bus.md) — what phase 1 deletes
- [ADR-003: Package Topology and Placement Rules](ADR-003-package-topology-and-placement-rules.md) — what phase 3 applies
- [Testing ADR-001: The Test Pyramid and Layer Placement](../testing/ADR-001-test-pyramid-and-layer-placement.md) — the 27 misplaced tests phase 1 clears
