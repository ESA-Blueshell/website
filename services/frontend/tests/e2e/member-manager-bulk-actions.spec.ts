import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"
import type {Page, Request} from "@playwright/test"

// ── Fixture data ───────────────────────────────────────────────────────────────
//
// The bulk-action preview is now computed entirely client-side (see
// src/utils/bulkCompute.ts): the dialogs derive each row's disposition, reason,
// fee tier, and amount from the data the page already loaded — users, memberships,
// contribution periods, and the paid set — and only ever POST to the execute
// endpoints. So these tests mock ONLY that loaded data plus the execute endpoints;
// there are no preview route mocks, and every disposition asserted below is the
// result of real client computation.
//
// The member manager auto-selects the latest contribution period on mount (the
// ContributionPeriodList slide-group is `mandatory`), so period-relative actions
// are enabled without an explicit period click.
//
// The reminder/incasso dialogs now wrap their inputs in a validated v-form and gate
// confirm: a payment-due / expected-incasso date STRICTLY AFTER today and a half-year
// cutoff WITHIN the selected period are required, or clicking confirm is a no-op (no
// execute POST). The tests therefore set a future date (FUTURE_DATE) and an explicit,
// deterministic cutoff (CUTOFF_DATE) before confirming. The cutoff defaults to a
// period-relative midpoint, so we override it to keep the fee-tier split stable:
// membership startDate <= cutoff → FULL_YEAR_FEE, startDate > cutoff → HALF_YEAR_FEE.

// A date safely after "today" for any realistic CI clock (the dialogs compare the
// payment-due / expected-incasso date against the real Amsterdam "today").
const FUTURE_DATE = "2099-12-31"
// Explicit half-year cutoff within PERIOD. 55 (2025-07-01) falls after it → half-year;
// 51 (2024-01-01) falls before it → full-year.
const CUTOFF_DATE = "2025-06-01"

const PERIOD = {
  id: 251,
  startDate: "2025-01-01",
  endDate: "2025-12-31",
  halfYearFee: 10,
  fullYearFee: 20,
  alumniFee: 5,
}

const CONTRIBUTION_PERIODS = [PERIOD]

const USERS = [
  // 51 — regular, unpaid, incasso payer, started before cutoff → reminder WARNING(PAYS_VIA_INCASSO),
  //      incasso INCLUDED (full-year fee)
  {id: 51, fullName: "Alice Regular", firstName: "Alice", lastName: "Regular", username: "alice-regular", email: "alice@example.com", enabled: true, roles: ["MEMBER"]},
  // 52 — honorary → mark-paid/unpaid SKIPPED(HONORARY); reminder/incasso EXCLUDED(HONORARY)
  {id: 52, fullName: "Bob Honorary", firstName: "Bob", lastName: "Honorary", username: "bob-honorary", email: "bob@example.com", enabled: true, roles: ["MEMBER"]},
  // 53 — regular, ALREADY PAID → mark-paid SKIPPED(ALREADY_PAID); reminder WARNING(ALREADY_PAID)
  {id: 53, fullName: "Carol Paid", firstName: "Carol", lastName: "Paid", username: "carol-paid", email: "carol@example.com", enabled: true, roles: ["MEMBER"]},
  // 54 — regular, NO EMAIL → reminder/incasso SKIPPED(NO_EMAIL)
  {id: 54, fullName: "Dave NoEmail", firstName: "Dave", lastName: "NoEmail", username: "dave-noemail", email: "", enabled: true, roles: ["MEMBER"]},
  // 55 — regular WITHOUT incasso, started AFTER cutoff → reminder INCLUDED with half-year fee
  {id: 55, fullName: "Erin HalfYear", firstName: "Erin", lastName: "HalfYear", username: "erin-halfyear", email: "erin@example.com", enabled: true, roles: ["MEMBER"]},
  // 56 — alumni, incasso payer → reminder WARNING(PAYS_VIA_INCASSO); incasso INCLUDED (alumni fee)
  {id: 56, fullName: "Frank Alumni", firstName: "Frank", lastName: "Alumni", username: "frank-alumni", email: "frank@example.com", enabled: true, roles: ["MEMBER"]},
  // 57 — regular WITHOUT incasso flag → incasso WARNING(INCASSO_MISMATCH)
  {id: 57, fullName: "Gina NoIncasso", firstName: "Gina", lastName: "NoIncasso", username: "gina-noincasso", email: "gina@example.com", enabled: true, roles: ["MEMBER"]},
  // 58 — membership ended WITHIN latest period → resume INCLUDED(WILL_RESUME)
  {id: 58, fullName: "Hank Resumable", firstName: "Hank", lastName: "Resumable", username: "hank-resumable", email: "hank@example.com", enabled: true, roles: ["MEMBER"]},
  // 59 — membership ended BEFORE latest period → resume INCLUDED(WILL_START_NEW)
  {id: 59, fullName: "Ivy StartNew", firstName: "Ivy", lastName: "StartNew", username: "ivy-startnew", email: "ivy@example.com", enabled: true, roles: ["MEMBER"]},
  // 60 — active membership (no endDate) → resume SKIPPED(ALREADY_ACTIVE); end INCLUDED
  {id: 60, fullName: "Jack Active", firstName: "Jack", lastName: "Active", username: "jack-active", email: "jack@example.com", enabled: true, roles: ["MEMBER"]},
]

