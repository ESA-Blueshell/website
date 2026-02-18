# Frontend System Test Master Plan

Last updated: 2026-02-18

## Status Legend
- `[x] ✅` Completed
- `[ ]` Not completed / still planned

## Phase 0: Baseline Inventory (already completed)
- `[x] ✅` Inventory all frontend routes from `frontend/src/plugins/router.ts`.
- `[x] ✅` Inventory all existing frontend system test suites in `api/src/test/kotlin/net/blueshell/api/system/frontend`.
- `[x] ✅` Map major existing helper usage and identify reusable helper entry points.
- `[x] ✅` Identify routed pages that currently have no direct system test coverage.
- `[x] ✅` Identify high-risk behavior gaps (auth guards, membership stepper, event sign-ups, job manager, blogs, app shell).

## Frontend Type Rule
- `[x] ✅` Do not create or maintain API type aliases in `frontend/src/services/api/index.ts`.
- `[x] ✅` Use generated API transport type names directly in components/pages (e.g. `BlogResponse`, `CommitteeDetailResponse`, `MembershipResponse`, `ContributionPeriodResponse`).

## Route + Page Coverage Checklist

### Core/public pages
- `[ ]` `/` Home: cover top-level CTAs, partner links, and key navigation actions.
- `[ ]` `/contact`: cover membership CTA navigation.
- `[ ]` `/committees`: cover committee list loading and empty/error states.
- `[ ]` `/membership`: cover membership landing CTA navigation.
- `[ ]` `/documents`: cover document download actions.
- `[ ]` `/aboutus`: page rendering smoke.
- `[ ]` `/board`: cover board accordion expand/collapse behavior.
- `[x] ✅` `/esports` redirect to `/esports/competitive-scene`.
- `[ ]` `/esports/competitive-scene`: page rendering + primary content smoke.
- `[ ]` `/esports/league-of-legends`: page rendering smoke.
- `[ ]` `/esports/counter-strike-2`: page rendering smoke.
- `[ ]` `/esports/valorant`: page rendering smoke.
- `[ ]` `/esports/rocketleague`: page rendering smoke.
- `[ ]` `/esports/geoguessr`: page rendering smoke.
- `[ ]` `/partners/become-a-partner`: page rendering smoke.
- `[ ]` `/partners/el-nino`: page rendering smoke.
- `[ ]` `/partners/marketing-maatwerk`: page rendering smoke.
- `[ ]` `/events/circuitShowdown`: page rendering smoke.
- `[ ]` `/blogs`: cover blogs list fetch + click-through.
- `[ ]` `/blogs/:id`: cover blog detail fetch + render.
- `[ ]` `/:pathMatch(.*)*`: not-found rendering.

### Auth/account/recovery pages
- `[x] ✅` `/login`: disabled account error, wrong-password error, successful login, create-account navigation, forgot-password username carryover.
- `[x] ✅` `/login/forgor`: reset request email flow.
- `[x] ✅` `/account`: full account edit success path and persistence assertions.
- `[x] ✅` `/account/create`: account creation + disabled account behavior + validation uniqueness cases.
- `[x] ✅` `/account/reset-password`: reset password and authenticate with new password.
- `[x] ✅` `/account/activate/member`: activation success + invalid token.
- `[x] ✅` `/account/activate/user`: user activation success + invalid/missing token flows.
- `[x] ✅` `/account/addresses/:id?`: create + update own address.

### Event pages
- `[ ]` `/events`: complete event list behavior including sign-up interactions and past-page query sync assertions.
- `[x] ✅` `/events/calendar` redirect to `/events`.
- `[x] ✅` `/events/create`: committee restriction, approval behavior, banner fetch after create.
- `[x] ✅` `/events/edit/:id`: update behavior, board/member approval behavior, banner fetch after edit.
- `[ ]` `/events/signups/:id`: verify respondents table and question/answer totals rendering.
- `[ ]` `/events/signups/edit/:accessToken`: guest edit/update/delete sign-up flow.

### Management pages
- `[x] ✅` `/committees/manage`: create/delete/update committees, add/remove committee members, committee role effects.
- `[x] ✅` `/members/manage`: period-based visibility, board create/update member, activation email sent, start membership, end membership.
- `[ ]` `/members/manage`: add coverage for delete member, resume membership, admin-delete guard.
- `[x] ✅` `/contributions/manage`: add period, switch periods, paid/unpaid toggles, paid/unpaid filtering by multiple fields.
- `[ ]` `/contributions/manage`: add coverage for period edit/delete and selected period retention.
- `[x] ✅` `/addresses/manage`: add address for user without address, delete address for user with address.
- `[ ]` `/addresses/manage`: add coverage for edit existing address and role-based delete disabling.
- `[x] ✅` `/recovery/manage`: resend activation, send password reset, active/inactive filtering by multiple fields.
- `[ ]` `/recovery/manage`: add loading/disable behavior assertions during recovery actions.
- `[ ]` `/management/jobs`: list executions, refresh, retry failed jobs, admin-only guard enforcement.

### Legacy/redirect routes
- `[x] ✅` `/account/reset-password/:username/:token` redirect behavior.
- `[x] ✅` `/account/activate/member/:token` redirect behavior.
- `[x] ✅` `/account/activate/user/:username/:token` redirect behavior.

