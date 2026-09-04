import {devices} from "@playwright/test"
import {expect, test, type Page} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

/**
 * Sending a period's payment emails, from the menu to the result. Driven end to end because
 * the wizard decides nothing: it hands the api the selection and renders the answer, and a
 * refusal puts the treasurer back on the step that owns it.
 */

/** Dates move with the clock, because every date the wizard accepts sits against the period. */
const day = (offset: number) => new Date(Date.now() + offset * 86_400_000).toISOString().slice(0, 10)

const PERIOD = {
  id: 201,
  startDate: day(-60),
  endDate: day(240),
  halfYearCutoffDate: day(90),
  halfYearFee: 10,
  fullYearFee: 20,
  alumniFee: 5,
}

const DUE_DATE = day(30)
const DEBIT_DATE = day(45)

/** One who transfers, one on direct debit, an honorary one, and one who has already paid. */
const MEMBERSHIPS = [
  {id: 100, userId: 1, memberType: "REGULAR", startDate: "2025-01-01", incasso: false},
  {id: 101, userId: 2, memberType: "REGULAR", startDate: "2025-02-01", incasso: true},
  {id: 102, userId: 3, memberType: "HONORARY", startDate: "2025-01-01", incasso: false},
  {id: 103, userId: 4, memberType: "REGULAR", startDate: "2025-03-01", incasso: false},
]

const USERS = [
  {id: 1, fullName: "Emma Dokter", username: "lyndisluna", enabled: true, roles: ["MEMBER"]},
  {id: 2, fullName: "Viktor Petrov", username: "ariosfury", enabled: true, roles: ["MEMBER"]},
  {id: 3, fullName: "Hanne Erelid", username: "hanne", enabled: true, roles: ["MEMBER"]},
  {id: 4, fullName: "Sanne Bakker", username: "sanne", enabled: true, roles: ["MEMBER"]},
]

const CONTRIBUTIONS = [{id: 300, userId: 4, contributionPeriodId: PERIOD.id}]

type Refusal = {status: number; errors: Array<Record<string, unknown>>}

async function openPaymentEmails(
  page: Page,
  options: {select?: number[]; refusal?: Refusal} = {},
): Promise<void> {
  const select = options.select ?? [1, 2, 3, 4]
  await installApiMocks(page, {
    users: USERS,
    memberships: MEMBERSHIPS,
    contributionPeriods: [PERIOD],
    contributions: CONTRIBUTIONS,
    paymentEmailRefusal: options.refusal,
  })
  await loginAsBoard(page.context())
  await page.goto("/user-manager")

  // Below lg the manager is a list rather than a table, and it selects with its own
  // controls. No viewport is pinned here, so each project drives the layout it is for.
  const table = page.getByTestId("member-manager-table")
  const list = page.getByTestId("member-manager-mobile-list")
  await table.or(list).first().waitFor()
  const narrow = (await list.count()) > 0
  const prefix = narrow ? "member-manager-mobile-checkbox" : "member-manager-checkbox"
  for (const id of select) {
    await page.getByTestId(`${prefix}-${id}`).locator("input").click()
  }
  await page.getByTestId("bulk-actions-menu-btn").click()
  await page.getByTestId("bulk-action-send-payment-emails").click()
  await rowsOf(page, "members").first().waitFor()
}

const next = (page: Page) => page.getByTestId("payment-emails-next-btn").click()

/** A step's rows, whichever layout the width is showing them in. */
const rowsOf = (page: Page, step: "members" | "fees" | "review") =>
  page.getByTestId(`payment-emails-${step}-table`).or(page.getByTestId(`payment-emails-${step}-list`))

/** Steps 1 and 2 ask nothing that has to be answered, so the dates are the only stop. */
async function goToTheLastStep(page: Page): Promise<void> {
  await next(page)
  await rowsOf(page, "fees").first().waitFor()
  await next(page)
  await page.getByTestId("payment-emails-payment-due-date").waitFor()
}

async function fillDates(page: Page): Promise<void> {
  await page.getByTestId("payment-emails-payment-due-date").locator("input").fill(DUE_DATE)
  await page.getByTestId("payment-emails-debit-date").locator("input").fill(DEBIT_DATE)
}