const MEMBERSHIPS = [
  {id: 151, userId: 51, memberType: "REGULAR", startDate: "2024-01-01", endDate: null, incasso: true},
  {id: 152, userId: 52, memberType: "HONORARY", startDate: "2023-01-01", endDate: null, incasso: false},
  {id: 153, userId: 53, memberType: "REGULAR", startDate: "2024-03-01", endDate: null, incasso: true},
  {id: 154, userId: 54, memberType: "REGULAR", startDate: "2024-02-01", endDate: null, incasso: true},
  {id: 155, userId: 55, memberType: "REGULAR", startDate: "2025-07-01", endDate: null, incasso: false},
  {id: 156, userId: 56, memberType: "ALUMNI", startDate: "2024-01-15", endDate: null, incasso: true},
  {id: 157, userId: 57, memberType: "REGULAR", startDate: "2024-01-10", endDate: null, incasso: false},
  {id: 158, userId: 58, memberType: "REGULAR", startDate: "2023-01-01", endDate: "2025-06-15", incasso: true},
  {id: 159, userId: 59, memberType: "ALUMNI", startDate: "2020-01-01", endDate: "2021-06-15", incasso: true},
  {id: 160, userId: 60, memberType: "REGULAR", startDate: "2024-01-01", endDate: null, incasso: true},
]

// Carol (53) is already paid for the selected period; everyone else is unpaid.
const CONTRIBUTIONS = [{id: 300, userId: 53, contributionPeriodId: 251}]

// ── Helpers ────────────────────────────────────────────────────────────────────

/** Execute-endpoint fulfilment shared by every action (the FE only needs a 200 with
 *  a non-null body to treat the action as applied and close the dialog). */
async function stubExecuteEndpoints(page: Page) {
  const executeGlobs: RegExp[] = [
    /\/contributions\/bulk\/mark-paid$/,
    /\/contributions\/bulk\/mark-unpaid$/,
    /\/contributionReminders\/bulk\/execute$/,
    /\/incassoNotifications\/bulk\/execute$/,
    /\/memberships\/bulk\/end\/execute$/,
    /\/memberships\/bulk\/resume\/execute$/,
  ]
  for (const glob of executeGlobs) {
    await page.route(glob, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({applied: 1, queued: 0, skipped: 0}),
      })
    })
  }
}

/**
 * Arm a listener that resolves with the first POST request to a path matching the
 * given fragment. Must be installed BEFORE the confirm click.
 */
function captureExecute(page: Page, pathFragment: string): Promise<Request> {
  return page.waitForRequest(
    (req) => req.method() === "POST" && req.url().includes(pathFragment),
  )
}