## App Shell / Global Behavior Checklist
- `[x] ✅` Top navigation routing for desktop.
- `[x] ✅` Drawer navigation routing for mobile.
- `[x] ✅` Role-based management menu visibility (board/admin).
- `[x] ✅` Account menu: logout behavior and session invalidation.
- `[x] ✅` Cookie snackbar acceptance persistence.
- `[x] ✅` Dark-mode toggle persistence.
- `[x] ✅` Auth guard redirect to login with `redirect` query.
- `[x] ✅` Admin-only guard redirect to home.

## Event Domain Functional Checklist
- `[x] ✅` Committee member create restricted to own committees.
- `[x] ✅` Board create can target any committee.
- `[x] ✅` Non-board-created events are not approved by default.
- `[x] ✅` Board can approve from event card and edit form.
- `[x] ✅` Banner upload/download behavior after create and edit.
- `[x] ✅` Calendar month navigation and event-detail popup.
- `[x] ✅` Event card copy-link and add-to-calendar (`.ics`) behavior.
- `[x] ✅` Event card navigation to sign-ups and edit pages.
- `[x] ✅` Event deletion from event card.
- `[ ]` Logged-in user sign-up create/update/delete via event card form.
- `[ ]` Guest sign-up create/update/delete and token-based edit route flow.
- `[ ]` Members-only event sign-up restrictions for non-members.
- `[ ]` Sign-up count synchronization when sign-ups mutate.
- `[ ]` Event sign-ups page response tables and totals (OPEN/RADIO/CHECKBOX).
- `[ ]` Past events pagination + `page` query synchronization.

## Membership Domain Functional Checklist
- `[x] ✅` Join-now entry point can start signup and create disabled account.
- `[ ]` Step 2 confirmation: resend activation email from membership stepper.
- `[ ]` Step 2 confirmation: "I've activated - Sign in" redirect behavior.
- `[ ]` Post-login return to membership signup stepper using redirect query.
- `[ ]` Step 3 address completion in membership flow.
- `[ ]` Step 4 membership completion and persisted membership.
- `[ ]` Already-member branch redirect behavior and snackbar message.

## Route Consistency / Dead Route Checklist
- `[ ]` Validate and resolve mismatch between drawer links and router definitions.
- `[ ]` Resolve un-routed pages:
- `[ ]` `frontend/src/pages/esports/Trackmania.vue`
- `[ ]` `frontend/src/pages/partners/Connectworks.vue`
- `[ ]` `frontend/src/pages/partners/Dekimo.vue`

## Execution Plan (ordered)

### Phase 1: App Shell + Route Guards
- `[x] ✅` Implement `AppShellSystemTest` for nav, drawer, cookie banner, dark mode, logout, role menus, and guards.
- `[x] ✅` Add redirect tests for all legacy auth/activation routes.
- `[x] ✅` Run targeted system tests + frontend `eslint` + `typecheck`.
  - Executed on 2026-02-18: targeted system tests passed and static checks are now green.

### Phase 2: Auth/Recovery/User Activation Completion
- `[x] ✅` Extend account page tests for successful user updates.
- `[x] ✅` Add `/account/activate/user` success and failure scenarios.
- `[x] ✅` Run targeted system tests + frontend `eslint` + `typecheck`.

### Phase 3: Membership Full Flow
- `[ ]` Add multi-step membership signup tests (steps 2/3/4 + resume-after-login + already-member branch).
- `[ ]` Run targeted system tests + frontend `eslint` + `typecheck`.

### Phase 4: Event Sign-up + Sign-ups Pages
- `[ ]` Add logged-in and guest sign-up create/update/delete coverage.
- `[ ]` Add `/events/signups/:id` table/totals coverage.
- `[ ]` Add `/events/signups/edit/:accessToken` guest edit coverage.
- `[ ]` Run targeted system tests + frontend `eslint` + `typecheck`.

### Phase 5: Management Gap Completion
- `[ ]` Extend address/member/contribution/recovery manager tests for remaining gaps.
- `[ ]` Add job manager system tests.
- `[ ]` Run targeted system tests + frontend `eslint` + `typecheck`.

### Phase 6: Public/Content Route Suite
- `[ ]` Add route smoke and key-action coverage for public pages (blogs, documents, board, committees, esports, partners, contact, not-found).
- `[ ]` Run targeted system tests + frontend `eslint` + `typecheck`.

### Phase 7: Route Consistency and Regression Safety
- `[ ]` Add regression tests for redirects/dead links/router consistency.
- `[ ]` Resolve mismatches between linked pages and routed pages.
- `[ ]` Run full frontend system test suite + frontend `eslint` + `typecheck`.

## Quality Gates (required per phase)
- `[x] ✅` Run relevant frontend system test subset via API service (`docker compose -f docker-compose.dev.yml run --service-ports api ...`).
- `[x] ✅` Run frontend lint for changed frontend scope.
  - Executed on 2026-02-18 via `docker compose -f docker-compose.dev.yml exec -T frontend yarn eslint`; passing after excluding `dist/**` in eslint ignores.
- `[x] ✅` Run frontend typecheck for changed frontend scope.
  - Executed on 2026-02-18 via `docker compose -f docker-compose.dev.yml exec -T frontend yarn typecheck`; resolve type issues in component imports/usages instead of adding aliases in `frontend/src/services/api/index.ts`.
- `[ ]` Confirm no helper duplication and keep reusable helpers outside test base where appropriate.
