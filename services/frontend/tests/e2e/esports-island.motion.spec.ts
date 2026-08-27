import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

/**
 * Runs only in the motion project, which does not emulate reduced motion. Everywhere else the
 * motion is switched off, so nothing else would notice if it disappeared.
 */
test.describe("the esports index, with motion", () => {
  test("opens the first game of the season, and passes it along on hover", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")
    const slices = page.getByTestId("esports-game-slices")
    await slices.waitFor()

    const each = slices.locator('[data-testid^="esports-game-"]')
    const first = each.first()
    const second = each.nth(1)

    // The first opens itself once the page has settled, so the opening is seen happening.
    await expect.poll(async () => first.getAttribute("class")).toContain("team-slice--open")

    await second.hover()

    await expect.poll(async () => second.getAttribute("class")).toContain("team-slice--open")
    await expect.poll(async () => first.getAttribute("class")).not.toContain("team-slice--open")
  })

  test("brightens a game's banner as its slice opens", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")
    const slices = page.getByTestId("esports-game-slices")
    await slices.waitFor()

    const second = slices.locator('[data-testid^="esports-game-"]').nth(1)
    const banner = second.locator("img")
    if (await banner.count() === 0) test.skip()

    const grey = () => banner.evaluate(el => getComputedStyle(el).filter)
    const shut = await grey()

    await second.hover()

    // Shut it is greyed and dimmed; open it is the game's own picture.
    await expect.poll(grey).not.toBe(shut)
  })
})
