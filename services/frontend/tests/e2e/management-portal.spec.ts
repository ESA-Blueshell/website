import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin, loginAsBoard, loginAsMember} from "./mocks"

test.describe("the Management portal", () => {
  test("the account menu switches into Management and back to the site", {tag: "@phone"}, async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/")

    await page.getByTestId("nav-account").click()
    await page.getByTestId("nav-switch-management").first().click()

    await expect(page).toHaveURL(/\/management$/)
    await expect(page.getByTestId("management-bar")).toBeVisible()
    await expect(page.getByTestId("management-dashboard")).toBeVisible()

    await page.getByTestId("management-account").click()
    await page.getByTestId("management-back-to-site").click()
    await expect(page).toHaveURL(/\/$/)
    await expect(page.getByTestId("management-bar")).toHaveCount(0)
  })

  test("the sidebar groups the pages and marks the one open", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto("/management/recovery")

    const sidebar = page.getByTestId("management-sidebar")
    await expect(sidebar).toBeVisible()
    await expect(page.getByTestId("management-nav-account-recovery")).toHaveAttribute("aria-current", "page")
    await expect(sidebar).toContainText("@Admin")
  })

  test("a phone gets the bottom bar, and More lists the rest", {tag: "@phone"}, async ({page}) => {
    await page.setViewportSize({width: 390, height: 844})
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/management/users")
    await expect(page.getByTestId("management-sidebar")).toBeHidden()
    await page.getByTestId("management-tab-more").click()

    await expect(page).toHaveURL(/\/management\/more$/)
    await expect(page.getByTestId("management-more-account-recovery")).toBeVisible()
  })

  test("old management addresses land on their new pages", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/user-manager")
    await expect(page).toHaveURL(/\/management\/users$/)
    await page.goto("/management/emails")
    await expect(page).toHaveURL(/\/management\/mail\/sent$/)
    await page.goto("/management/cohorts/targets")
    await expect(page).toHaveURL(/\/management\/platforms\/brevo$/)
  })

  test("a member is shown the unauthorized page", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())

    await page.goto("/management/users")

    await expect(page).toHaveURL(/\/unauthorized/)
  })
})
