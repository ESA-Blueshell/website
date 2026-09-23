import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

test.describe("home page banners", () => {
  test("renders main/social/footer banners", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/")

    await expect(page.locator("#blueshell")).toBeVisible()
    await expect(page.getByTestId("home-perks").locator('[data-testid^="home-perks-"]:not([data-testid="home-perks-signup"])')).toHaveCount(6)
    // Only the room somebody is in, with who is in it, joined in Discord itself.
    await expect(page.getByTestId("home-discord-room-1")).toContainText("Emma")
    await expect(page.getByTestId("home-discord-room-1")).toContainText("Viktor")
    await expect(page.getByTestId("home-discord-room-1").getByRole("link", {name: "Join General"}))
      .toHaveAttribute("href", "https://discord.com/channels/324285132133629963/1")
    await expect(page.getByTestId("home-discord-room-9")).toHaveCount(0)
    await expect(page.getByTestId("home-discord-live")).toHaveText("2/40 online")
    await expect(page.getByTestId("home-partners-El Niño")).toHaveAttribute("href", "/partners/el-nino")
    await expect(page.getByTestId("home-call-discord")).toHaveAttribute("href", "https://discord.gg/23YMFQy")
    await expect(page.getByText(/SITECIE GANG/i).first()).toBeVisible()

    // The upcoming events run as posters, each saying how full it is and leading to its own page.
    const upcoming = page.getByTestId("home-upcoming")
    await expect(upcoming.getByTestId("home-upcoming-head-count")).toContainText("1")
    await expect(upcoming.getByTestId("home-upcoming-strip-500-state")).toHaveText("Sign-ups open · 1 going")
    await expect(upcoming.getByTestId("home-upcoming-strip-500")).toHaveAttribute("href", "/events/500")

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
