import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

test.describe("home page banners", () => {
  test("renders main/social/footer banners", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/")

    await expect(page.locator("#blueshell")).toBeVisible()
    await expect(page.getByText("Follow us on Social Media", {exact: true})).toBeVisible()
    await expect(page.getByText(/SITECIE GANG/i).first()).toBeVisible()

    const joinNow = page.getByRole("button", {name: /join now/i}).first()
    await expect(joinNow).toBeVisible()

    await Promise.all([
      page.waitForURL("**/membership/signup"),
      joinNow.click(),
    ])

    await expect(page).toHaveURL(/\/membership\/signup/)
  })
})
