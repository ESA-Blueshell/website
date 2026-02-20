# Frontend Test Coverage Expansion Plan

## Goal
Reach and sustain high frontend test coverage with deterministic, offline tests that run in an isolated frontend container (no dependent services and no external network calls), while keeping tests as a specification of intended behavior.

## Baseline Snapshot (2026-02-20)
- Unit: `74` files, `134` tests, all passing.
- E2E (mocked Playwright): `8` tests, all passing.
- Global coverage:
- Line: `75.78%` (`7875/10392`)
- Branch: `70.57%` (`585/829`)
- Function: `55.99%` (`173/309`)
- Coverage artifacts:
- `frontend/coverage/unit/index.html`
- `frontend/coverage/unit/lcov.info`
- `frontend/coverage/unit/lcov-report/index.html`

## Current Snapshot (2026-02-20, post Step 8B+forms pass)
- Unit: `90` files, `182` tests, all passing.
- E2E (mocked Playwright): `8` tests.
- Latest run status (2026-02-20): `mobile-chrome 4/4 pass`, `chromium 4/4 fail` due deterministic timeout issues now under active stabilization.
- Global coverage:
- Line: `89.78%` (`9327/10389`)
- Branch: `72.28%` (`962/1331`)
- Function: `46.90%` (`212/452`)
- Delta from baseline:
- Line: `+14.00pp`
- Branch: `+1.71pp`
- Function: `-9.09pp`

## Current Risks (From Coverage Audit)
- Largest uncovered line hotspots:
- `src/App.vue` (`446` uncovered lines)
- `src/components/form/UserForm.vue` (still high; now partially covered with new rule/flow tests)
- `src/components/form/EventSignUpForm.vue` (partially covered; error/edge branches remain)
- `src/components/base/EventDetails.vue` (`163`)
- `src/components/base/EventCalendar.vue` (`149`)
- `src/components/base/PastEventsPane.vue` (`128`)
- `src/plugins/validation.ts` (message/rule coverage improved; branch edges remain)
- `src/pages/membership/MembershipSignUp.vue` (`87`)
- `src/plugins/store.ts` (`28`)
- `src/pages/Events.vue` (`25`)
- Low-coverage clusters:
- `src/components/form` still branch/function heavy despite stronger line uplift
- `src/components/base` line coverage `61.86%`
- `src/plugins/validation.ts` branch coverage still below target
- `src/utils/datetime.ts` line coverage `50.00%`
- Current global risk shift:
- Line coverage is now near Milestone 3 trajectory, but function coverage dropped because denominator increased significantly after expanding instrumented form test surface.
- Need branch/function-first additions for form save paths, conditional flows, and plugin/store logic.

## Coverage Milestones
- Milestone 1: Line `>=80%`, Branch `>=74%`, Function `>=60%`
- Milestone 2: Line `>=86%`, Branch `>=80%`, Function `>=68%`
- Milestone 3: Line `>=90%`, Branch `>=85%`, Function `>=75%`

## E2E Expansion Targets (Best-Practice)
- Expand from smoke-level coverage to broad behavior coverage of all major routes and critical user journeys with deterministic network mocking.
- Keep e2e tests fully offline and stack-independent:
- no dependency on backend/db/rabbitmq containers,
- no external network requests,
- seeded deterministic fixtures only.
- Standardize Playwright architecture:
- dedicated fixture/mocks layer per domain (`auth`, `events`, `management`, `blogs`, `partners`),
- helper actions and assertions for repeated flows,
- stable selectors (`getByRole`, explicit labels, `data-testid` only where semantics are insufficient).
- Enforce anti-flake controls:
- isolate storage/session per test,
- no cross-test shared mutable state,
- explicit readiness assertions before interactions,
- route-wait assertions based on UI state rather than arbitrary sleeps,
- maintain `retries: 0` locally; optional limited retries in CI only for infrastructure noise.
- Add route-matrix coverage:
- navbar links,
- partner links,
- footer links,
- deprecated-route guards (including Trackmania),
- auth redirects (`requiresAuth`, `requiresAdmin`) for member/board/admin personas.
- Add flow-level coverage:
- membership sign-up happy path + validation failures,
- events overview, create/edit upsert flows (mocked),
- sign-up token/edit flows,
- management list filtering and mutation confirmation paths,
- blogs list/detail rendering and not-found fallbacks.
- Add e2e reporting and governance:
- Playwright HTML/blob reports as artifacts,
- flake triage log in this plan,
- gating policy: no new e2e allowed without deterministic mocks and one negative-path assertion.

