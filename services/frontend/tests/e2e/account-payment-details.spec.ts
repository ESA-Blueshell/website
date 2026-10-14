import {expect, test} from "./test"
import {installApiMocks, loginAsMember} from "./mocks"

const PHONE = {width: 390, height: 844}

const OWN = {
  held: true,
  paysByIncasso: false,
  periods: [
    {
      periodId: 3, startDate: "2026-09-01", endDate: "2027-08-31", feeType: "FULL_YEAR_FEE" as const, fee: 30,
      lastEmailKind: "REMINDER" as const, lastEmailAt: "2026-09-12T10:00:00Z", dueDate: "2026-09-26",
    },
    {periodId: 2, startDate: "2025-09-01", endDate: "2026-08-31", feeType: "HALF_YEAR_FEE" as const, fee: 15, paidAt: "2026-03-04T10:00:00Z"},
  ],
}

test.describe("the Payment details page", () => {
  test("is an account tab before Help for a member, listing what is paid and what is not", async ({page}) => {
    await installApiMocks(page, {ownContributions: OWN})
    await loginAsMember(page.context())

    await page.goto("/account")
    await page.getByTestId("account-tab-payment-details").click()

    await expect(page).toHaveURL(/\/account\/payment-details$/)
    await expect(page.locator("h1")).toHaveText("Payment details")
    await expect(page.getByTestId("payment-details-owed")).toContainText("€ 30.00")
    await expect(page.getByTestId("payment-details-period-3")).toContainText("Not paid yet")
    await expect(page.getByTestId("payment-details-period-3")).toContainText("Pay by 26 September 2026")
    await expect(page.getByTestId("payment-details-period-2")).toContainText("Paid 4 March 2026")
    await expect(page.getByTestId("payment-details-bank")).toContainText("NL19 INGB 0008 0964 62")
    await expect(page.getByTestId("payment-details-mandate")).toBeVisible()
  })

  test("is not offered to somebody who never joined", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())

    await page.goto("/account")

    await expect(page.getByTestId("account-tab-help")).toBeVisible()
    await expect(page.getByTestId("account-tab-payment-details")).toHaveCount(0)
  })

  test("fits a phone without scrolling sideways", async ({page}) => {
    await page.setViewportSize(PHONE)
    await installApiMocks(page, {ownContributions: {...OWN, paysByIncasso: true}})
    await loginAsMember(page.context())

    await page.goto("/account/payment-details")

    await expect(page.getByTestId("payment-details-method")).toContainText("Direct debit")
    await expect(page.getByTestId("payment-details-mandate")).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(overflow).toBeLessThanOrEqual(0)
  })
})
