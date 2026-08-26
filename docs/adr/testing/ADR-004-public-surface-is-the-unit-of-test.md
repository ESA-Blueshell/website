# ADR-004: The Public Surface Is the Unit of Test

## Status
Accepted

## Context

[ADR-003](ADR-003-coverage-counters-thresholds-and-ratchet.md) sets a 100% METHOD
floor. A floor decides how much; it does not decide what a test is allowed to
address. Without that second rule the floor is satisfiable in ways that make the
codebase worse — by widening visibility until every helper is directly reachable,
or by reflection.

The question also has to be answered before "unit" means anything. Fowler notes
that the pyramid never defined it, and borrows Jay Fields' distinction between
**solitary** tests, where every collaborator is a double, and **sociable** tests,
where real collaborators participate — declining to prefer either, since both are
[worth writing](https://martinfowler.com/articles/practical-test-pyramid.html).
That ambiguity is fine for the size of a test. It is not fine for its target.

## Decision

**A test addresses the public surface of the class under test. Private methods are
never tested directly.**

Private methods are covered as a consequence of exercising the public methods that
reach them. Nothing else is permitted to reach them: visibility is not widened for
testing, and reflection is not used to invoke them.

### When a private method wants its own test

That desire is the signal. A private method complex enough to deserve tests
independent of its callers is doing work that is not the enclosing class's
responsibility, and the response is to **extract it into its own class with its own
public method** — where it can be tested directly, named honestly, and reused.

The alternative — widening the method to `internal` so the test source set can
reach it — is rejected. It couples tests to implementation detail, so a refactor
that reorganises internals breaks tests without any behaviour changing, and it
quietly makes the class's contract larger than its author intended.

### Why the coverage gate counts private methods anyway

The METHOD counter in [ADR-003](ADR-003-coverage-counters-thresholds-and-ratchet.md)
cannot distinguish visibility — JaCoCo records none — so private methods are inside
the denominator. That is consistent with this ADR rather than in tension with it:

- A private method is reachable only from inside its class.
- So an uncovered private method proves some public path through that class was
  never exercised.
- The remedy is a test through the public surface, or deleting a method nothing can
  reach.

Both remedies are the right action. The gate therefore points at a real defect
even though it cannot name it precisely.

### What this means for design

The rule creates pressure, and the pressure is the point. A class whose public
surface cannot reach all of its own code has either dead code or a hidden
responsibility. Under a 100% METHOD gate that stops being a matter of taste and
becomes a build failure with two honest fixes: delete it, or extract it.

## Implementation status

Decided. No tooling enforces the negative half of this rule — nothing prevents a
test from widening visibility or using reflection to reach a private method.
Review is the only check. A detekt rule flagging reflection into private members
from test sources would close the gap if it proves necessary.

## Consequences

### Positive
- **Tests survive refactoring.** Coupled only to the public surface, they keep
  passing while internals are reorganised — which is the property that makes a
  suite worth having.
- **The gate becomes a design signal.** Uncovered private methods surface dead code
  and misplaced responsibilities instead of merely lowering a percentage.
- **Extraction is the cheapest way out**, so the rule pushes toward smaller classes
  with named responsibilities rather than toward more test scaffolding.

### Negative
- **Some private logic is genuinely awkward to reach.** A branch that only triggers
  on a rare collaborator state may need elaborate setup through the public surface,
  and the resulting test is worse than a direct one would have been.
- **Extraction has a cost.** Applied mechanically it produces many single-method
  classes, which is its own kind of unreadable.
- **The rule is unenforced.** A reviewer who does not know it exists will accept an
  `internal` widening without comment.

### Neutral
- **This does not settle solitary versus sociable.** A test may double every
  collaborator or none; the rule constrains only what it *targets*. The resource
  bounds in [ADR-001](ADR-001-test-pyramid-and-layer-placement.md) are what decide
  which layer it belongs to.

## Related ADRs
- [ADR-001: The Test Pyramid and Layer Placement](ADR-001-test-pyramid-and-layer-placement.md) — what decides a test's layer
- [ADR-003: Coverage Counters, Thresholds and the Ratchet](ADR-003-coverage-counters-thresholds-and-ratchet.md) — the METHOD floor this rule interprets
- [API ADR-001: Multi-Layered Domain-Driven Architecture](../api/ADR-001-multi-layered-domain-driven-architecture.md) — the boundaries extraction respects
