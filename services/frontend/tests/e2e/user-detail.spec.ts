import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"
import {aMembership, aUser} from "./records"

const ann = aUser({id: 71, fullName: "Ann Vos", username: "ann", enabled: true, roles: ["MEMBER"]})

test.describe("one user's page", () => {
  test("opens from Users, keeps its tab in the address, and records a payment in place", async ({page}) => {
    await installApiMocks(page, {users: [ann], memberships: [aMembership({id: 171, userId: 71, startDate: "2024-09-01", incasso: false})]})
    await loginAsBoard(page.context())
    await page.goto("/management/users")

    await page.getByTestId("member-manager-open-71").click()
    await expect(page).toHaveURL(/\/management\/users\/71$/)
    await expect(page.getByTestId("user-overview")).toContainText("Pays by transfer")

    await page.getByTestId("user-tab-contributions").click()
    await expect(page).toHaveURL(/\/management\/users\/71\/contributions$/)
    await page.getByTestId("user-period-toggle-1").click()
    await expect(page.getByTestId("user-period-said-1")).toHaveText("Payment recorded.")
    await expect(page.getByTestId("user-period-1")).toContainText("Paid on")

    await page.reload()
    await expect(page.getByTestId("user-contributions")).toBeVisible()
  })

  test("reads well on a phone", {tag: "@phone"}, async ({page}) => {
    await page.setViewportSize({width: 360, height: 780})
    await installApiMocks(page, {users: [ann], memberships: [aMembership({id: 171, userId: 71, startDate: "2024-09-01"})]})
    await loginAsBoard(page.context())
    await page.goto("/management/users/71/membership")

    await expect(page.getByTestId("membership-panel")).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(overflow).toBeLessThanOrEqual(0)
  })
})
