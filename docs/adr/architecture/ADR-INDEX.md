# Architecture Decision Records Index

This set records how the API is structured: what a module is, what fills one,
where a file goes, and how the codebase gets from its current shape to this one.
It is cross-cutting because the decisions bind the whole service rather than one
stack.

## ADR List

### Structure

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [001](ADR-001-application-modules-replace-layers.md) | Application Modules Replace Layers | Accepted | Feature modules verified by Spring Modulith; `shared` and `security` open, fourteen cycles closed; layer rules inside a module become convention |
| [003](ADR-003-package-topology-and-placement-rules.md) | Package Topology and Placement Rules | Accepted | Twenty flat modules, four folders and two named interfaces inside each, six rules for where a file goes |
| [007](ADR-007-authorization-lives-with-its-aggregate.md) | Authorization Lives With Its Aggregate | Accepted | Domain permission evaluators move to their module; the dispatch mechanism stays |
| [008](ADR-008-the-backend-does-not-borrow-the-frontends-vocabulary.md) | The Backend Does Not Borrow the Frontend's Vocabulary | Accepted | Records are named for what they are, not where they appear; identity is `code` and the human name is `name`; comments name the act or the caller, never the widget |

### Execution

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [002](ADR-002-use-case-services-replace-the-command-bus.md) | Use-Case Services Replace the Command Bus | Accepted | The bus and all 110 handlers are deleted; operations become service methods |
| [004](ADR-004-deferred-execution-surface.md) | Deferred Execution Surface | Accepted | runAsync and runIn on a dispatcher, durable, returning a handle rather than a future; runIn waits for a caller |
| [005](ADR-005-validation-placement.md) | Validation Placement | Accepted | Field constraints stay declarative; uniqueness moves into the use case behind a database constraint |

### Getting there

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [006](ADR-006-migration-sequencing.md) | Migration Sequencing | Accepted | Delete, untangle, verify, move — four phases across 881 files, with verification before the rename |

## Superseded by this set
- [API ADR-001: Multi-Layered Domain-Driven Architecture](../api/ADR-001-multi-layered-domain-driven-architecture.md)
- [API ADR-002: Command Pattern with CommandBus](../api/ADR-002-command-pattern-with-command-bus.md)
- [API ADR-016: Layer Dependency Rules](../api/ADR-016-layer-dependency-rules.md)
- [API ADR-020: Shared Kernel Governance](../api/ADR-020-shared-kernel-governance.md)
- [API ADR-022: Platform, Infrastructure, and Shared Organization](../api/ADR-022-platform-infrastructure-shared-organization.md)
- [API ADR-014: Permission Evaluation Strategy](../api/ADR-014-permission-evaluation-strategy.md) — location only; the mechanism is carried forward

## Amended by this set
- [API ADR-003: Validation Layer Separation](../api/ADR-003-validation-layer-separation.md) — where database-dependent rules run
- [API ADR-006: Event-Driven Architecture](../api/ADR-006-event-driven-architecture.md) — events are one cycle-breaking instrument of several, and run on the Event Publication Registry
- [API ADR-023: Job Consolidation and Reliable Execution](../api/ADR-023-job-consolidation-and-reliable-execution.md) — gains scheduledFor and the runAsync/runIn surface
- [API ADR-013: Entity Association Pattern](../api/ADR-013-entity-association-pattern.md) — an owning-side reference may cross a module boundary, a `mappedBy` back-reference may not

## Related Documentation
- [ADR umbrella index](../ADR-INDEX.md)
- [API ADR index](../api/ADR-INDEX.md)
- [Testing ADR index](../testing/ADR-INDEX.md)
