# Bulk Actions — Consolidated Reimplementation Design

## 0. How the seven lenses were reconciled

Seven lenses split into two camps:

- **"Go client-side + 6 modals"** (frontend-data, modal-ux recommendation, endpoints, testing-migration): move preview into the browser, one modal per action, one submit endpoint per action.
- **"The FE is fine, the real bug is backend preview/execute duplication"** (complexity critique, correctness critique, modal-ux *critique*, divergence critique): keep one modal (or thin per-action wrappers), fix the duplication by extracting a shared *decision function* both preview and execute call.

The maintainer's brief is the tie-breaker and it is explicit: **per-action modals, client-side preview from existing FE data where feasible, one submit endpoint per action.** So we adopt the first camp's *shape*. But every critique of that camp landed on the same three load-bearing facts, which I verified in the actual code, and they force a **tiered** rather than uniform client-side move:

1. **FE `memberSince` is the *earliest* membership start** (`useMemberRows.ts:30-34`, `reduce` to min), but backend fee resolution uses the ***latest*** membership start (`maxByOrNull { it.startDate }`, `BulkContributionReminderHandlers.kt:45`). A naive JS port of `resolveFeeType` would use the wrong date and silently send wrong fee tiers. This kills "just port FeeResolution to TS" unless we also expose the latest-membership start.
2. **Resume basis period is `periods.findLatest()`** (`BulkResumeMembershipCommandHandlers.kt:74`), a global most-recent period that the FE does **not** load and that is **not** `selectedPeriod`. The frontend-data lens guessed `selectedPeriod`; that guess is wrong and would flip WILL_RESUME↔WILL_START_NEW. Confirmed by correctness + frontend-data critiques.
3. **Reminder/incasso execute has an `email.isBlank()` skip that preview lacks** (`BulkContributionReminderHandlers.kt:146`), plus `lastSentOn` comes from an audit table (`reminders.findLastReminderForUserAndPeriod`) the FE never loads. Pure silent-divergence + a real UX signal that only the server has.

**Verdict adopted:** the maintainer's target is correct as a *direction*, but "compute preview from FE data" is only *sound* for the actions whose entire decision input already lives in the FE. It is *unsound* for the two email actions (fee tier + audit) unless we ship more data than we have today. So the consolidated design is a **tiered preview**, not a blanket client-side move, and it pairs that with the backend camp's non-negotiable fix (single decision function per action) so that even the server-computed actions stop diverging.

---

## 1. Target architecture

```
┌─────────────────────────── FRONTEND ───────────────────────────┐
│ MemberManager.vue                                               │
│   ├─ useMemberSelection (unchanged)                             │
│   ├─ useMemberRows / usePaidToggle (unchanged, source of truth  │
│   │     for FE-derived state: paid set, memberships, period)    │
│   └─ <BulkActionHost :action="active"> dynamic <component :is>  │
│         renders exactly ONE of 6 per-action dialogs             │
│                                                                 │
│ 6 per-action dialogs (thin), each ~70-110 lines:                │
│   MarkPaidDialog / MarkUnpaidDialog        → FE preview          │
│   EndMembershipDialog                       → FE preview         │
│   ResumeMembershipDialog                    → SERVER preview     │
│   ReminderDialog / IncassoDialog            → SERVER preview     │
│                                                                 │
│ Shared FE building blocks:                                      │
│   BulkDialogScaffold.vue   (title/counts/table/sort/confirm)    │
│   useBulkPreview<TRow>()    (rows, counts, reinclude, submit)   │
│   bulkDisposition.ts        (label/color/reason pure helpers)   │
│   feePreview.ts             (TS port of FeeResolution, tier 2)  │
└─────────────────────────────────────────────────────────────────┘
                              │ HTTP (one submit endpoint per action)
┌─────────────────────────── BACKEND ────────────────────────────┐
│ Controllers unchanged as 2 files, but preview endpoints removed │
│ for stateless actions; kept only for reminder/incasso/resume.   │
│                                                                 │
│ Per action: ONE decide() pure-ish domain function.              │
│   decide() → List<Decision>   (disposition+reason+fee context)  │
│   preview handler = decide() → rows                             │
│   execute handler = decide() → apply side effects               │
│ ⇒ preview and execute can no longer diverge: same code path.    │
└─────────────────────────────────────────────────────────────────┘
```

