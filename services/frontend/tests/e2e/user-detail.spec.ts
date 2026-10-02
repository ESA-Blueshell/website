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
    await expect(page.getByTestId("mandate-standing")).toHaveText("Pays by transfer")
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(overflow).toBeLessThanOrEqual(0)
  })
})

test.describe("one user's Account and Roles", () => {
  test("the board reads the roles but cannot change them, and the Account tab offers a password reset", async ({page}) => {
    await installApiMocks(page, {users: [ann]})
    await loginAsBoard(page.context())

    await page.goto("/management/users/71/roles")
    await expect(page.getByTestId("user-roles-panel")).toBeVisible()
    await expect(page.getByTestId("user-roles-save-btn")).toHaveCount(0)

    await page.getByTestId("user-tab-account").click()
    await expect(page.getByTestId("recovery-user-send-btn-PASSWORD_RESET-71")).toBeVisible()
    await expect(page.getByTestId("account-security-panel")).toBeVisible()
  })
})

test.describe("one user's mandate", () => {
  test("the board records a paper mandate and sees only the last four of the account", async ({page}) => {
    await installApiMocks(page, {users: [ann], memberships: [aMembership({id: 171, userId: 71, startDate: "2024-09-01"})]})
    await loginAsBoard(page.context())
    await page.goto("/management/users/71/membership")

    await page.getByTestId("mandate-record").click()
    await page.getByTestId("mandate-iban").locator("input").fill("NL91 ABNA 0417 1643 00")
    await page.getByTestId("mandate-holder").locator("input").fill("Ann Vos")
    await page.getByTestId("mandate-save").click()

    await expect(page.getByTestId("mandate-facts")).toContainText("NL•• … ••00")
    await expect(page.getByTestId("mandate-panel")).not.toContainText("0417")
  })
})