/**
 * Re-route an execute endpoint to refuse the selection with the api's 409 contract:
 * an RFC 7807 ProblemDetail whose field errors name the offending user ids. Playwright
 * matches routes newest-first, so this overrides the 200 stub for the rest of the test.
 */
async function refuseExecute(
  page: Page,
  glob: RegExp,
  errors: Array<{code: string; message: string; values: number[]}>,
) {
  await page.route(glob, async (route) => {
    await route.fulfill({
      status: 409,
      contentType: "application/problem+json",
      body: JSON.stringify({
        type: "about:blank",
        title: "Conflict",
        status: 409,
        detail: "The selection no longer matches the current data.",
        errors: errors.map((e) => ({objectName: "bulkContribution", field: "userIds", ...e})),
      }),
    })
  })
}

/** Count GET /users calls, so a reload of the table can be asserted (or ruled out). */
function countUserReloads(page: Page): () => number {
  let calls = 0
  page.on("request", (req) => {
    if (req.method() === "GET" && new URL(req.url()).pathname.endsWith("/users")) calls += 1
  })
  return () => calls
}

async function setupPage(page: Page) {
  await installApiMocks(page, {
    users: USERS,
    memberships: MEMBERSHIPS,
    contributionPeriods: CONTRIBUTION_PERIODS,
    contributions: CONTRIBUTIONS,
  })
  await stubExecuteEndpoints(page)

  await loginAsBoard(page.context())
  await page.setViewportSize({width: 1440, height: 900})
  await page.goto("/user-manager")
  await expect(page.getByTestId("member-manager-table")).toBeVisible({timeout: 30_000})
  // The list auto-selects the latest period; wait for its select button to confirm
  // periods loaded so period-relative actions are enabled.
  await expect(page.getByTestId("contribution-period-select-btn-251")).toBeVisible({timeout: 10_000})
}

async function selectRows(page: Page, ids: number[]) {
  for (const id of ids) {
    await page.getByTestId(`member-manager-checkbox-${id}`).click()
    await expect(page.getByTestId(`member-manager-checkbox-${id}`).locator("input")).toBeChecked()
  }
}

async function openAction(page: Page, actionTestId: string) {
  await page.getByTestId("bulk-actions-menu-btn").click()
  await expect(page.getByTestId("bulk-actions-menu")).toBeVisible()
  await page.getByTestId(actionTestId).click()
  await expect(page.getByTestId("bulk-action-dialog")).toBeVisible({timeout: 10_000})
  await expect(page.getByTestId("bulk-action-preview-table")).toBeVisible({timeout: 10_000})
}

// ── Tests ──────────────────────────────────────────────────────────────────────
//
// Desktop (chromium) project only. Mobile no longer supports selection / bulk
// actions (#454); see playwright.config.ts.