### Preview tiering (the core resolved decision)

| Action | Preview source | Why |
|---|---|---|
| mark-paid | **FE** | Decision = `userId ∈ paidUserIds`? SKIPPED(ALREADY_PAID):INCLUDED. All data in FE. No server preview endpoint. |
| mark-unpaid | **FE** | Mirror: `userId ∈ paidUserIds`? INCLUDED:SKIPPED(NOT_PAID). No server preview endpoint. |
| end-membership | **FE** | Decision = any membership with `endDate==null && startDate < today`. Memberships fully loaded. No server preview endpoint. **Needs server `today`** (see §4). |
| resume-membership | **SERVER** | Depends on `periods.findLatest()` basis period the FE does not have, and per-user full membership history with end dates. Keep preview endpoint. |
| contribution-reminder | **SERVER** | Fee tier needs *latest*-membership start (FE has *earliest*); `alreadyPaid`; `lastSentOn` from audit. Keep preview endpoint. |
| incasso-notification | **SERVER** | Same as reminder + incasso-flag check. Keep preview endpoint. |

This directly answers the maintainer's item (2): we compute preview from FE data **wherever feasible** — and "feasible" is precisely the three stateless actions. We explicitly do **not** port fee resolution or duplicate audit/period lookups into the browser, because the correctness and frontend-data critiques both showed that path is a divergence generator, not a divergence fix.

---

## 2. Endpoints (maintainer item 4: one submit endpoint per action)

Rename to action-named paths so the generated client methods are unambiguous. Keep the two controller files (splitting into 6 controller files was rejected by the complexity + testing critiques as cosmetic churn; the controllers are 3-line dispatchers).

### ContributionBulkController.kt

| Method | Path | Request → Response | Notes |
|---|---|---|---|
| POST | `/contributions/bulk/mark-paid` | `BulkMarkPaidRequest{userIds, contributionPeriodId}` → `BulkActionResult` | execute only; **no preview** |
| POST | `/contributions/bulk/mark-unpaid` | `BulkMarkUnpaidRequest{userIds, contributionPeriodId}` → `BulkActionResult` | execute only; **no preview** |
| POST | `/contributionReminders/bulk/preview` | `BulkContributionReminderRequest{userIds, contributionPeriodId, cutoffDate, paymentDueDate}` → `BulkPreviewResult` | preview kept |
| POST | `/contributionReminders/bulk/execute` | `…+{includedUserIds, feeTypeOverrides}` → `BulkActionResult` | |
| POST | `/incassoNotifications/bulk/preview` | `BulkIncassoNotificationRequest{userIds, contributionPeriodId, cutoffDate, expectedIncassoDate}` → `BulkPreviewResult` | preview kept |
| POST | `/incassoNotifications/bulk/execute` | `…+{includedUserIds, feeTypeOverrides}` → `BulkActionResult` | |

### MembershipBulkController.kt

| Method | Path | Request → Response | Notes |
|---|---|---|---|
| POST | `/memberships/bulk/end/preview` | `BulkEndMembershipRequest{userIds}` → `BulkPreviewResult{…, serverToday}` | **kept but demoted**: only used to hand the FE the server `today`; FE still computes rows. Alternatively fold `today` into a cheap `/system/today` — see decision D4. |
| POST | `/memberships/bulk/end/execute` | `BulkEndMembershipRequest{userIds}` → `BulkActionResult` | |
| POST | `/memberships/bulk/resume/preview` | `BulkResumeMembershipRequest{userIds}` → `BulkPreviewResult` | preview kept |
| POST | `/memberships/bulk/resume/execute` | `BulkResumeMembershipRequest{userIds}` → `BulkActionResult` | |

**Preview no longer receives `includedUserIds`/`feeTypeOverrides`** (they were passed and ignored — divergence + complexity lenses both flagged this dead asymmetry, `BulkActionConfirmDialog` sent `props.userIds` and `{}`). Preview is now purely the immutable server truth; operator overrides live only in FE state and are sent only to `execute`. This is the "preview is immutable, execute is operator-driven" fix the complexity lens converged on.

