import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin, loginAsBoard} from "./mocks"

test.describe("the Management dashboard", () => {
  test("opens Management for the board without any admin figure", async ({page}) => {
    const adminCalls: string[] = []
    page.on("request", (request) => {
      if (/\/management\/(jobs|exceptions)/.test(request.url())) adminCalls.push(request.url())
    })
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/management")

    await expect(page.getByTestId("dashboard-membership")).toBeVisible()
    await expect(page.getByTestId("management-nav-dashboard")).toHaveAttribute("aria-current", "page")
    await expect(page.getByTestId("dashboard-system")).toHaveCount(0)
    expect(adminCalls).toEqual([])
  })

  test("adds the system figures for an admin, each block linking to its page", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())
    await page.goto("/management")

    await expect(page.getByTestId("dashboard-system")).toContainText("@Admin")
    await page.getByTestId("dashboard-mail").getByRole("link", {name: "Sent mail"}).click()
    await expect(page).toHaveURL(/\/management\/mail\/sent$/)
  })

  test("fits a phone without scrolling sideways", {tag: "@phone"}, async ({page}) => {
    await page.setViewportSize({width: 360, height: 780})
    await installApiMocks(page)
    await loginAsAdmin(page.context())
    await page.goto("/management")

    await expect(page.getByTestId("dashboard-system")).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(overflow).toBeLessThanOrEqual(0)
  })
})