test.describe("step 1, who the batch writes to", () => {
  test("ticks who the api would write to, and says why the others are not", async ({page}) => {
    await openPaymentEmails(page)

    await expect(page.getByTestId("payment-emails-send-to-1").locator("input")).toBeChecked()
    await expect(page.getByTestId("payment-emails-send-to-2").locator("input")).toBeChecked()
    await expect(page.getByTestId("payment-emails-send-to-4").locator("input")).not.toBeChecked()
    await expect(page.getByTestId("payment-emails-send-to-3")).toBeHidden()
    await expect(page.getByTestId("payment-emails-reason-3")).toContainText("Owes no contribution")
    await expect(page.getByTestId("payment-emails-reason-4"))
      .toContainText("Already paid this contribution")
  })

  test("counts what is ticked, and moves when a warned member is ticked back in", async ({page}) => {
    await openPaymentEmails(page)

    await expect(page.getByTestId("payment-emails-count-recipients")).toContainText("2 of 4")
    await expect(page.getByTestId("payment-emails-count-reminders"))
      .toContainText("1 contribution reminder")
    await expect(page.getByTestId("payment-emails-count-notifications"))
      .toContainText("1 incasso notification")
    await expect(page.getByTestId("payment-emails-count-excluded")).toContainText("1 cannot be emailed")

    await page.getByTestId("payment-emails-send-to-4").locator("input").click()

    await expect(page.getByTestId("payment-emails-count-recipients")).toContainText("3 of 4")
    await expect(page.getByTestId("payment-emails-count-reminders"))
      .toContainText("2 contribution reminders")
  })

  test("unticking a member drops them from the rest of the wizard", async ({page}) => {
    await openPaymentEmails(page)

    await page.getByTestId("payment-emails-send-to-2").locator("input").click()
    await next(page)

    await expect(page.getByTestId("payment-emails-fee-row-1")).toBeVisible()
    await expect(page.getByTestId("payment-emails-fee-row-2")).toBeHidden()
  })

  // Sorting is a table-header gesture, so this test is about the desktop layout and pins it.
  test("says when each member was last written to, and sorts on it", async ({page}) => {
    await page.setViewportSize({width: 1400, height: 900})
    await openPaymentEmails(page)

    await expect(page.getByTestId("payment-emails-last-ask-2")).toHaveText("01/09/2025")
    await expect(page.getByTestId("payment-emails-last-ask-1")).toHaveText("—")

    const rows = page.locator('[data-testid^="payment-emails-row-"]')
    // Ascending first: the members nobody has written to are the ones worth including.
    await page.getByRole("button", {name: "Last payment email"}).click()
    await expect(rows.last()).toHaveAttribute("data-testid", "payment-emails-row-2")

    await page.getByRole("button", {name: "Last payment email"}).click()
    await expect(rows.first()).toHaveAttribute("data-testid", "payment-emails-row-2")
  })
})

