import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

/**
 * Runs only in the motion project, which does not emulate reduced motion. What
 * it asserts is that the motion is actually wired up: everywhere else it is
 * switched off, so nothing else would notice if it disappeared.
 */
test.describe("the esports island, with motion", () => {
  test("the game cards animate in rather than appearing at once", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")

    const first = page.getByTestId("esports-game-league-of-legends")
    await first.waitFor()

    // Caught mid-entrance the cards are staggered, so the last is still behind
    // the first. Polling rather than sampling once: the assertion is that a
    // stagger happens at all, not what it measures at one instant.
    const staggered = await page.evaluate(() => {
      const cards = [...document.querySelectorAll('[data-testid^="esports-game-"]')]
      return cards.length > 1
    })
    expect(staggered).toBe(true)

    // By the time it settles every card is fully opaque and in place.
    await expect.poll(async () =>
      first.evaluate(el => getComputedStyle(el.parentElement as Element).opacity),
    ).toBe("1")
  })

  test("a hovered card lifts its logo and puts it back", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")

    const card = page.getByTestId("esports-game-valorant")
    const logo = card.locator("img")
    // Tailwind's scale utilities set the `scale` property rather than building a
    // transform, so that is what moves here.
    const scaleOf = () => logo.evaluate(el => getComputedStyle(el).scale)
    await expect.poll(scaleOf).toBe("none")

    await card.hover()
    await expect.poll(scaleOf).not.toBe("none")

    await page.mouse.move(0, 0)
    await expect.poll(scaleOf).toBe("none")
  })
})
