# Frontend Test Coverage Expansion Plan

## Goal
Reach and sustain near-full frontend coverage with deterministic, offline tests that do not depend on external services.

## Current Baseline
- Unit tests for common components, composables, plugins, and utils.
- Added page-level tests under `tests/unit/pages/**` with per-page files.
- Added route/nav integrity checks under `tests/unit/router/**` and `tests/unit/app/**`.
- Coverage output configured in Vitest (`coverage/unit` as text + HTML + lcov).

## Coverage Targets
- Global line coverage: >= 90%.
- Global branch coverage: >= 85%.
- Critical paths (`src/pages/**`, `src/components/common/**`, `src/plugins/**`): >= 95% line coverage.

## Expansion Phases
1. Stabilize deterministic unit tests
- Keep all API calls mocked (`@/services/api`) in unit tests.
- Keep router/store interactions mocked and assertion-focused.
- Ensure no test performs real network I/O.

2. Increase behavioral assertions per page
- Add validation for all success/failure branches in auth and activation pages.
- Extend manager page tests for delete/error/reload paths.
- Extend event flows for approval/retry/error outcomes.

3. Close branch coverage gaps in reusable components
- Add branch-focused tests for form components (`UserForm`, `AddressForm`, `MembershipForm`, `EventForm`).
- Add accessibility and state-transition checks for banners, rows, and modals.

4. Harden loop/regression detection
- Add explicit call-count assertions on watchers and reactive effects.
- Add tests for idempotent upsert behavior across manager and events pages.

5. Coverage gates in CI
- Enforce thresholds in `vitest.config.ts` once baseline is stable.
- Publish `coverage/unit/lcov.info` and HTML report artifacts.

## Test Topology
- Unit root: `tests/unit/**`
- Page tests: `tests/unit/pages/**` (one page per test file)
- Common component tests: `tests/unit/components/**`
- Router/nav integrity tests: `tests/unit/router/**` and `tests/unit/app/**`
- E2E tests: `tests/e2e/**`

## Remaining High-Value Additions
- Add negative-path tests for API errors where only happy-path is currently asserted.
- Add deeper assertions for route-query synchronization in membership sign-up.
- Add snapshot-like structural checks for critical navigation/footer layouts.
