import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin, loginAsBoard, loginAsMember} from "./mocks"

test.describe("the Management portal", () => {
  test("the account menu switches into Management and back to the site", {tag: "@phone"}, async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/")

    await page.getByTestId("nav-account").click()
    await page.getByTestId("nav-switch-management").first().click()

    await expect(page).toHaveURL(/\/management\/more$/)
    await expect(page.getByTestId("management-bar")).toBeVisible()
    await expect(page.getByTestId("management-more")).toBeVisible()
    await expect(page.locator("header.site-bar")).toHaveCount(0)

    await page.getByTestId("management-account").click()
    await page.getByTestId("management-back-to-site").click()
    await expect(page).toHaveURL(/\/$/)
    await expect(page.getByTestId("management-bar")).toHaveCount(0)
  })

  test("the sidebar groups the pages and marks the admin's own", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto("/management")

    const sidebar = page.getByTestId("management-sidebar")
    await expect(sidebar).toBeVisible()
    await expect(sidebar).toContainText("@Admin")
    await page.getByTestId("management-nav-jobs").click()
    await expect(page).toHaveURL(/\/management\/jobs$/)
  })

  test("a phone gets the bottom bar, and More lists every page", {tag: "@phone"}, async ({page}) => {
    await page.setViewportSize({width: 390, height: 844})
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/management")

    await expect(page.getByTestId("management-sidebar")).toBeHidden()
    await expect(page.getByTestId("management-tab-more")).toBeVisible()
    await page.getByTestId("management-more-account-recovery").click()
    await expect(page).toHaveURL(/\/management\/recovery$/)
  })

  test("a member is turned away", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())

    await page.goto("/management")

    await expect(page).toHaveURL(/\/$/)
    await expect(page.getByTestId("management-bar")).toHaveCount(0)
  })
})