## E2E Milestones
- E2E Milestone A: stabilize existing 8 tests across Chromium + mobile with zero flakes.
- E2E Milestone B: at least one deterministic e2e spec for every major top-level page family (`home`, `events`, `membership`, `management`, `blogs`, `partners`, `auth`, `esports`).
- E2E Milestone C: complete route/link integrity matrix in e2e (navbar + partners + footer + deprecated routes).
- E2E Milestone D: full critical user-journey suite with positive and negative paths for each domain.

## Execution Protocol
After each implementation step:
- Update this plan with `Completed`, `Why`, `Learned`.
- Record current coverage deltas and the files improved.
- Record any reusable test pattern extraction done (helpers, composables, utils, stubs).

Spec-first quality gate for every test change:
- Define expected behavior first from product intent (not from current implementation quirks).
- If observed behavior conflicts with intent, fix implementation first, then assert intended behavior in tests.
- Do not lock in broken behavior with assertions, snapshots, or call-order expectations.
- Include at least one negative/error-path assertion where relevant.
- Validate no unintended loops/retries are introduced (explicit call-count checks for watchers/effects where applicable).

Behavior triage rule:
- `Ambiguous intent`: capture assumption in this plan before writing assertions.
- `Clear intent + broken behavior`: implementation fix is required in the same step as tests.
- `Clear intent + correct behavior`: write/expand tests only.

## Progress Journal
### Step 1: Deterministic Frontend Test Baseline
- Status: Completed
- Completed:
- Isolated unit and mocked Playwright suites run through `docker compose ... run --rm --no-deps frontend`.
- Coverage reporting enabled in `frontend/vitest.config.ts`.
- Why:
- Guarantees tests run without relying on backend/db/rabbit/ports.
- Learned:
- Keeping all API calls mocked in unit tests is mandatory for repeatability and speed.

### Step 2: Per-Page Test Topology and Link Integrity
- Status: Completed
- Completed:
- Added page-level tests in `tests/unit/pages/**` with one page per test file.
- Added navbar/route integrity checks and partner/footer link checks.
- Why:
- Established broad behavior coverage for major user flows and navigation safety.
- Learned:
- Route-resolution assertions catch missing internal links early with low maintenance cost.

### Step 3: Regression Guards for Event Flows
- Status: Completed
- Completed:
- Added event flow tests for upsert behavior, event passing, and loop-avoidance call-count assertions.
- Why:
- Event pages are mutation-heavy and prone to watcher/reload regressions.
- Learned:
- Explicit call-count assertions are effective for proving absence of reactive fetch loops.

### Step 4: Hotspot and Duplication Analysis
- Status: Completed (this update)
- Completed:
- Audited `lcov.info` to identify highest uncovered files and low-coverage clusters.
- Audited tests for repeated patterns (store mocks, API mocks, settle/hrefs usage).
- Why:
- Prioritization must be based on coverage impact and maintenance cost.
- Learned:
- Current bottleneck is concentrated in `App.vue`, `components/form/*`, `components/base/*`, and plugin validation/store logic.
- Repetitive mock wiring across page tests justifies shared test utilities.

### Step 5: Spec-First Testing Policy Integration
- Status: Completed (this update)
- Completed:
- Integrated a mandatory spec-first gate into the execution protocol.
- Added explicit behavior triage rules to avoid codifying broken behavior in tests.
- Why:
- Coverage increases are only valuable if assertions represent desired product behavior.
- Learned:
- A strict “intent first, fix first” rule reduces the risk of test suites becoming a mirror of regressions.

