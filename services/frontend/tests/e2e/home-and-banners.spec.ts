import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

test.describe("home page banners", () => {
  test("renders main/social/footer banners", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/")

    await expect(page.locator("#blueshell")).toBeVisible()
    await expect(page.getByText("Follow us on Social Media", {exact: true})).toBeVisible()
    await expect(page.getByText(/SITECIE GANG/i).first()).toBeVisible()

    // The upcoming events run as posters, each saying how full it is and leading to the events.
    const upcoming = page.getByTestId("home-upcoming")
    await expect(upcoming.getByTestId("home-upcoming-head-count")).toContainText("1")
    await expect(upcoming.getByTestId("home-upcoming-strip-500-state")).toHaveText("Sign-ups open · 1 going")
    await expect(upcoming.getByTestId("home-upcoming-strip-500")).toHaveAttribute("href", "/events")

    const becomeMember = page.getByTestId("home-become-member")
    await expect(becomeMember).toBeVisible()
    await expect(page.getByTestId("home-join-discord")).toHaveAttribute("href", "https://discord.gg/23YMFQy")

    await Promise.all([
      page.waitForURL("**/membership/signup"),
      becomeMember.click(),
    ])

    await expect(page).toHaveURL(/\/membership\/signup/)
  })
})
