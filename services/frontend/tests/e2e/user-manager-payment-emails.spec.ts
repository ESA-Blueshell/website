import {expect, test, type Page} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

/**
 * Sending a period's payment emails to a selection, from the menu to the result. Driven end
 * to end because the dialog decides nothing: it hands the api the selection and renders the
 * answer.
 */

/** One member who transfers, one on direct debit, and an honorary one neither email reaches. */
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

async function openPaymentEmails(page: Page, select: number[] = [1, 2, 3]): Promise<void> {
  await page.setViewportSize({width: 1400, height: 900})
  await installApiMocks(page, {users: USERS, memberships: MEMBERSHIPS, contributions: []})
  await loginAsBoard(page.context())
  await page.goto("/user-manager")
  await page.getByTestId("member-manager-table").waitFor()
  for (const id of select) {
    await page.getByTestId(`member-manager-checkbox-${id}`).locator("input").click()
  }
  await page.getByTestId("bulk-actions-menu-btn").click()
  await page.getByTestId("bulk-action-send-payment-emails").click()
  await page.getByTestId("bulk-action-dialog").waitFor()
}

test.describe("sending payment emails", () => {
  test("shows which email each member gets, and who gets none", async ({page}) => {
    await openPaymentEmails(page)

    await expect(page.getByTestId("payment-emails-kind-1")).toContainText("Contribution reminder")
    await expect(page.getByTestId("payment-emails-kind-2")).toContainText("Incasso notification")
    await expect(page.getByTestId("bulk-preview-disposition-3")).toContainText("Excluded")
    await expect(page.getByTestId("bulk-preview-note-3")).toContainText("Honorary")
  })

  test("counts each kind above the table", async ({page}) => {
    await openPaymentEmails(page)

    await expect(page.getByTestId("payment-emails-count-reminders")).toContainText("1 contribution reminder")
    await expect(page.getByTestId("payment-emails-count-notifications"))
      .toContainText("1 incasso notification")
    await expect(page.getByTestId("payment-emails-count-excluded")).toContainText("1 not written to")
  })

  test("switching a member's email moves the counts and flags the row", async ({page}) => {
    await openPaymentEmails(page)

    await page.getByTestId("payment-emails-kind-2").click()
    await page.getByRole("option", {name: "Contribution reminder"}).click()

    await expect(page.getByTestId("payment-emails-count-reminders")).toContainText("2 contribution reminders")
    await expect(page.getByTestId("payment-emails-count-notifications"))
      .toContainText("0 incasso notifications")
    await expect(page.getByTestId("payment-emails-switched-2")).toContainText("Pays by direct debit")
  })

  test("prices each row, and re-prices one when its fee type changes", async ({page}) => {
    await openPaymentEmails(page)

    await expect(page.getByTestId("payment-emails-amount-1")).toContainText("20.00")

    await page.getByTestId("payment-emails-feetype-1").click()
    await page.getByRole("option", {name: "Alumni fee"}).click()

    // The period's alumni fee, worked out in the browser: no round trip to re-price a row.
    await expect(page.getByTestId("payment-emails-amount-1")).toContainText("5.00")
  })

  test("last sent follows the email the row is set to", async ({page}) => {
    await openPaymentEmails(page)

    // Viktor was reminded on 01/09 but is getting a notification, which he has never had.
    await expect(page.getByTestId("payment-emails-last-sent-2")).toContainText("Never")

    await page.getByTestId("payment-emails-kind-2").click()
    await page.getByRole("option", {name: "Contribution reminder"}).click()

    await expect(page.getByTestId("payment-emails-last-sent-2")).toContainText("01/09/2025")
  })

  test("previews one member's email before sending to any of them", async ({page}) => {
    await openPaymentEmails(page)

    await page.getByTestId("payment-emails-payment-due-date").locator("input").fill("2026-12-01")
    await page.getByTestId("payment-emails-preview-btn").click()

    await expect(page.getByTestId("email-preview-subject"))
      .toContainText("Please pay your Blueshell contribution")
    await expect(page.getByTestId("email-preview-frame")).toBeVisible()
  })

  test("sends, and closes on success", async ({page}) => {
    await openPaymentEmails(page)

    await page.getByTestId("payment-emails-payment-due-date").locator("input").fill("2026-12-01")
    await page.getByTestId("payment-emails-debit-date").locator("input").fill("2026-12-15")
    await page.getByTestId("bulk-action-confirm-btn").click()

    await expect(page.getByTestId("bulk-action-dialog")).toBeHidden({timeout: 5000})
  })

  test("will not send without the dates the batch needs", async ({page}) => {
    await openPaymentEmails(page)

    await page.getByTestId("bulk-action-confirm-btn").click()

    await expect(page.getByTestId("bulk-action-dialog")).toBeVisible()
    await expect(page.getByTestId("payment-emails-payment-due-date")).toContainText("required")
    await expect(page.getByTestId("payment-emails-debit-date")).toContainText("required")
  })

  test("a date nobody in the batch needs is optional and says so", async ({page}) => {
    await openPaymentEmails(page, [1])

    await expect(page.getByTestId("payment-emails-debit-date"))
      .toContainText("Nobody in this selection is on direct debit")

    await page.getByTestId("payment-emails-payment-due-date").locator("input").fill("2026-12-01")
    await page.getByTestId("bulk-action-confirm-btn").click()

    await expect(page.getByTestId("bulk-action-dialog")).toBeHidden({timeout: 5000})
  })
})

test.describe("the payment emails action without a selection", () => {
  test("is offered but inert, because there is nobody to write to", async ({page}) => {
    await page.setViewportSize({width: 1400, height: 900})
    await installApiMocks(page, {users: USERS, memberships: MEMBERSHIPS})
    await loginAsBoard(page.context())
    await page.goto("/user-manager")
    await page.getByTestId("member-manager-table").waitFor()

    await page.getByTestId("bulk-actions-menu-btn").click()

    await expect(page.getByTestId("bulk-action-send-payment-emails"))
      .toHaveClass(/v-list-item--disabled/)
  })
})