test.describe("on a phone", () => {
  // The device's screen, without its defaultBrowserType: that one option inside a describe
  // forces a new worker, which Playwright refuses outright.
  test.use({
    viewport: devices["Pixel 7"].viewport,
    deviceScaleFactor: devices["Pixel 7"].deviceScaleFactor,
    isMobile: devices["Pixel 7"].isMobile,
    hasTouch: devices["Pixel 7"].hasTouch,
  })

  test("selects members from the list, and reads why each one is in or out", async ({page}) => {
    await openPaymentEmails(page)
    // The facts the desktop columns carry, on a screen too narrow to carry columns.
    await expect(page.getByTestId("payment-emails-send-to-1").locator("input")).toBeChecked()
    await expect(page.getByTestId("payment-emails-last-ask-2")).toContainText("01/09/2025")
    await expect(page.getByTestId("payment-emails-reason-3")).toContainText("Owes no contribution")
    await expect(page.getByTestId("payment-emails-reason-4"))
      .toContainText("Already paid this contribution")
  })

  /** The rightmost edge anything in the dialog reaches, which a clipped column exceeds. */
  async function widestEdge(page: Page): Promise<{worst: number; viewport: number; doc: number}> {
    return page.evaluate(() => {
      const root = document.querySelector(".v-overlay__content")!
      const worst = [...root.querySelectorAll("*")].reduce(
        (acc, el) => Math.max(acc, el.getBoundingClientRect().right), 0)
      return {worst: Math.round(worst), viewport: window.innerWidth,
              doc: document.documentElement.scrollWidth}
    })
  }

  test("no step is cut off with no way to reach it", async ({page}) => {
    await openPaymentEmails(page)

    // toBeVisible passes for an element painted outside the viewport, so each step is measured.
    for (const step of ["members", "fees", "review"] as const) {
      // The step's own rows first: measuring straight after Next reads the outgoing step.
      await rowsOf(page, step).first().waitFor()
      const edge = await widestEdge(page)
      expect(edge.worst, `${step} step overflows`).toBeLessThanOrEqual(edge.viewport)
      expect(edge.doc, `${step} step scrolls the page sideways`)
        .toBeLessThanOrEqual(edge.viewport)
      if (step !== "review") await next(page)
    }
  })

  test("carries the batch through every step", async ({page}) => {
    await openPaymentEmails(page, {select: [1, 2]})
    await next(page)

    await expect(page.getByTestId("payment-emails-kind-1")).toBeVisible()
    await expect(page.getByTestId("payment-emails-amount-1")).toContainText("20.00")

    await next(page)
    await fillDates(page)

    await expect(page.getByTestId("payment-emails-recipient-1")).toContainText("Emma Dokter")
  })
})

test.describe("step 2, the fees and the emails", () => {
  test("moving a member onto the other email warns by name and flags the row", async ({page}) => {
    await openPaymentEmails(page)
    await next(page)

    await page.getByTestId("payment-emails-kind-2").click()
    await page.getByRole("option", {name: "Contribution reminder"}).click()

    await expect(page.getByTestId("payment-emails-kind-warning")).toContainText("Viktor Petrov")
    await expect(page.getByTestId("payment-emails-switched-2")).toContainText("Pays by direct debit")
    await expect(page.getByTestId("payment-emails-count-reminders"))
      .toContainText("2 contribution reminders")
  })

  test("prices each row, and re-prices one when its fee type changes", async ({page}) => {
    await openPaymentEmails(page)
    await next(page)

    await expect(page.getByTestId("payment-emails-amount-1")).toContainText("20.00")

    await page.getByTestId("payment-emails-feetype-1").click()
    await page.getByRole("option", {name: "Alumni fee"}).click()

    // The period's alumni fee, worked out in the browser: no round trip to re-price a row.
    await expect(page.getByTestId("payment-emails-amount-1")).toContainText("5.00")
    await expect(page.getByTestId("payment-emails-fee-warning")).toContainText("Emma Dokter")
  })

  test("last payment email holds still when the row switches email", async ({page}) => {
    await openPaymentEmails(page)
    await next(page)

    // Viktor was reminded on 01/09 and is getting a notification: still a member we wrote to.
    await expect(page.getByTestId("payment-emails-last-ask-2")).toContainText("01/09/2025")

    await page.getByTestId("payment-emails-kind-2").click()
    await page.getByRole("option", {name: "Contribution reminder"}).click()

    await expect(page.getByTestId("payment-emails-last-ask-2")).toContainText("01/09/2025")
  })
})