Request DTOs become **immutable `data class`es with `val`** and Jakarta constraints (`@NotEmpty userIds`, `@Positive contributionPeriodId`, `@NotNull` dates) per the repo's jakarta-validation convention.

No versioned/parallel endpoints, no adapter layer, no feature flag — the testing-migration lens proposed a 3-phase dual-stack rollout; the critique of that lens (and the "no external consumers of these board-only endpoints" reality) rejected it as coverage debt. This is a single-PR internal refactor.

---

## 3. Backend: kill preview↔execute duplication (the fix every lens agreed on)

For **every** action that still has server logic, extract ONE decision function; preview and execute both call it. This is the single most important backend change and it is what actually satisfies the maintainer's "split logic / dual behaviors / divergence-prone" complaint — not the endpoint rename.

Resume already does this well (`classifyUser` + `ResumeOutcome` sealed class). Generalize the pattern:

```kotlin
// per action, in the domain/application layer
data class ReminderDecision(
    val userId: Long, val name: String, val memberType: MemberType,
    val memberSince: LocalDate?, val disposition: BulkRowDisposition,
    val reason: BulkRowReason?, val recommendedFeeType: BulkFeeType?,
    val amount: Double?, val lastSentOn: LocalDate?,
)

fun decideReminder(userId, periodId, period, cutoffDate, services): ReminderDecision {
    val activeMembership = memberships.findByUserId(userId).maxByOrNull { it.startDate }
    val memberType = activeMembership?.memberType ?: MemberType.REGULAR
    val recommendedFeeType = resolveFeeType(memberType, activeMembership?.startDate, cutoffDate)
    val alreadyPaid = contributions.existsByUserIdAndPeriodId(userId, periodId)
    val emailMissing = users.findById(userId).email.isBlank()   // ← now visible in preview
    return when {
        recommendedFeeType == null -> …EXCLUDED, HONORARY
        emailMissing               -> …SKIPPED,  NO_EMAIL      // ← NEW reason, ends silent skip
        alreadyPaid                -> …WARNING,  ALREADY_PAID
        else                       -> …INCLUDED
    }
}
```

- **Preview handler:** map `decide()` → `BulkPreviewRow`.
- **Execute handler:** call `decide()`; if `disposition==INCLUDED` OR (`disposition==WARNING` AND `userId ∈ includedUserIds`) → apply, using `feeTypeOverrides[userId] ?: recommendedFeeType` for the amount; else count skipped/excluded.

Effects of this on the confirmed bugs:
- **email-blank silent skip** → gone: `NO_EMAIL` is a first-class `BulkRowReason`, shown in preview. (New enum value; add to `BulkRowReason`.)
- **midnight `LocalDate.now()` drift** (end + resume) → gone within a request: `decide()` takes a single `actionDate` computed once at the top of each handler and threaded in. (The correctness lens rightly downgraded cross-request midnight risk as low, but making `today` a parameter is free and also enables the FE-end-membership tiering in §4.)
- **fee-override amount divergence** → contained: preview shows the recommended amount; execute recomputes from override; FE shows the effective amount from a local lookup as the operator changes the selector (no new preview round-trip). This is the complexity lens's "immutable preview + local effective recompute", and it only needs a **tiny** TS fee helper keyed off the row's *recommended* type and the period fees the FE already has — not a full port.

Add **fee-override validation** on execute (endpoints critique gap): reject `feeTypeOverrides` for users who are EXCLUDED/HONORARY; reject overrides for users not in `includedUserIds`; missing override → recommended type. Enforce with a guard in the execute handler (throwing a `ValidationException` mapped to 400), consistent with jakarta-style FE errors.

**Fee-override wiring is already live** in the reminder/incasso execute handlers (`feeTypeOverrides[userId] ?: recommendedFeeType`, line 152) — the testing-migration lens's worry that it was dead code is **false for reminder/incasso** (verified). Keep it; just add the validation guard.

