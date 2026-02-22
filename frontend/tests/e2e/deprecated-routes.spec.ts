import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

test.describe("deprecated routes", () => {
  test("trackmania route resolves to not-found and returns home via fallback link", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/trackmania")

    await expect(page).toHaveURL(/\/esports\/trackmania$/)
    await expect(page.getByText(/Uh oh, we made a fucky wucky!/i)).toBeVisible()

    const homeLink = page.getByRole("link", {name: "here"})
    await expect(homeLink).toHaveAttribute("href", "/")
    await homeLink.click()

    await expect(page).toHaveURL(/\/$/)
    await expect(page.locator("#blueshell")).toBeVisible()
  })
})
