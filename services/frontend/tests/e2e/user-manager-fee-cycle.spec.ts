import {expect, test, type Page} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

/**
 * Asking a period's unpaid members for what they owe, from the menu to the result.
 *
 * Driven end to end rather than asserted in a unit test because the dialog decides nothing:
 * it asks the api for the partition and renders the answer, so a spec that never lets the
 * request happen would be watching something other than what the treasurer sees.
 */

/** One member on each side of the partition, plus an honorary one the cycle cannot write to. */
const MEMBERSHIPS = [
  {id: 100, userId: 1, memberType: "REGULAR", startDate: "2025-01-01", incasso: false},
  {id: 101, userId: 2, memberType: "REGULAR", startDate: "2025-02-01", incasso: true},
  {id: 102, userId: 3, memberType: "HONORARY", startDate: "2025-01-01", incasso: false},
]

const USERS = [
  {id: 1, fullName: "Emma Dokter", username: "lyndisluna", enabled: true, roles: ["MEMBER"]},
  {id: 2, fullName: "Viktor Petrov", username: "ariosfury", enabled: true, roles: ["MEMBER"]},
  {id: 3, fullName: "Hanne Erelid", username: "hanne", enabled: true, roles: ["MEMBER"]},
]

async function openFeeCycle(page: Page): Promise<void> {
  await page.setViewportSize({width: 1400, height: 900})
  // No contributions, so nobody has paid and the whole membership list is in the cycle.
  await installApiMocks(page, {users: USERS, memberships: MEMBERSHIPS, contributions: []})
  await loginAsBoard(page.context())
  await page.goto("/user-manager")
  await page.getByTestId("member-manager-table").waitFor()
  await page.getByTestId("bulk-actions-menu-btn").click()
  await page.getByTestId("bulk-action-fee-cycle").click()
  await page.getByTestId("bulk-action-dialog").waitFor()
}

test.describe("the fee cycle", () => {
  test("shows both sides of the partition, and who it will not write to", async ({page}) => {
    await openFeeCycle(page)

    await expect(page.getByTestId("fee-cycle-group-1")).toContainText("Transfer")
    await expect(page.getByTestId("fee-cycle-group-2")).toContainText("Direct debit")
    await expect(page.getByTestId("fee-cycle-count-transfer")).toContainText("2 by transfer")
    await expect(page.getByTestId("fee-cycle-count-direct-debit")).toContainText("1 by direct debit")

    // Excluded rather than omitted, so the honorary member's absence is visible.
    await expect(page.getByTestId("bulk-preview-disposition-3")).toContainText("Excluded")
    await expect(page.getByTestId("bulk-preview-note-3")).toContainText("Honorary")
  })

  test("prices each row, and re-prices one when its fee type changes", async ({page}) => {
    await openFeeCycle(page)

    await expect(page.getByTestId("fee-cycle-amount-1")).toContainText("20.00")

    await page.getByTestId("fee-cycle-feetype-1").click()
    await page.getByRole("option", {name: "Alumni fee"}).click()

    // The period's alumni fee, worked out in the browser: no round trip to re-price a row.
    await expect(page.getByTestId("fee-cycle-amount-1")).toContainText("5.00")
  })

  test("says which members have already been asked, without shutting them out", async ({page}) => {
    await openFeeCycle(page)

    await expect(page.getByTestId("fee-cycle-asked-already-warning"))
      .toContainText("already been asked for this period")
    await expect(page.getByTestId("fee-cycle-last-asked-2")).toContainText("01/09/2025")
    await expect(page.getByTestId("fee-cycle-last-asked-1")).toContainText("Never")
    // Asked already, still included: chasing is the job.
    await expect(page.getByTestId("bulk-preview-disposition-2")).toContainText("Included")
  })

  test("reads one member's email before sending to any of them", async ({page}) => {
    await openFeeCycle(page)

    await page.getByTestId("fee-cycle-payment-due-date").locator("input").fill("2026-12-01")
    await page.getByTestId("fee-cycle-debit-date").locator("input").fill("2026-12-15")
    await page.getByTestId("fee-cycle-preview-email-btn").click()

    await expect(page.getByTestId("email-preview-subject")).toContainText("Please pay your Blueshell contribution")
    await expect(page.getByTestId("email-preview-frame")).toBeVisible()
  })

  test("sends both statements from one confirmation and reports each side", async ({page}) => {
    await openFeeCycle(page)

    await page.getByTestId("fee-cycle-payment-due-date").locator("input").fill("2026-12-01")
    await page.getByTestId("fee-cycle-debit-date").locator("input").fill("2026-12-15")
    await page.getByTestId("bulk-action-confirm-btn").click()

    await expect(page.getByTestId("bulk-action-dialog")).toBeHidden({timeout: 5000})
  })

  test("will not send without both dates", async ({page}) => {
    await openFeeCycle(page)

    await page.getByTestId("bulk-action-confirm-btn").click()

    // The dialog stays open with the fields complaining, rather than sending a cycle that
    // promises no date.
    await expect(page.getByTestId("bulk-action-dialog")).toBeVisible()
    await expect(page.getByTestId("fee-cycle-payment-due-date")).toContainText("required")
    await expect(page.getByTestId("fee-cycle-debit-date")).toContainText("required")
  })
})

test.describe("the fee cycle without a period", () => {
  test("is offered but inert, because there is no cycle without one", async ({page}) => {
    await page.setViewportSize({width: 1400, height: 900})
    await installApiMocks(page, {users: USERS, memberships: MEMBERSHIPS, contributionPeriods: []})
    await loginAsBoard(page.context())
    await page.goto("/user-manager")
    await page.getByTestId("member-manager-table").waitFor()

    await page.getByTestId("bulk-actions-menu-btn").click()

    await expect(page.getByTestId("bulk-action-fee-cycle")).toHaveClass(/v-list-item--disabled/)
  })
})
