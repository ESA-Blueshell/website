import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

test.describe("esports mobile layout", () => {
  test("opens a team's roster on a tap, since there is no pointer to hover with", async ({page}) => {
    await installApiMocks(page)
    await page.setViewportSize({width: 390, height: 844})

    await page.goto("/esports/valorant")

    await expect(page).toHaveURL(/\/esports\/valorant$/)
    const team = page.getByTestId("team-roster-1")
    await expect(team).toContainText("BS Waterboarders")
    await expect(team).toContainText("3 on the roster")

    // A card turns over on hover, which a touch screen cannot offer; tapping pins it over.
    const card = team.getByRole("button")
    await expect(card).toHaveAttribute("aria-expanded", "false")

    await card.click()

    await expect(card).toHaveAttribute("aria-expanded", "true")
    await expect(team).toContainText("Players")
    await expect(team).toContainText("AriosFury")
  })

  test("names the season on show, where there is no room to label every node", async ({page}) => {
    await installApiMocks(page)
    await page.setViewportSize({width: 390, height: 844})

    await page.goto("/esports/valorant")

    // The half labels give way to one caption on a narrow screen: twelve of them collide.
    await expect(page.getByTestId("esports-season-caption")).toContainText("Autumn 2025/26")
  })
})
