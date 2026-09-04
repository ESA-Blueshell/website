import {expect, test, type Page} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

async function openPeriods(page: Page): Promise<void> {
  await page.setViewportSize({width: 1400, height: 900})
  await installApiMocks(page)
  await loginAsBoard(page.context())
  await page.goto("/user-manager")
  await page.getByTestId("contribution-period-list").waitFor()
}

async function openEditDialog(page: Page, periodId: number): Promise<void> {
  const period = page.getByTestId(`contribution-period-select-btn-${periodId}`)
  await period.scrollIntoViewIfNeeded()
  await period.hover()
  await page.getByTestId(`contribution-period-edit-btn-${periodId}`).click()
  await expect(page.getByTestId("contribution-period-dialog")).toContainText("Edit Contribution Period")
}

const field = (page: Page, name: string) => page.getByTestId(`contribution-period-${name}-field`).locator("input")

async function fillPeriod(page: Page): Promise<void> {
  await field(page, "start-date").fill("2027-01-01")
  await field(page, "end-date").fill("2027-12-31")
  await field(page, "half-year-cutoff").fill("2027-07-01")
  await field(page, "half-year-fee").fill("15")
  await field(page, "full-year-fee").fill("30")
  await field(page, "alumni-fee").fill("5")
}

/**
 * The request rather than the response: a success reloads the list, and awaiting the response
 * races that reload.
 */
const writeToPeriods = (page: Page) => page.waitForRequest((request) =>
  request.method() !== "GET" && /\/contributionPeriods(\/\d+)?$/.test(new URL(request.url()).pathname),
)

/**
 * The dialog is shared between adding and editing a period, so what it carries from one to the
 * next decides which endpoint the submit reaches. A create that keeps the edited period's id
 * updates that period instead, and the version it carries is stale by then, so the API answers
 * with a conflict.
 */
test.describe("contribution period dialog", () => {
  test("the add dialog holds nothing of the period that was edited", async ({page}) => {
    await openPeriods(page)
    await openEditDialog(page, 201)
    await page.getByTestId("contribution-period-cancel-btn").click()

    await page.getByTestId("contribution-period-add-btn").click()

    const dialog = page.getByTestId("contribution-period-dialog")
    await expect(dialog).toContainText("Add Contribution Period")
    await expect(field(page, "start-date")).toHaveValue("")
    await expect(field(page, "end-date")).toHaveValue("")
    await expect(field(page, "half-year-cutoff")).toHaveValue("")
    // The delete button belongs to an existing period, so its absence says the dialog knows
    // it is adding one.
    await expect(page.getByTestId("contribution-period-delete-btn")).toHaveCount(0)
  })

  test("a period added after an edit is created, not written over the edited one", async ({page}) => {
    await openPeriods(page)
    await openEditDialog(page, 201)
    await page.getByTestId("contribution-period-cancel-btn").click()
    await page.getByTestId("contribution-period-add-btn").click()
    await fillPeriod(page)

    const submitted = writeToPeriods(page)
    await page.getByTestId("contribution-period-submit-btn").click()
    const request = await submitted

    expect(request.method()).toBe("POST")
    expect(new URL(request.url()).pathname).toMatch(/\/contributionPeriods$/)
    expect(request.postDataJSON()).toMatchObject({startDate: "2027-01-01", endDate: "2027-12-31"})
    // The edited period's contact list is no more the new period's than its id is.
    expect(request.postDataJSON().contactListId ?? null).toBeNull()
    // The created period reaches the list, which is what the conflict used to prevent.
    await expect(page.getByTestId("contribution-period-select-btn-202")).toBeVisible()
  })

  test("editing a period sends its own version, which the form cannot dirty", async ({page}) => {
    await openPeriods(page)
    await openEditDialog(page, 201)
    await field(page, "full-year-fee").fill("40")

    const submitted = writeToPeriods(page)
    await page.getByTestId("contribution-period-submit-btn").click()
    const request = await submitted

    expect(request.method()).toBe("PUT")
    expect(new URL(request.url()).pathname).toMatch(/\/contributionPeriods\/201$/)
    // Version 4 is what the list read for period 201. Taking it from the form would let an
    // edit ship whatever the previous period held, which the API refuses with a conflict.
    expect(request.postDataJSON()).toMatchObject({version: 4, fullYearFee: 40, contactListId: 8})
    // The dialog only closes on a save the API accepted.
    await expect(page.getByTestId("contribution-period-dialog")).toBeHidden()
  })

  test("a cancelled edit is not carried into the next one", async ({page}) => {
    await openPeriods(page)
    await openEditDialog(page, 201)
    await field(page, "full-year-fee").fill("99")
    await page.getByTestId("contribution-period-cancel-btn").click()

    await openEditDialog(page, 201)

    await expect(field(page, "full-year-fee")).toHaveValue("20")
  })
})
