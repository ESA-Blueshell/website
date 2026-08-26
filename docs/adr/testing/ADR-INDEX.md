# Testing Architecture Decision Records Index

This index tracks decisions about how the repository is tested and what the
build gates on. The set is cross-cutting: the pyramid spans the Kotlin API, the
Vue frontend, and the suites that drive both at once.

## ADR List

### The pyramid

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [001](ADR-001-test-pyramid-and-layer-placement.md) | The Test Pyramid and Layer Placement | Accepted | Five named layers, the question each answers, and the rule that decides where a test belongs |
| [004](ADR-004-public-surface-is-the-unit-of-test.md) | The Public Surface Is the Unit of Test | Accepted | Tests address public methods; a private method wanting its own test is an extraction signal |

### Coverage gating

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [002](ADR-002-coverage-gates-apply-to-changed-code.md) | Coverage Gates Apply to Changed Code | Accepted | Gates bind the code a change touches, not the legacy tail |
| [003](ADR-003-coverage-counters-thresholds-and-ratchet.md) | Coverage Counters, Thresholds and the Ratchet | Accepted | 100% method on merged execution data, 80% branch per source set, exclusions, and the dated schedule to 100% |

### Frontend

| # | Title | Status | Summary |
|---|-------|--------|---------|
| [005](ADR-005-frontend-coverage-parity.md) | Frontend Coverage Parity | Accepted | The same numbers and the same ratchet on the frontend, and the harness change that makes them measurable |
| [006](ADR-006-frontend-end-to-end-completeness.md) | Frontend End-to-End Completeness | Accepted | Route inventory plus a function threshold as the checkable form of "covers every action" |

## Superseded by this set
- [API ADR-011: Testing Strategy](../api/ADR-011-testing-strategy.md)
- [Frontend ADR-007: Testing and Quality Gates](../frontend/ADR-007-testing-and-quality-gates.md)

## Related Documentation
- [ADR umbrella index](../ADR-INDEX.md)
- [API ADR index](../api/ADR-INDEX.md)
- [Frontend ADR index](../frontend/ADR-INDEX.md)