### Step 6: Extract Reusable Test Harnesses
- Status: Completed
- Completed:
- Added shared unit-test utilities in `tests/unit/helpers/testUtils.ts`:
- `settle`, `hrefs`
- `withVuexUseStore`, `withVueRouter`
- `createTestStore` (available for incremental adoption)
- Kept compatibility by re-exporting helpers from `tests/unit/pages/helpers.ts`.
- Refactored representative page tests (`Events`, `Login`, `Account`, `Address`) to use shared module-mock helpers instead of repeated inline wiring.
- Why:
- Reduces duplication and speeds creation of branch-focused tests.
- Learned:
- Standardized module-mock helpers remove high-friction boilerplate while preserving per-test control over route/store state.
- Centralized async settling utilities simplify future behavior-first test additions.

### Step 7: Convert `App.vue` Template-String Test to Behavioral Unit Tests
- Status: Completed
- Completed:
- Replaced the template-string test with behavior-focused tests in `tests/unit/app/App.navbar.test.ts`.
- Added assertions for:
- desktop vs mobile navigation behavior,
- board/admin visibility branches,
- role-loading success/error paths,
- cookie acceptance flow,
- dark-mode toggle persistence,
- logout redirect behavior for auth-required routes,
- konami-code easter egg behavior.
- Corrected implementation to match intended navigation behavior:
- added missing mobile links for `blogs` and `geoguessr`,
- later removed `trackmania` navbar links and route assertions after deprecation to prevent dead-link coverage.
- Why:
- Current `App.vue` test validates text presence but contributes almost no runtime coverage.
- Learned:
- Behavior tests expose intent mismatches quickly; in this case mobile/desktop navbar parity gaps were found and fixed.
- Spec changes must be reflected quickly in tests to keep them as an intent contract rather than stale behavior snapshots.
- App-level tests are high-leverage for line and branch improvements when they cover mounted lifecycle logic.

### Step 8: Form and Base Component Coverage Push
- Status: In Progress
- Completed (8A):
- Added focused base-component suites:
- `tests/unit/components/base/EventDetails.test.ts`
- `tests/unit/components/base/EventCalendar.test.ts`
- `tests/unit/components/base/PastEventsPane.test.ts`
- Fixed implementation bug in `src/components/base/EventDetails.vue`:
- banner state is now refreshed when either event `id` or `banner` flag changes, preventing stale banner rendering.
- Updated navbar route/link tests for Trackmania deprecation:
- removed `/esports/trackmania` from `tests/unit/router/navbarRoutes.test.ts`
- removed Trackmania navbar assertions from `tests/unit/app/App.navbar.test.ts`
- Why:
- These files were among the largest uncovered base-component hotspots and had high line-count impact.
- Learned:
- Base components are coverage-leverage points; a small number of targeted suites moved global line coverage materially.
- Spec-first tests revealed a real stale-state bug (`EventDetails` banner watcher) that would otherwise persist.
- Completed (8B):
- Added form-focused unit suites with one component per test file:
- `tests/unit/components/form/AddressForm.test.ts`
- `tests/unit/components/form/AnswersForm.test.ts`
- `tests/unit/components/form/CommitteeForm.test.ts`
- `tests/unit/components/form/EventForm.test.ts`
- `tests/unit/components/form/EventSignUpForm.test.ts`
- `tests/unit/components/form/GuestForm.test.ts`
- `tests/unit/components/form/MembershipForm.test.ts`
- `tests/unit/components/form/SurveyForm.test.ts`
- `tests/unit/components/form/UserForm.test.ts`
- Added field-level form suites:
- `tests/unit/components/form/fields/AnswerField.test.ts`
- `tests/unit/components/form/fields/QuestionField.test.ts`
- Added explicit validation-message suite:
- `tests/unit/plugins/validation.rules.test.ts`
- Why:
- Directly addresses the instruction to fully test form validations and validation messages, while preserving per-component test-file separation.
- Learned:
- Rule-assertion tests with explicit `Form`/`VvField` stubs scale better for broad form coverage than trying to fully render every Vuetify control.
- Explicit message assertions in a dedicated rules suite reduce duplication and prevent drift between form-level rules and user-visible error text.
- Remaining (8C Planned):
- Remaining (8C Planned):
- Stabilize current Chromium e2e failures (`events`, `home-and-banners`, `management-lists`) by removing hidden test nondeterminism and strengthening readiness/navigation assertions.
- Why:
- E2E reliability is now the main blocker for scaling the suite.
- Expected impact (8C):
- Restores stable baseline so large e2e expansion can proceed safely.

