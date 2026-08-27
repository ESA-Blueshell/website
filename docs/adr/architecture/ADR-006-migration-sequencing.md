# ADR-006: Migration Sequencing

## Status
Accepted

## Context

The preceding five records describe a target, not a route. Together they touch
653 main source files and 228 api test files, delete about 160 of them, break
fourteen dependency cycles, add a framework, and change every package name.

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
`ApplicationModules.verify()` fails on a cycle between closed modules, so every
downstream check is blocked behind
[ADR-001](ADR-001-application-modules-replace-layers.md)'s fourteen pairs.
Openness waives a cycle, but it is spent on `shared` and `security` and is not
available as a way round the rest.

**Verification does not wait on the flattening.** A detection strategy nominates
the nested packages, so the order below puts verification third and the package
moves last. The guarantee then holds while 653 files move, rather than arriving
after them.

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

### Phase 2 — Break the fourteen cycles

Fourteen cycles, 23 imports to invert. Ordered **delete, then move, then invert**
— the same principle as the phases themselves, because six of the fourteen need
no design at all.

Three earlier claims about this phase were wrong and are withdrawn:

- **"Domain events are the instrument."** Events fit five of the 23 imports.
  Eleven are cross-module JPA associations, which no event can break, and four
  are types filed in the wrong module.
- **"Invert the smaller direction."** For `email ↔ contribution`,
  `email ↔ event` and `committee ↔ user` the smaller direction is the legitimate
  one — a domain building its own email content, a committee membership naming a
  user — and the larger direction is the violation.
- **"One pair per pull request."** Two files cause six of the fourteen cycles
  between them, so the unit of work is the fix, not the pair.

| # | Fix | Cycles closed | Imports |
|---|-----|---------------|---------|
| 1 | Delete `User.profilePicture` — dead, read by nothing in any source set | `file ↔ user` | 1 |
| 2 | Drop `Answer.eventSignUpAnswer` back-reference | `survey ↔ event` | 1 |
| 3 | Drop `File._eventBanners` back-reference | `file ↔ event` | 1 |
| 4 | Drop four `User` back-references, add `UserDeletionParticipant` | `user ↔ contribution`, and part of three more | 4 |
| 5 | Move `RemoveContactJob` and `SyncContactJob` into `sync` | `contact ↔ sync` | 2 |
| 6 | Move the committee-membership listeners into `committee` | `committee ↔ user` | 3 |
| 7 | Move `UserSpecifications.approved()` — it returns `Specification<Event>` — into `event` | `user ↔ event` | 1 |
| 8 | `JobExecutionViewService` stops resolving cohort, period and event labels | `jobs ↔ event`, `jobs ↔ contribution`, `jobs ↔ cohort` | 9 |
| 9 | `EmailSenderService` stops building each domain's content | `email ↔ auth`, `email ↔ contribution`, `email ↔ event` | 6 |
| 10 | `MembershipUseCases` reaches `SignupCompletionService` through a port or an event | `auth ↔ user` | 1 |

Fixes 1 to 3 are deletions and belong to phase 1's principle; they are listed
here because each closes a cycle. Fixes 5 to 7 are rule 3 of
[ADR-003](ADR-003-package-topology-and-placement-rules.md) — one feature, one
module — applied to code filed in the wrong place. Only 8, 9 and 10 need a
design decision, and 8 and 9 are each one file doing three modules' work.

The cascade that fix 4 removes is re-homed on an injected participant list, not
an event: `user` publishes `UserDeletionParticipant`, each affected module
registers a `@Component`, and the use case calls them inside its own
transaction. A database-level `ON DELETE CASCADE` cannot substitute, because
`User` carries `@SQLDelete` and its deletion is an `UPDATE`.

### Phase 3 — Turn verification on

Write the detection strategy nominating the nested module packages, the twenty
`@PackageInfo` classes, the `api` and `entities` named interfaces and the
`allowedDependencies` whitelists; enable `ApplicationModules.verify()`; delete
`LayeredArchitectureTest`; and add the ArchUnit rule that no `mappedBy` field
may name a type outside its own module.

The dependency and the `event_publication` table already exist, so neither is
part of this phase.

This is third rather than last because it does not need the flattening. Putting
it here means the boundaries are enforced *before* 653 files move, so phase 4
cannot silently reintroduce what phase 2 removed, and feature work landing
alongside phase 4 cannot either.