test.describe("member manager bulk actions", () => {
  // eslint-disable-next-line no-empty-pattern
  test.beforeEach(async ({}, testInfo) => {
    if (testInfo.project.name === "mobile-chrome") {
      test.skip()
    }
  })

  // ── Selection chrome (no dialog) ──────────────────────────────────────────────

  test("checkboxes appear in each row and header", async ({page}) => {
    await setupPage(page)

    await expect(page.getByTestId("member-manager-header-checkbox")).toBeVisible()
    await expect(page.getByTestId("member-manager-checkbox-51")).toBeVisible()
    await expect(page.getByTestId("member-manager-checkbox-52")).toBeVisible()
    await expect(page.getByTestId("member-manager-checkbox-53")).toBeVisible()
  })

  test("selecting rows enables the bulk-actions menu", async ({page}) => {
    await setupPage(page)

    await expect(page.getByTestId("bulk-actions-menu-btn")).toBeDisabled()
    await page.getByTestId("member-manager-checkbox-51").click()
    await expect(page.getByTestId("member-manager-checkbox-51").locator("input")).toBeChecked()
    await expect(page.getByTestId("bulk-actions-menu-btn")).not.toBeDisabled()
  })

  test("header checkbox selects then deselects all visible rows", async ({page}) => {
    await setupPage(page)

    await page.getByTestId("member-manager-header-checkbox").click()
    await expect(page.getByTestId("member-manager-checkbox-51").locator("input")).toBeChecked()
    await expect(page.getByTestId("member-manager-checkbox-52").locator("input")).toBeChecked()

    await page.getByTestId("member-manager-header-checkbox").click()
    await expect(page.getByTestId("member-manager-checkbox-51").locator("input")).not.toBeChecked()
    await expect(page.getByTestId("member-manager-checkbox-52").locator("input")).not.toBeChecked()
    await expect(page.getByTestId("bulk-actions-menu-btn")).toBeDisabled()
  })

  test("bulk actions menu shows all six actions", async ({page}) => {
    await setupPage(page)

    await page.getByTestId("member-manager-checkbox-51").click()
    await page.getByTestId("bulk-actions-menu-btn").click()

    await expect(page.getByTestId("bulk-actions-menu")).toBeVisible()
    await expect(page.getByTestId("bulk-action-mark-paid")).toBeVisible()
    await expect(page.getByTestId("bulk-action-mark-unpaid")).toBeVisible()
    await expect(page.getByTestId("bulk-action-send-reminder")).toBeVisible()
    await expect(page.getByTestId("bulk-action-send-incasso")).toBeVisible()
    await expect(page.getByTestId("bulk-action-end-membership")).toBeVisible()
    await expect(page.getByTestId("bulk-action-resume-membership")).toBeVisible()
  })

  test("selected rows persist when a search filter is applied", async ({page}) => {
    await setupPage(page)

    await page.getByTestId("member-manager-header-checkbox").click()
    await expect(page.getByTestId("member-manager-checkbox-51").locator("input")).toBeChecked()

    await page.getByTestId("member-manager-search-input").locator("input").fill("Alice")
    await expect(page.getByTestId("member-manager-row-51")).toBeVisible()
    await expect(page.getByTestId("member-manager-row-52")).not.toBeVisible()

    // Selection persists across filtering.
    await expect(page.getByTestId("member-manager-checkbox-51").locator("input")).toBeChecked()
    await expect(page.getByTestId("bulk-actions-menu-btn")).not.toBeDisabled()
  })

  // ── Mark paid (client-side: honorary skip, already-paid skip) ─────────────────

  test.describe("mark paid", () => {
    test("computes SKIPPED for honorary and already-paid, INCLUDED otherwise, and posts to execute", async ({page}) => {
      await setupPage(page)
      await selectRows(page, [51, 52, 53])
      await openAction(page, "bulk-action-mark-paid")

      // 51 regular unpaid → INCLUDED; 52 honorary → SKIPPED; 53 already paid → SKIPPED.
      await expect(page.getByTestId("bulk-preview-disposition-51")).toContainText("Included")
      await expect(page.getByTestId("bulk-preview-disposition-52")).toContainText("Skipped")
      await expect(page.getByTestId("bulk-preview-disposition-53")).toContainText("Skipped")
      await expect(page.getByTestId("bulk-preview-note-52")).toContainText("Honorary")
      await expect(page.getByTestId("bulk-preview-note-53")).toContainText("Already paid")

      // Counts: 3 selected, 1 will apply, 2 skipped.
      await expect(page.getByTestId("bulk-action-counts")).toContainText("3 selected")
      await expect(page.getByTestId("bulk-action-counts")).toContainText("1 will apply")
      await expect(page.getByTestId("bulk-action-counts")).toContainText("2 skipped")

      const req = captureExecute(page, "/contributions/bulk/mark-paid")
      await page.getByTestId("bulk-action-confirm-btn").click()
      const body = (await req).postDataJSON()
      expect(body.userIds).toEqual([51])
      expect(body.contributionPeriodId).toBe(251)

      await expect(page.getByTestId("bulk-action-dialog")).not.toBeVisible({timeout: 5_000})
    })
  })

  // ── Mark unpaid (client-side: only the already-paid row is includable) ────────

  test.describe("mark unpaid", () => {
    test("computes INCLUDED only for the already-paid row and posts to execute", async ({page}) => {
      await setupPage(page)
      await selectRows(page, [51, 53])
      await openAction(page, "bulk-action-mark-unpaid")

      // 51 unpaid → SKIPPED(NOT_PAID); 53 paid → INCLUDED.
      await expect(page.getByTestId("bulk-preview-disposition-51")).toContainText("Skipped")
      await expect(page.getByTestId("bulk-preview-note-51")).toContainText("Not paid")
      await expect(page.getByTestId("bulk-preview-disposition-53")).toContainText("Included")

      await expect(page.getByTestId("bulk-action-counts")).toContainText("1 will apply")

      const req = captureExecute(page, "/contributions/bulk/mark-unpaid")
      await page.getByTestId("bulk-action-confirm-btn").click()
      const body = (await req).postDataJSON()
      expect(body.userIds).toEqual([53])
      expect(body.contributionPeriodId).toBe(251)

      await expect(page.getByTestId("bulk-action-dialog")).not.toBeVisible({timeout: 5_000})
    })
  })

  // ── Reminder (client-side: exclude honorary, skip no-email, warn already-paid,
  //    fee tiers by cutoff) ──────────────────────────────────────────────────────

  test.describe("send reminder", () => {
    test("computes dispositions, fee tiers and amounts, then posts included set to execute", async ({page}) => {
      await setupPage(page)
      // 51 incasso payer → WARNING(PAYS_VIA_INCASSO), full-year; 52 honorary EXCLUDED;
      // 53 already-paid → WARNING(ALREADY_PAID); 54 no-email SKIPPED;
      // 55 non-incasso half-year → INCLUDED; 56 alumni incasso payer → WARNING(PAYS_VIA_INCASSO).
      await selectRows(page, [51, 52, 53, 54, 55, 56])
      await openAction(page, "bulk-action-send-reminder")

      // Pin the cutoff so the fee-tier split is deterministic (it otherwise defaults to a
      // period-relative midpoint).
      await page.getByTestId("bulk-action-cutoff-date").locator("input").fill(CUTOFF_DATE)

      // Members who pay via incasso are now warned (off by default) on the reminder action.
      await expect(page.getByTestId("bulk-preview-disposition-51")).toContainText("Warning")
      await expect(page.getByTestId("bulk-preview-note-51")).toContainText("Pays via incasso")
      await expect(page.getByTestId("bulk-preview-disposition-52")).toContainText("Excluded")
      await expect(page.getByTestId("bulk-preview-note-52")).toContainText("Honorary")
      await expect(page.getByTestId("bulk-preview-disposition-53")).toContainText("Warning")
      await expect(page.getByTestId("bulk-preview-note-53")).toContainText("Already paid")
      await expect(page.getByTestId("bulk-preview-disposition-54")).toContainText("Skipped")
      await expect(page.getByTestId("bulk-preview-note-54")).toContainText("No email")
      await expect(page.getByTestId("bulk-preview-disposition-55")).toContainText("Included")
      await expect(page.getByTestId("bulk-preview-disposition-56")).toContainText("Warning")
      await expect(page.getByTestId("bulk-preview-note-56")).toContainText("Pays via incasso")

      // Fee tiers via the Amount column: 51 started 2024-01-01 (<= cutoff 2025-06-01) →
      // full-year € 20; 55 started 2025-07-01 (> cutoff) → half-year € 10; 56 alumni → € 5.
      // Amounts are computed for warned rows too (before the disposition is decided).
      await expect(page.getByTestId("bulk-preview-amount-51")).toContainText("€ 20")
      await expect(page.getByTestId("bulk-preview-amount-55")).toContainText("€ 10")
      await expect(page.getByTestId("bulk-preview-amount-56")).toContainText("€ 5")

      // Counts: 1 will apply (55), 3 with warnings (51, 53, 56), 1 excluded (52), 1 skipped (54).
      await expect(page.getByTestId("bulk-action-counts")).toContainText("1 will apply")
      await expect(page.getByTestId("bulk-action-counts")).toContainText("3 with warnings")
      await expect(page.getByTestId("bulk-action-counts")).toContainText("1 excluded")
      await expect(page.getByTestId("bulk-action-counts")).toContainText("1 skipped")

      // Re-include ("Forcibly include") checkboxes are offered for the WARNING rows only.
      await expect(page.getByTestId("bulk-preview-reinclude-51")).toBeVisible()
      await expect(page.getByTestId("bulk-preview-reinclude-53")).toBeVisible()
      await expect(page.getByTestId("bulk-preview-reinclude-56")).toBeVisible()

      // A future payment-due date is required to confirm (past dates gate the confirm).
      await page.getByTestId("bulk-action-payment-due-date").locator("input").fill(FUTURE_DATE)

      const req = captureExecute(page, "/contributionReminders/bulk/execute")
      await page.getByTestId("bulk-action-confirm-btn").click()
      const body = (await req).postDataJSON()
      // includedUserIds carries only the client-included set (warned rows not re-included).
      expect([...body.includedUserIds].sort((a: number, b: number) => a - b)).toEqual([55])
      expect(body.contributionPeriodId).toBe(251)
      expect(body.cutoffDate).toBe(CUTOFF_DATE)
      expect(body.paymentDueDate).toBe(FUTURE_DATE)

      await expect(page.getByTestId("bulk-action-dialog")).not.toBeVisible({timeout: 5_000})
    })

    test("re-including the already-paid warning row promotes it to INCLUDED and sends it", async ({page}) => {
      await setupPage(page)
      await selectRows(page, [53])
      await openAction(page, "bulk-action-send-reminder")

      await expect(page.getByTestId("bulk-preview-disposition-53")).toContainText("Warning")
      // Re-include: click the inner input of the WARNING row's checkbox.
      await page.getByTestId("bulk-preview-reinclude-53").locator("input").click()
      await expect(page.getByTestId("bulk-preview-disposition-53")).toContainText("Included")

      await page.getByTestId("bulk-action-payment-due-date").locator("input").fill(FUTURE_DATE)

      const req = captureExecute(page, "/contributionReminders/bulk/execute")
      await page.getByTestId("bulk-action-confirm-btn").click()
      const body = (await req).postDataJSON()
      expect([...body.includedUserIds]).toContain(53)

      await expect(page.getByTestId("bulk-action-dialog")).not.toBeVisible({timeout: 5_000})
    })
  })

  // ── Incasso (client-side: as reminder + incasso-mismatch warning) ─────────────

  test.describe("send incasso", () => {
    test("warns on incasso mismatch, keeps other reminder rules, and posts to execute", async ({page}) => {
      await setupPage(page)
      // 51 incasso=true INCLUDED, 57 incasso=false WARNING(INCASSO_MISMATCH),
      // 52 honorary EXCLUDED, 56 alumni incasso=true INCLUDED.
      await selectRows(page, [51, 57, 52, 56])
      await openAction(page, "bulk-action-send-incasso")

      await expect(page.getByTestId("bulk-preview-disposition-51")).toContainText("Included")
      await expect(page.getByTestId("bulk-preview-disposition-57")).toContainText("Warning")
      await expect(page.getByTestId("bulk-preview-note-57")).toContainText("Not marked for incasso")
      await expect(page.getByTestId("bulk-preview-disposition-52")).toContainText("Excluded")
      await expect(page.getByTestId("bulk-preview-disposition-56")).toContainText("Included")

      // Counts: 2 will apply (51, 56), 1 with warnings (57), 1 excluded (52).
      await expect(page.getByTestId("bulk-action-counts")).toContainText("2 will apply")
      await expect(page.getByTestId("bulk-action-counts")).toContainText("1 with warnings")
      await expect(page.getByTestId("bulk-action-counts")).toContainText("1 excluded")

      // Incasso requires a future expected-incasso date (cutoff defaults to a valid
      // in-period value). A past date would gate the confirm.
      await page.getByTestId("bulk-action-expected-incasso-date").locator("input").fill(FUTURE_DATE)

      const req = captureExecute(page, "/incassoNotifications/bulk/execute")
      await page.getByTestId("bulk-action-confirm-btn").click()
      const body = (await req).postDataJSON()
      expect([...body.includedUserIds].sort((a: number, b: number) => a - b)).toEqual([51, 56])
      expect(body.contributionPeriodId).toBe(251)
      expect(body.expectedIncassoDate).toBe(FUTURE_DATE)

      await expect(page.getByTestId("bulk-action-dialog")).not.toBeVisible({timeout: 5_000})
    })
  })

  // ── End membership (client-side: skip already-ended) ──────────────────────────

  test.describe("end membership", () => {
    test("includes active memberships, skips already-ended, and posts included set to execute", async ({page}) => {
      await setupPage(page)
      // 60 active → INCLUDED; 58 ended → SKIPPED(NO_ACTIVE_MEMBERSHIP).
      await selectRows(page, [60, 58])
      await openAction(page, "bulk-action-end-membership")

      await expect(page.getByTestId("bulk-preview-disposition-60")).toContainText("Included")
      await expect(page.getByTestId("bulk-preview-disposition-58")).toContainText("Skipped")
      await expect(page.getByTestId("bulk-preview-note-58")).toContainText("No active membership")

      await expect(page.getByTestId("bulk-action-counts")).toContainText("1 will apply")

      const req = captureExecute(page, "/memberships/bulk/end/execute")
      await page.getByTestId("bulk-action-confirm-btn").click()
      const body = (await req).postDataJSON()
      expect(body.userIds).toEqual([60])

      await expect(page.getByTestId("bulk-action-dialog")).not.toBeVisible({timeout: 5_000})
    })

    test("cancel closes the dialog without clearing the selection", async ({page}) => {
      await setupPage(page)
      await selectRows(page, [60])
      await page.getByTestId("bulk-actions-menu-btn").click()
      await page.getByTestId("bulk-action-end-membership").click()
      await expect(page.getByTestId("bulk-action-dialog")).toBeVisible({timeout: 10_000})

      await page.getByRole("button", {name: "Cancel"}).click()
      await expect(page.getByTestId("bulk-action-dialog")).not.toBeVisible({timeout: 5_000})

      await expect(page.getByTestId("member-manager-checkbox-60").locator("input")).toBeChecked()
    })
  })

  // ── Resume membership (client-side: WILL_RESUME / WILL_START_NEW / ALREADY_ACTIVE) ─

  test.describe("resume membership", () => {
    test("classifies resume outcomes client-side and posts included set to execute", async ({page}) => {
      await setupPage(page)
      // 58 ended within latest period → WILL_RESUME (INCLUDED);
      // 59 ended before latest period → WILL_START_NEW (INCLUDED);
      // 60 active → ALREADY_ACTIVE (SKIPPED).
      await selectRows(page, [58, 59, 60])
      await openAction(page, "bulk-action-resume-membership")

      await expect(page.getByTestId("bulk-preview-disposition-58")).toContainText("Included")
      await expect(page.getByTestId("bulk-preview-note-58")).toContainText("Will resume")
      await expect(page.getByTestId("bulk-preview-disposition-59")).toContainText("Included")
      await expect(page.getByTestId("bulk-preview-note-59")).toContainText("Will start new")
      await expect(page.getByTestId("bulk-preview-disposition-60")).toContainText("Skipped")
      await expect(page.getByTestId("bulk-preview-note-60")).toContainText("Already has an active membership")

      await expect(page.getByTestId("bulk-action-counts")).toContainText("2 will apply")

      const req = captureExecute(page, "/memberships/bulk/resume/execute")
      await page.getByTestId("bulk-action-confirm-btn").click()
      const body = (await req).postDataJSON()
      expect([...body.userIds].sort((a: number, b: number) => a - b)).toEqual([58, 59])

      await expect(page.getByTestId("bulk-action-dialog")).not.toBeVisible({timeout: 5_000})
    })
  })

  // ── A refused selection (the api's 409 contract) ──────────────────────────────
  //
  // The bulk endpoints never apply a selection partly: a 409 means nothing was
  // written and the offending rows are named in each field error's `values`. These
  // tests drive the real dialog against that response — the happy paths above only
  // ever see a 200, so this is the only coverage of the refusal branch.

  test.describe("a refused selection", () => {
    const MARK_PAID = /\/contributions\/bulk\/mark-paid$/

    test("names the refused rows and states that nothing was changed", async ({page}) => {
      await setupPage(page)
      await refuseExecute(page, MARK_PAID, [
        {code: "DeletedUserIds", message: "1 of the selected users have been deleted.", values: [51]},
      ])
      await selectRows(page, [51])
      await openAction(page, "bulk-action-mark-paid")

      await page.getByTestId("bulk-action-confirm-btn").click()

      const alert = page.getByTestId("bulk-paid-rejection")
      await expect(alert).toBeVisible({timeout: 10_000})
      await expect(alert).toContainText("Nothing was changed")
      await expect(alert).toContainText("have been deleted")
      // Named by row, not by raw id, so the operator can find the row in the table.
      await expect(alert).toContainText("Alice Regular")
    })

    test("keeps the dialog open and does not report success", async ({page}) => {
      await setupPage(page)
      await refuseExecute(page, MARK_PAID, [
        {code: "DeletedUserIds", message: "Deleted.", values: [51]},
      ])
      await selectRows(page, [51])
      await openAction(page, "bulk-action-mark-paid")

      await page.getByTestId("bulk-action-confirm-btn").click()
      await expect(page.getByTestId("bulk-paid-rejection")).toBeVisible({timeout: 10_000})

      // The happy path closes the dialog after ~1.2s; a refusal must not.
      await page.waitForTimeout(2_000)
      await expect(page.getByTestId("bulk-action-dialog")).toBeVisible()
      await expect(page.getByTestId("bulk-action-preview-table")).toBeVisible()
    })

    test("reloads the table but leaves the selection intact when the data is stale", async ({page}) => {
      await setupPage(page)
      const reloads = countUserReloads(page)
      await refuseExecute(page, MARK_PAID, [
        {code: "UnknownUserIds", message: "1 of the selected ids is not a user.", values: [51]},
      ])
      await selectRows(page, [51])
      await openAction(page, "bulk-action-mark-paid")

      const before = reloads()
      await page.getByTestId("bulk-action-confirm-btn").click()
      await expect(page.getByTestId("bulk-paid-rejection")).toBeVisible({timeout: 10_000})
      await expect.poll(() => reloads(), {timeout: 10_000}).toBeGreaterThan(before)

      // Reloading must not cost the operator the selection they just built.
      await expect(page.getByTestId("member-manager-checkbox-51").locator("input")).toBeChecked()
    })

    test("does not reload when only the choice was wrong", async ({page}) => {
      await setupPage(page)
      const reloads = countUserReloads(page)
      await refuseExecute(page, MARK_PAID, [
        {code: "HonoraryUserIds", message: "Honorary members owe no contribution.", values: [51]},
      ])
      await selectRows(page, [51])
      await openAction(page, "bulk-action-mark-paid")

      const before = reloads()
      await page.getByTestId("bulk-action-confirm-btn").click()
      await expect(page.getByTestId("bulk-paid-rejection")).toBeVisible({timeout: 10_000})

      // Honorary status is not a staleness signal, so the table is left alone.
      await page.waitForTimeout(1_000)
      expect(reloads()).toBe(before)
    })

    test("lists every reason when the api returns more than one", async ({page}) => {
      await setupPage(page)
      await refuseExecute(page, MARK_PAID, [
        {code: "DeletedUserIds", message: "Deleted users in the selection.", values: [51]},
        {code: "HonoraryUserIds", message: "Honorary members in the selection.", values: [53]},
      ])
      await selectRows(page, [51, 53])
      await openAction(page, "bulk-action-mark-paid")

      await page.getByTestId("bulk-action-confirm-btn").click()

      const alert = page.getByTestId("bulk-paid-rejection")
      await expect(alert).toContainText("Deleted users in the selection.")
      await expect(alert).toContainText("Honorary members in the selection.")
      await expect(alert).toContainText("Alice Regular")
      await expect(alert).toContainText("Carol Paid")
    })
  })

})
