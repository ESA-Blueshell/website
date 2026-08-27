import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

/**
 * Runs in the motion project, which does not emulate reduced motion. The line lighting up is
 * the whole point of the timeline, and it is invisible to every other project by design.
 */
test.describe("the season timeline, with motion", () => {
  test("lights the line as far as the season under the pointer", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/valorant")
    await page.getByTestId("esports-season-timeline").waitFor()

    const litWidth = () => page.locator(".season-timeline__lit")
      .evaluate(el => el.getBoundingClientRect().width)

    // At rest the line runs as far as the season on show, which is the newest.
    const atRest = await litWidth()

    await page.getByTestId("esports-season-node-19").hover()
    await expect.poll(litWidth).toBeLessThan(atRest)

    await page.getByTestId("esports-season-node-20").hover()
    await expect.poll(litWidth).toBe(atRest)
  })

  test("turns a team card over when it is hovered, and back when it is left", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/valorant")
    const card = page.getByTestId("team-roster-1")
    await card.waitFor()

    const turn = () => card.locator(".team-card__inner")
      .evaluate(el => getComputedStyle(el).transform)

    // Untouched the card carries no transform at all.
    await expect.poll(turn).toBe("none")

    await card.hover()
    // Half a turn about the y axis, which the computed matrix reports as 3d.
    await expect.poll(turn).not.toBe("none")

    await page.mouse.move(0, 0)
    await expect.poll(turn).toBe("none")
  })
})
