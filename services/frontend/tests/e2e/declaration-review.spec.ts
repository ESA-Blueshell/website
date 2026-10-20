import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

const PHONE = {width: 390, height: 844}

test.describe("the board's review of declarations", () => {
  test("a board member finds a submitted declaration, and sends it back with a reason", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/management/declarations")
    await expect(page.getByTestId("declarations-manage-30")).toContainText("Pizza for the committee meeting")
    await expect(page.getByTestId("declarations-manage-31")).toHaveCount(0)
    await page.getByTestId("declarations-search").fill("pizza")
    await page.getByRole("link", {name: "Pizza for the committee meeting"}).click()
    await expect(page).toHaveURL(/\/management\/declarations\/30$/)

    await expect(page.getByTestId("declaration-review-history")).toContainText("Submitted by Ann Vos")
    await page.getByTestId("declaration-send-back").click()
    await page.getByTestId("declaration-reason").locator("textarea").fill("The receipt is cut off.")
    await page.getByTestId("declaration-reason-confirm").click()
    await expect(page.getByTestId("declaration-review-state")).toContainText("Sent back")
    await expect(page.getByTestId("declaration-review-history")).toContainText("The receipt is cut off.")
  })

  test("a board member approves from the list, and the list fits a phone", async ({page}) => {
    await page.setViewportSize(PHONE)
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/management/declarations")
    await page.getByTestId("declarations-check-30").click()
    await page.getByTestId("declarations-approve").click()
    await page.getByTestId("confirm-go").click()
    await expect(page.getByTestId("declarations-manage-30")).toHaveCount(0)
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(PHONE.width)
  })

  test("a board member copies the transfer of an approved declaration, reveals the IBAN and marks it paid", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/management/declarations/31")
    await expect(page.getByTestId("declaration-review-holder")).toHaveText("Ann Vos")
    await expect(page.getByTestId("declaration-review-iban")).toHaveText("NL •• … ••00")
    await expect(page.getByTestId("declaration-review-amount")).toHaveText("€ 39.99")
    await page.getByTestId("declaration-reveal").click()
    await expect(page.getByTestId("declaration-review-iban")).toHaveText("NL91 ABNA 0417 1643 00")

    await page.getByTestId("declaration-mark-paid").click()
    await expect(page.getByTestId("declaration-paid-dialog")).toContainText("You sent € 39.99 to NL •• … ••00.")
    await page.getByTestId("confirm-go").click()
    await expect(page.getByTestId("declaration-review-state")).toContainText("Paid")
    await expect(page.getByTestId("declaration-review-history")).toContainText("Marked as paid by B. Board, to NL •• … ••00")
    await expect(page.getByTestId("declaration-review-pay-to")).toHaveCount(0)
  })

  test("the transfer and the bulk payout fit a phone", async ({page}) => {
    await page.setViewportSize(PHONE)
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/management/declarations/31")
    await expect(page.getByTestId("declaration-review-reference")).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(PHONE.width)

    await page.goto("/management/declarations")
    await page.getByTestId("declarations-status-search").click()
    await page.getByTestId("declarations-status-pay").click()
    await page.getByTestId("declarations-check-31").click()
    await page.getByTestId("declarations-mark-paid").click()
    await page.getByTestId("confirm-go").click()
    await expect(page.getByTestId("declarations-manage-31")).toHaveCount(0)
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(PHONE.width)
  })
})