test.describe("step 3, what will be sent", () => {
  test("lists each recipient, and previews the email one of them gets", async ({page}) => {
    await openPaymentEmails(page)
    await goToTheLastStep(page)

    await expect(page.getByTestId("payment-emails-recipient-1")).toContainText("Emma Dokter")
    await expect(page.getByTestId("payment-emails-recipient-3")).toBeHidden()

    await fillDates(page)
    await page.getByTestId("payment-emails-preview-1").click()

    await expect(page.getByTestId("email-preview-subject"))
      .toContainText("Please pay your Blueshell contribution")
    await expect(page.getByTestId("email-preview-frame")).toBeVisible()
  })

  test("will not send without the dates the batch needs", async ({page}) => {
    await openPaymentEmails(page)
    await goToTheLastStep(page)

    await expect(page.getByTestId("payment-emails-payment-due-date")).toContainText("required")
    await expect(page.getByTestId("payment-emails-debit-date")).toContainText("required")
    await expect(page.getByTestId("payment-emails-next-btn")).toBeDisabled()
  })

  test("a date nobody in the batch needs is optional and says so", async ({page}) => {
    await openPaymentEmails(page, {select: [1]})
    await goToTheLastStep(page)

    await expect(page.getByTestId("payment-emails-debit-date"))
      .toContainText("Nobody here is on direct debit")

    await page.getByTestId("payment-emails-payment-due-date").locator("input").fill(DUE_DATE)
    await next(page)
    await page.getByTestId("payment-emails-confirm-send-btn").click()

    await expect(page.getByTestId("payment-emails-wizard")).toBeHidden({timeout: 5000})
  })
})

test.describe("the confirmation", () => {
  test("Send opens a summary, and nothing goes out until it is confirmed", async ({page}) => {
    await openPaymentEmails(page)
    await goToTheLastStep(page)
    await fillDates(page)

    await next(page)

    await expect(page.getByTestId("payment-emails-confirm-summary")).toBeVisible()
    await expect(page.getByTestId("payment-emails-confirm-reminders")).toContainText("1")
    await expect(page.getByTestId("payment-emails-confirm-notifications")).toContainText("1")
    await expect(page.getByTestId("payment-emails-confirm-not-emailed"))
      .toContainText("2 selected members get no email")

    await page.getByTestId("payment-emails-confirm-send-btn").click()

    await expect(page.getByTestId("payment-emails-wizard")).toBeHidden({timeout: 5000})
  })

  test("backing out returns to the last step with everything intact", async ({page}) => {
    await openPaymentEmails(page)
    await goToTheLastStep(page)
    await fillDates(page)
    await next(page)

    await page.getByTestId("payment-emails-confirm-back-btn").click()

    await expect(page.getByTestId("payment-emails-confirm-summary")).toBeHidden()
    await expect(page.getByTestId("payment-emails-payment-due-date").locator("input"))
      .toHaveValue(DUE_DATE)
  })
})

test.describe("a refusal from the api", () => {
  test("turns the date input red, on the step that owns it", async ({page}) => {
    await openPaymentEmails(page, {
      refusal: {
        status: 400,
        errors: [{
          objectName: "SendPaymentEmailsRequest",
          field: "paymentDueDate",
          code: "DateOutsideContributionPeriod",
          message: "A date must fall within the contribution period, or shortly after it ends.",
        }],
      },
    })
    await goToTheLastStep(page)
    await fillDates(page)
    await next(page)

    await page.getByTestId("payment-emails-confirm-send-btn").click()

    await expect(page.getByTestId("payment-emails-confirm-summary")).toBeHidden()
    await expect(page.getByTestId("payment-emails-rejection")).toContainText("Nothing was sent")
    const dueDate = page.getByTestId("payment-emails-payment-due-date")
    await expect(dueDate).toContainText("must fall within the contribution period")
    await expect(dueDate).toHaveClass(/v-input--error/)
  })

  test("puts the treasurer back on the member step with the rows it named", async ({page}) => {
    await openPaymentEmails(page, {
      refusal: {
        status: 409,
        errors: [{
          objectName: "SendPaymentEmailsRequest",
          field: "userIds",
          code: "UnknownUserIds",
          message: "1 of the selected users no longer exist.",
          values: [2],
        }],
      },
    })
    await goToTheLastStep(page)
    await fillDates(page)
    await next(page)

    await page.getByTestId("payment-emails-confirm-send-btn").click()

    await expect(rowsOf(page, "members").first()).toBeVisible()
    await expect(page.getByTestId("payment-emails-refusal-2")).toContainText("no longer exist")
    await expect(page.getByTestId("payment-emails-refusal-1")).toBeHidden()
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