### Step 9: Plugin/Utility Branch Hardening
- Status: In Progress
- Planned:
- Expand tests for `validation.ts`, `store.ts`, `router.ts`, and `datetime.ts`.
- Add explicit negative/error-path branches and guard-condition assertions.
- Completed (partial):
- Added `tests/unit/plugins/validation.rules.test.ts` to assert message contract for core rules (`required`, `alphaNum`, `min/max chars`, numeric bounds, email/student-email, password composition, cross-field match, date/dateTime, phone).
- Learned:
- Validation-message regressions are now detected at plugin level before they leak into multiple form UIs.
- Why:
- These modules have dense branch logic and broad blast radius.
- Expected impact:
- Branch coverage uplift with relatively small, focused tests.

### Step 10: CI Coverage Gates
- Status: Pending
- Planned:
- Enable enforceable `thresholds` in `vitest.config.ts` after Milestone 1 is stable.
- Keep HTML and lcov outputs as artifacts.
- Why:
- Prevents silent coverage regression.
- Expected impact:
- Sustained coverage quality over future feature work.

### Step 11: E2E Foundation Refactor
- Status: Pending
- Planned:
- Restructure Playwright helpers into domain fixtures and deterministic mock builders.
- Add reusable authenticated contexts for `guest`, `member`, `board`, and `admin` roles.
- Add common readiness helper to assert app bootstrap complete before test interactions.
- Why:
- A scalable e2e suite requires clean fixture boundaries and repeatable setup.
- Expected impact:
- Lower e2e maintenance cost and fewer flaky startup/navigation failures.

### Step 12: E2E Route and Link Matrix
- Status: Pending
- Planned:
- Add e2e specs validating all navbar links and partner/footer links resolve correctly.
- Include deprecated-route assertions (`/esports/trackmania` should resolve to NotFound and remain unreachable from nav).
- Why:
- Link and route regressions are high-impact and often missed by isolated unit tests.
- Expected impact:
- Continuous detection of broken navigation and stale/deprecated routes.

### Step 13: E2E Critical Journey Expansion
- Status: Pending
- Planned:
- Add full e2e user journeys for:
- auth/login/logout + redirect behavior,
- membership signup and form validation failures,
- events create/edit/sign-up update flows (mocked APIs),
- management mutation flows with confirmation dialogs.
- Why:
- Journey-level tests validate integration between router, state, forms, and rendering.
- Expected impact:
- Higher confidence in business-critical frontend behavior under realistic browser execution.

### Step 14: E2E Negative Paths and Loop Guards
- Status: Pending
- Planned:
- Add explicit failure-path e2e specs for API errors, empty states, and auth denial.
- Assert absence of runaway request loops by tracking intercepted API call counts on key pages.
- Why:
- Prevents silent regressions in reactive effects and error handling.
- Expected impact:
- Better branch realism and improved production incident prevention.

### Step 15: E2E Governance and Reporting
- Status: Pending
- Planned:
- Publish Playwright reports and traces as CI artifacts.
- Maintain e2e flake register in this plan with root cause and mitigation per incident.
- Add CI gate that blocks on flaky spec markers without tracked mitigation.
- Why:
- Coverage growth without reliability governance degrades trust in test signals.
- Expected impact:
- Sustainable high-signal e2e suite aligned with best practices.
