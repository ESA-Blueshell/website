import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

test.describe("esports mobile layout", () => {
  test("stacks a team's name above its roster in a narrow viewport", async ({page}) => {
    await installApiMocks(page)
    await page.setViewportSize({width: 390, height: 844})

    await page.goto("/esports/valorant")

    await expect(page).toHaveURL(/\/esports\/valorant$/)
    const team = page.getByTestId("team-roster-1")
    await expect(team).toContainText("BS Waterboarders")
    await expect(team).toContainText("Players")
    await expect(team).toContainText("AriosFury")

    // Stacked, not side by side: the name sits above the roster rather than beside it.
    const name = await team.getByTestId("team-roster-name").boundingBox()
    const member = await team.locator(".team-roster__member").first().boundingBox()
    expect(name!.y + name!.height).toBeLessThanOrEqual(member!.y)
  })
})
