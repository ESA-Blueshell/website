# ADR-007: Testing and Quality Gates

## Status
Superseded by the [testing ADR set](../testing/ADR-INDEX.md).

The contract-safety rules below remain in force and are carried forward by
[testing ADR-005](../testing/ADR-005-frontend-coverage-parity.md): mapper and schema
tests after an OpenAPI regeneration, and route-guard coverage for authenticated and
unauthorised paths. The gate definitions are replaced —
[ADR-005](../testing/ADR-005-frontend-coverage-parity.md) sets the unit numbers and
[ADR-006](../testing/ADR-006-frontend-end-to-end-completeness.md) the end-to-end
completeness rule. Retained for history.

## Context
Frontend contracts changed significantly after API refactors. Without strong quality gates, regressions can slip in around payload mapping, route guards, and critical user flows.

## Decision
Adopt layered frontend quality gates with contract-focused tests.

### Required CI Gates
- `yarn typecheck`
- `yarn lint`
- `yarn build`

### Test Strategy
- Unit tests for mappers, schemas, and pure composables
- Component tests for critical forms and guard-driven UI states
- End-to-end coverage for critical journeys (auth, membership, event sign-up, management actions)

### Contract Safety Rules
- Changes to generated API clients require adapter/mapping review
- Command mappers and validators must be tested for nullability/required-field behavior
- Route guard behavior must be test-covered for authenticated and unauthorized paths

## Consequences

### Positive
- Earlier detection of API/frontend contract regressions
- Safer iterative refactors
- Better confidence in boundary behavior

### Negative
- Additional test maintenance effort
- CI duration can increase without suite segmentation

## Guidelines

### DO
- Prioritize tests at domain boundaries (adapter mapping, validation, guards)
- Keep tests deterministic with stable fixtures and mocked transport
- Tag critical-path E2E tests for predictable CI use

### DO NOT
- Rely on manual QA only for contract changes
- Skip mapper/schema tests after OpenAPI regeneration
- Merge architectural refactors without boundary regression checks

## References
- Vue testing guide: https://vuejs.org/guide/scaling-up/testing.html
- Vitest: https://vitest.dev/
- Playwright: https://playwright.dev/