---

## 4. `serverToday` for end-membership FE preview

End-membership is stateless enough for FE preview, but "started today" is a same-day boundary and browser TZ ≠ server TZ (system tests already pin Europe/Amsterdam for exactly this flake — see commit `4deb7a13`). Resolution: the **end-preview endpoint returns `serverToday: LocalDate`** in an extended envelope, and the FE dialog uses that date (not `new Date()`) to compute `startDate < serverToday`. This is one extra field, negligible cost, and eliminates the TZ pitfall the frontend-data and correctness lenses both raised.

Rejected alternative: a generic `/system/today`. Folding it into the already-present end-preview call is simpler and keeps the FE decision atomic (D4).

Note this means end-membership technically keeps a preview *call*, but the **row computation is FE-side** — the endpoint returns only counts + `serverToday`, not per-row dispositions. This honors "compute from FE data" while staying correct.

---

## 5. Frontend components

### 5.1 Shared scaffold (extract FIRST, before splitting — modal-ux critique's ordering)

- **`BulkDialogScaffold.vue`** — BaseModal wrapper: title/icon slot, counts summary bar, the sortable preview `<v-data-table>` with a `#row-extra` slot (fee selector / reinclude checkbox columns), cancel/confirm buttons, `useSubmitFeedback` wiring. Renders whatever `rows` + column config it's handed; knows nothing about action type.
- **`useBulkPreview.ts`** — generic composable `<TRow extends {userId; disposition; reason}>`: holds `rows`, `counts` (derived), `reincludeOverrides` map, computed `includedUserIds` (`INCLUDED ∪ (WARNING ∧ reincluded)`), `submitting`, and a `submit(fn)` runner. Both FE-preview and server-preview dialogs use it; the only difference is how `rows` are populated (local compute vs API call).
- **`bulkDisposition.ts`** — pure: `dispositionLabel`, `dispositionColor`, `rowColorClass`, `reasonLabel(reason)` (incl. new `NO_EMAIL`), `formatMemberSince`. Lifted verbatim out of the current monolith (they're already pure, `BulkActionConfirmDialog.vue:172-232`).
- **`feePreview.ts`** — minimal: `effectiveAmount(recommendedFeeType | overrideType, period)` for live re-display as the operator changes a row's fee selector. **Not** a port of `resolveFeeType` — the recommended type always comes from the server preview row; this only maps type→€ from `period.{fullYearFee,halfYearFee,alumniFee}`.

### 5.2 Six per-action dialogs (thin)

Each imports the scaffold + `useBulkPreview`, declares only its own form state and columns, and defines `loadPreview()` + `onSubmit()`:

- **MarkPaidDialog.vue / MarkUnpaidDialog.vue** — `loadPreview()` computes rows locally from `paidUserIds` + selection; no fee/date UI; `onSubmit` → `markPaid`/`markUnpaid`. Simplest (~70 lines).
- **EndMembershipDialog.vue** — calls end-preview once to get `serverToday` + counts, computes rows locally from `memberships` using `serverToday`; no fee UI; `onSubmit` → `endMembership`.
- **ResumeMembershipDialog.vue** — server preview (WILL_RESUME / WILL_START_NEW / ALREADY_ACTIVE / NO_CONTRIBUTION_PERIOD); read-only rows; `onSubmit` → `resumeMembership`.
- **ReminderDialog.vue** — `paymentDueDate` + `cutoffDate` inputs (with client validation: cutoff within period), server preview, fee-type selector column + reinclude column, `onSubmit` sends `includedUserIds` + `feeTypeOverrides`.
- **IncassoDialog.vue** — same as Reminder but `expectedIncassoDate` and the INCASSO_MISMATCH warning row.

### 5.3 Host

**`MemberManager.vue`** renders `<component :is="dialogFor[activeAction]">` bound to the selection + period + memberships it already holds. Each dialog owns its own open/close + reset; `MemberManager` loses the giant per-action branching it currently threads to the shared modal. `BulkActionsMenu.vue` unchanged (still emits the chosen action).

**Why 6 dialogs and not a strategy object inside one modal:** the modal-ux critique argued a strategy pattern is cheaper. But the maintainer explicitly asked for one modal per action, and with the scaffold + `useBulkPreview` doing ~80% of the work each dialog is ~80 lines with **zero action-type conditionals**. The critique's "you just move branching into the composable" is avoided because `useBulkPreview` is genuinely action-agnostic (it never switches on action) — the differences are the `loadPreview`/`onSubmit` closures each dialog supplies, which is composition, not branching.

---

## 6. What stays server-side and why (money/rules source of truth)

- **Fee tier resolution** (`resolveFeeType`) — stays server-side; input (latest-membership start) not reliably in FE. FE only maps a *known* type to €.
- **`alreadyPaid`, `lastSentOn`, incasso-flag** — server (DB/audit). `alreadyPaid` for mark-paid/unpaid *is* mirrored (that's the whole point of `paidUserIds`), so those two go FE; for reminder/incasso the same fact is combined with fee/audit data that isn't in FE, so it stays server.
- **Resume basis period** (`findLatest`) + membership classification — server.
- **All authz** — `@PreAuthorize` on every endpoint incl. the retained previews; FE preview is display-only and never a security boundary (auth enforced at execute).
- **All mutations + amount actually recorded** — server, in `@Transactional` execute handlers via `decide()`.

Staleness/concurrency (a board colleague edits state mid-dialog): accepted as eventual-consistency for previews, but **execute always re-runs `decide()` against live DB**, so it never acts on stale preview — it acts on truth. `BulkActionResult` already returns `applied/skipped/queued`, so the FE can surface "N rows changed since preview" from the delta. No checksum/version mechanism (the correctness lens proposed one but never defined it; the critique correctly called it placeholder — re-running `decide()` on execute is the real guarantee and it already exists).

---

## 7. Tests (maintainer item 3: readability; testing-migration, tempered by its critique)

- **Backend unit:** one `decide*Test.kt` per action testing the decision function across boundaries (cutoff edge `start == cutoff`, ALUMNI, HONORARY→EXCLUDED, blank email→NO_EMAIL, resume basis-period in/out, end started-today). This is where logic coverage concentrates now.
- **Backend IT:** keep per-endpoint but slim to contract + authz (403 non-board, 400 invalid override, happy path). **Add an invariant IT per server-preview action asserting preview disposition counts == execute outcome counts** for an unchanged DB — this is the regression net against the class of bug the maintainer is reacting to.
- **FE unit (vitest):** test the three FE-preview compute functions (mark-paid/unpaid/end) against `useMemberRows` snapshots incl. the `serverToday` boundary; test `useBulkPreview` reinclude/includedUserIds math once (shared).
- **E2E / system:** keep flows; for FE-preview actions drop the preview mock and only mock execute; keep preview mocks for reminder/incasso/resume. Update `MemberManagerBulkHelper` selectors for the new dialog components.

OpenAPI: paths + DTOs change ⇒ regen required. Run `generate-openapi-local.sh` (H2, DB-free per memory) then `lint:gen`, else byte-diff fails. Verify `BulkRowReason` now includes `NO_EMAIL` and that `BulkActionType`/`BulkRowDisposition` export as `$ref` enums (endpoints lens flagged possible inlining — check `@Schema` present, which it already is on these enums).

---

## 8. Sequencing (single PR, safe internal order)

1. Backend: add `decide*()` functions; refactor existing preview+execute handlers to call them (behavior-preserving except the NO_EMAIL fix). Add `NO_EMAIL` reason. Add override validation guard. Add `serverToday` to end-preview envelope.
2. Backend: rename endpoints to action paths; drop mark-paid/unpaid preview endpoints; drop `includedUserIds`/`feeTypeOverrides` from preview requests.
3. Regen OpenAPI + client; `lint:gen`.
4. FE: extract scaffold + `useBulkPreview` + `bulkDisposition` + `feePreview` from the monolith.
5. FE: build 6 dialogs; wire `MemberManager` host; delete `BulkActionConfirmDialog.vue`.
6. Tests: decision unit tests, preview==execute invariants, FE compute unit tests, update e2e/system.
