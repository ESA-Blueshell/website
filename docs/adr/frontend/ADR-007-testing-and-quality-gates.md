# ADR-007: Testing and Quality Gates

## Status
Accepted

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
