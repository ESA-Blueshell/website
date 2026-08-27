# ADR-006: Frontend End-to-End Completeness

## Status
Accepted

## Context

[ADR-001](ADR-001-test-pyramid-and-layer-placement.md) makes frontend e2e the layer
that covers what a user can do, now that the system layer is bounded by real-stack
risk. "Covers every action" is the one requirement in this set with no natural
metric behind it.

A percentage does not express it. A spec that loads a page and asserts a heading
scores well on lines while exercising no action at all — the failure mode Marick
identified when
[coverage becomes a target](http://www.exampler.com/testing-com/writings/coverage.pdf).
Conversely a page can be thoroughly driven and still be missing entirely, because
nothing points at it.

The current position, at `b4a0ca80`: `src/plugins/router.ts` declares 56 `path:`
entries, 8 of them redirects, across 49 named routes. 22 spec files reach 26
distinct `goto` targets. **13 routes have no spec** — mostly dynamic routes
(`:id`, `:token`) and admin surfaces including `/committees/manage`, `/myapps`,
`/unauthorized` and `/login/forgor`.

No tool asserts this. A search for anything relating vue-router records to
Playwright navigations found nothing, and the absence is reported here rather than
papered over with a plausible package name.

## Decision

Completeness is enforced by **two checks that fail for different reasons**, because
neither is sufficient alone.

### 1. Route inventory — is the page reachable at all?

A generated inventory parses the route records from `src/plugins/router.ts` and the
`goto` targets from `tests/e2e/**/*.spec.ts`, and fails when a route has no spec
that visits it. It must resolve nested route records and match dynamic segments
(`/events/:id` against `/events/42`), and redirect-only records are exempt — a
redirect's destination is what needs proving.

This catches the failure a percentage cannot see: a page nothing points at
contributes no uncovered lines, because its code never loads.

### 2. Istanbul function threshold — did the handlers fire?

A `functions` threshold on the e2e istanbul output, ratcheting on the
[ADR-003](ADR-003-coverage-counters-thresholds-and-ratchet.md) dates. Reaching a
route proves the page renders; executing its functions is what proves the actions
on it were taken.

Enforced with `nyc check-coverage` against `coverage/e2e/coverage-final.json`,
which supports per-file and per-metric thresholds. `monocart-coverage-reports` was
considered and rejected: it enforces only global watermarks, with no per-file
threshold.

### Unit and e2e coverage are gated separately, never merged

`scripts/convert-frontend-coverage.mjs` merges coverage maps today. That merged
number must not be gated. The unit suite and the instrumented bundle report
branches and functions differently, so an aggregate of the two is not a quantity
with a meaning — and the branch discrepancy is precisely the `v8` defect that
[ADR-005](ADR-005-frontend-coverage-parity.md) moves the unit suite off. Merging
remains available for reporting.

### Why not a data-testid inventory

Requiring every interactive `data-testid` in `src` to be referenced by some spec is
the most literal reading of "every action", and it was rejected. There are 280
unique testids, and they are already shared with the Kotlin Playwright helpers in
`tests/system` — renaming one breaks two suites today. A gate that made
every testid load-bearing in a third place would make renaming a selector an
expensive act, which is a poor trade for a rule that routes plus functions already
approximate.

## Implementation status

Decided, none of it built.

- The route-inventory check is a script this repository has to own. It parses
  vue-router records and Playwright `goto` calls; no third-party tool does this.
- The 13 uncovered routes are the starting backlog. The check should land in
  report-only mode and go blocking once they are covered or consciously exempted.
- `nyc` is not currently a dependency, and no CI step reads `coverage/e2e`.
- The initial e2e functions percentage is unmeasured. It is set from the first
  report-only run rather than guessed here.

## Consequences

### Positive
- **The two checks fail for different reasons**, so neither can be satisfied by
  gaming the other. Reachability and execution are both required.
- **The route inventory is self-maintaining.** Adding a route to `router.ts` without
  a spec fails immediately, at the moment the author has the context.
- **A concrete backlog exists** — 13 named routes rather than a vague sense that
  e2e could be better.

### Negative
- **A bespoke script is a maintenance liability.** It parses two moving targets, and
  vue-router's nested and dynamic forms are exactly where a naive parser breaks.
- **`goto` is not the only way to reach a route.** A spec navigating by clicking a
  link visits a page the inventory will record as uncovered, producing false
  failures and pressure to add pointless `goto` calls.
- **Function coverage in a browser still does not prove assertions ran.** A spec
  that clicks everything and asserts nothing passes both checks.

### Neutral
- **Route count is not a quality measure.** 49 routes covered by 49 shallow specs
  would satisfy the inventory. It is a floor on breadth, not a statement about
  depth — depth is what the functions threshold is for, and even that is a proxy.

## Related ADRs
- [ADR-001: The Test Pyramid and Layer Placement](ADR-001-test-pyramid-and-layer-placement.md) — why this layer carries the action coverage
- [ADR-003: Coverage Counters, Thresholds and the Ratchet](ADR-003-coverage-counters-thresholds-and-ratchet.md) — the ratchet dates
- [ADR-005: Frontend Coverage Parity](ADR-005-frontend-coverage-parity.md) — the unit-layer gate and its provider change
