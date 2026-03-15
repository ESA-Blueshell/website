import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

test.describe("esports mobile layout", () => {
  test("valorant page renders core team labels in mobile viewport", async ({page}) => {
    await installApiMocks(page)
    await page.setViewportSize({width: 390, height: 844})

    await page.goto("/esports/valorant")

    await expect(page).toHaveURL(/\/esports\/valorant$/)
    await expect(page.getByText("BS Waterboarders")).toBeVisible()
    await expect(page.getByText("Players").first()).toBeVisible()
    await expect(page.getByText("Substitutes").first()).toBeVisible()
    await expect(page.getByText("Coach").first()).toBeVisible()
  })
})
