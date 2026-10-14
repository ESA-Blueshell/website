import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin, loginAsBoard} from "./mocks"
import {aMembership, aUser} from "./records"

const ann = aUser({id: 71, fullName: "Ann Vos", username: "ann", enabled: true, roles: ["MEMBER"]})

test.describe("one user's page", () => {
  test("opens from Users, keeps its tab in the address, and records a payment in place", async ({page}) => {
    await installApiMocks(page, {users: [ann], memberships: [aMembership({id: 171, userId: 71, startDate: "2024-09-01"})]})
    await loginAsBoard(page.context())
    await page.goto("/management/users")

    await page.getByTestId("member-manager-open-71").click()
    await expect(page).toHaveURL(/\/management\/users\/71$/)
    await expect(page.getByTestId("user-row-membership")).toContainText("Regular since 1 Sep 2024")

    await page.getByTestId("user-tab-contributions").click()
    await expect(page).toHaveURL(/\/management\/users\/71\/contributions$/)
    await page.getByTestId("user-period-toggle-1").click()
    await expect(page.getByTestId("user-period-said-1")).toHaveText("Payment recorded.")
    await expect(page.getByTestId("user-period-1")).toContainText("Paid")

    await page.reload()
    await expect(page.getByTestId("user-contributions")).toBeVisible()
  })

  test("reads well on a phone", {tag: "@phone"}, async ({page}) => {
    await page.setViewportSize({width: 360, height: 780})
    await installApiMocks(page, {users: [ann], memberships: [aMembership({id: 171, userId: 71, startDate: "2024-09-01"})]})
    await loginAsBoard(page.context())
    await page.goto("/management/users/71/membership")

    await expect(page.getByTestId("membership-panel")).toBeVisible()
    const overflow = () => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(await overflow()).toBeLessThanOrEqual(0)

    await page.goto("/management/users/71/contributions")
    await expect(page.getByTestId("user-contributions")).toBeVisible()
    expect(await overflow()).toBeLessThanOrEqual(0)
  })
})

test.describe("one user's Account and Roles", () => {
  test("the board has no Roles tab, and the Account tab offers a password reset", async ({page}) => {
    await installApiMocks(page, {users: [ann]})
    await loginAsBoard(page.context())

    await page.goto("/management/users/71")
    await expect(page.getByTestId("user-overview")).toBeVisible()
    await expect(page.getByTestId("user-tab-roles")).toHaveCount(0)

    await page.getByTestId("user-tab-account").click()
    await expect(page.getByTestId("recovery-user-send-btn-PASSWORD_RESET-71")).toBeVisible()
    await expect(page.getByTestId("account-security-panel")).toHaveCount(0)
  })

  test("an admin changes roles from the Roles tab", async ({page}) => {
    await installApiMocks(page, {users: [ann]})
    await loginAsAdmin(page.context())

    await page.goto("/management/users/71")
    await page.getByTestId("user-tab-roles").click()
    await expect(page).toHaveURL(/\/management\/users\/71\/roles$/)
    await expect(page.getByTestId("user-roles-panel")).toBeVisible()
    await expect(page.getByTestId("user-roles-save-btn")).toBeVisible()
  })
})

test.describe("one user's mandate", () => {
  test("the board records a paper mandate and sees only the last two of the account", async ({page}) => {
    await installApiMocks(page, {users: [ann], memberships: [aMembership({id: 171, userId: 71, startDate: "2024-09-01"})]})
    await loginAsBoard(page.context())
    await page.goto("/management/users/71")
    await page.getByTestId("user-row-payment-details").click()
    await expect(page).toHaveURL(/\/management\/users\/71\/payment-details$/)

    await page.getByTestId("mandate-record").click()
    await page.getByTestId("mandate-iban").locator("input").fill("NL91 ABNA 0417 1643 00")
    await page.getByTestId("mandate-holder").locator("input").fill("Ann Vos")
    await page.getByTestId("mandate-save").click()

    await expect(page.getByTestId("mandate-facts")).toContainText("NL•• … ••00")
    await expect(page.getByTestId("mandate-panel")).not.toContainText("0417")

    // A reveal shows the full number until it is hidden again.
    await page.getByTestId("mandate-reveal").click()
    await expect(page.getByTestId("mandate-account")).toContainText("NL91 ABNA 0417 1643 00")
    await page.getByTestId("mandate-reveal").click()
    await expect(page.getByTestId("mandate-panel")).not.toContainText("0417")
  })
})
