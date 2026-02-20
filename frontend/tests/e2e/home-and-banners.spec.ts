import {expect, test} from "@playwright/test"
import {installApiMocks} from "./mocks"

test.describe("home page banners", () => {
  test("renders main/social/footer banners", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/")

    await expect(page.locator("#blueshell")).toBeVisible({timeout: 30_000})
    await expect(page.getByText("Follow us on Social Media", {exact: true})).toBeVisible()
    await expect(page.getByText(/SITECIE GANG/i).first()).toBeVisible()

    await page.getByRole("button", {name: /join now/i}).click()
    await expect(page).toHaveURL(/\/membership\/signup/)
  })
})
