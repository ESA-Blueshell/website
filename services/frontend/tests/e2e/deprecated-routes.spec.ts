import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

test.describe("deprecated routes", () => {
  test("an esports address no game claims is not-found and returns home via fallback link", async ({page}) => {
    await installApiMocks(page)

    // Any address under /esports now reaches the one page that serves every game, so what
    // makes this a miss is that no record answers to it.
    await page.goto("/esports/trackmania-2")

    await expect(page).toHaveURL(/\/esports\/trackmania-2$/)
    await expect(page.getByText(/Uh oh, we made a fucky wucky!/i)).toBeVisible()

    const homeLink = page.getByRole("link", {name: "here"})
    await expect(homeLink).toHaveAttribute("href", "/")
    await homeLink.click()

    await expect(page).toHaveURL(/\/$/)
    await expect(page.locator("#blueshell")).toBeVisible()
  })
})