### Phase 4 — Move packages, rename-only

The topology in
[ADR-003](ADR-003-package-topology-and-placement-rules.md), applied as commits
that contain **renames and import updates and nothing else**. No behaviour
change, no formatting, no opportunistic fixes. One module per commit so a
reviewer can check the rename list rather than the diff.

Last, and schedulable into any quiet window, because by this point it changes
nothing enforceable — it buys shorter imports and findability. That matters
because 653 renames collide with every branch in flight, and this is the phase
that can wait for them.

Rename-only discipline is no longer purely human: the coverage diff task
resolves renames with `git diff -M`, exempting a pure rename (`R100`) and gating
a rename mixed with an edit (`R0xx`).

### Alongside, not in sequence

[ADR-004](ADR-004-deferred-execution-surface.md) and
[ADR-005](ADR-005-validation-placement.md) are additive and touch modules the
structural phases barely reach, so they run in parallel rather than waiting for
phase 4:

- ADR-004's `enqueue` → `runAsync` rename and its misleading `scheduledFor={}`
  log line are immediate. `runIn` and the `scheduledFor` column wait for a
  caller.
- ADR-005's missing unique constraints on `discord` and `phone_number` are a
  live integrity gap and are filed as a defect. The two validator moves follow
  phase 2, since they land in modules phase 2 rewrites.

## Implementation status

Phase 1 is nearly complete. Nine command-bus slices were planned and seven have
merged; `esports` and the deletion of `shared/command` itself remain. The
`shared` narrowing, the permission distribution, the dead async bridge and the
Spring-context test move have all landed.

Phase 2 has not started, and its prerequisite is this record and the four others
amended alongside it — every figure in the phase-2 table was measured after the
seventh slice merged, and five claims in the set were wrong before that
measurement.

Phase 4's rename-only discipline is no longer purely human, so the warning this
section used to carry is withdrawn: the diff task behind the coverage gates
distinguishes a pure rename from a mixed one by `git diff -M` similarity, which
is precisely what a reviewer could not do after the fact.

## Consequences

### Positive
- **The codebase shrinks before it moves.** Roughly 160 files never get
  relocated at all.
- **`git blame` survives** the largest package rename this repository has had.
- **Each phase stands alone.** Stopping after phase 1 leaves a smaller codebase
  with `shared` and permissions fixed; after phase 2, an acyclic one; after
  phase 3, an acyclic one that stays acyclic without anyone watching.
- **The guarantee arrives before the churn.** Verification third rather than
  last means the 653-file rename happens under enforcement instead of on trust.
- **Findability improves in phase 1**, not only at the end — which matters
  because that is the cost being paid daily.

### Negative
- **Phase 1 carries the most risk and comes first.** Deleting the bus and 110
  handlers changes behaviour paths in 20 controllers; the structural phases that
  follow are safer but gated behind it.
- **Two structures coexist for longer.** Verification landing before the moves
  means the nested layout is the enforced one for as long as phase 4 waits, and
  new work has to choose which layout to write in.
- **Phase 3 carries throwaway code.** The detection strategy that nominates
  nested packages is deleted once phase 4 flattens them, and the twenty
  `@PackageInfo` classes move with their packages.
- **Phase 4 may be deferred indefinitely.** Once the boundaries are enforced,
  the flattening has no forcing function, and a phase with no deadline and no
  enforcement is a phase that can quietly not happen. What is then permanently
  lost is the two uninformative segments in every import.

### Neutral
- **The order optimises for wasted work and blame, not for speed.** Moving
  packages first would deliver the findability win sooner, at the cost of
  carefully relocating about 160 files that are then deleted.

## Related ADRs
- [ADR-001: Application Modules Replace Layers](ADR-001-application-modules-replace-layers.md) — the cycles that gate phase 3, and the openness that bounds them
- [ADR-002: Use-Case Services Replace the Command Bus](ADR-002-use-case-services-replace-the-command-bus.md) — what phase 1 deletes
- [ADR-003: Package Topology and Placement Rules](ADR-003-package-topology-and-placement-rules.md) — what phase 3 applies
- [Testing ADR-001: The Test Pyramid and Layer Placement](../testing/ADR-001-test-pyramid-and-layer-placement.md) — the misplaced tests phase 1 clears
- [API ADR-013: Entity Association Pattern](../api/ADR-013-entity-association-pattern.md) — the rule that decides eleven of phase 2's imports
