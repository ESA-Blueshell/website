import {expect, test} from "./test"
import {installApiMocks} from "./mocks"
import {eightSeasonFixtures} from "./esportsStrip"

/**
 * Moving between seasons, and which way.
 *
 * Runs only in the motion project, which does not emulate reduced motion. Everywhere else the
 * band crosses over instead of travelling, by design — so this is the only place the travel
 * exists to be watched.
 *
 * Eight seasons rather than the suite's usual two, because "four years back" is not a thing
 * two seasons can express. 67 is the newest and 64 an older one with a team of its own.
 */
const NEWEST = 67
const OLDER = 64

/** Where each of the two seasons on screen sits, along the axis it is travelling. */
const crossing = async (page: import("@playwright/test").Page) =>
  page.getByTestId("season-swipe").evaluate(el => [...el.children].map(
    child => new DOMMatrix(getComputedStyle(child).transform).m41,
  ))

test.describe("swiping between seasons", () => {
  test("sends the season on screen out to the right and brings an older one in from the left", async ({page}) => {
    await installApiMocks(page, eightSeasonFixtures)
    await page.goto("/esports/valorant")
    const swipe = page.getByTestId("season-swipe")
    await swipe.waitFor()

    await page.getByTestId(`esports-season-node-${OLDER}`).click()

    // Both seasons are on the page at once, which is what makes it a pass rather than a swap.
    await expect.poll(async () => (await crossing(page)).length).toBe(2)
    await expect(swipe).toHaveAttribute("data-swipe", "past")
    await expect(swipe).toHaveAttribute("data-swipe-mode", "slide")

    const [leaving, arriving] = await crossing(page)
    // Oldest is left on the strip, so going back moves the page rightwards: what was here
    // leaves by the right edge, and what is older arrives from the left.
    expect(leaving).toBeGreaterThan(0)
    expect(arriving).toBeLessThan(0)
  })

  test("mirrors it exactly when the season chosen is a later one", async ({page}) => {
    await installApiMocks(page, eightSeasonFixtures)
    await page.goto(`/esports/valorant?season=${OLDER}`)
    const swipe = page.getByTestId("season-swipe")
    await swipe.waitFor()

    await page.getByTestId(`esports-season-node-${NEWEST}`).click()

    await expect.poll(async () => (await crossing(page)).length).toBe(2)
    await expect(swipe).toHaveAttribute("data-swipe", "future")

    const [leaving, arriving] = await crossing(page)
    expect(leaving).toBeLessThan(0)
    expect(arriving).toBeGreaterThan(0)
  })

  test("settles with the season that arrived standing square, and only it", async ({page}) => {
    await installApiMocks(page, eightSeasonFixtures)
    await page.goto("/esports/valorant")
    await page.getByTestId("season-swipe").waitFor()

    await page.getByTestId(`esports-season-node-${OLDER}`).click()

    // Its team's slice is open in the frame the band is first drawn in, with the pass still on:
    // the pass is the whole animation, so nothing grows once it is over. Asked of the page at a
    // frame boundary, and of the arriving season, the one leaving having had its names taken off.
    const landing = await (await page.waitForFunction(() => {
      const slice = document.querySelector("[data-testid=\"team-roster-52\"]")
      if (!slice) return null
      return {
        open: slice.className.includes("slice--open"),
        panels: document.querySelectorAll("[data-testid=\"season-swipe\"] > *").length,
      }
    })).jsonValue()
    expect(landing).toEqual({open: true, panels: 2})

    // The season that left is gone rather than parked off-screen, and the one that arrived is
    // where the band always sits.
    await expect.poll(async () => await crossing(page), {timeout: 5000}).toEqual([0])
    await expect(page.getByTestId("team-roster-52")).toBeAttached()
  })

  test("does not travel when the same season is asked for again", async ({page}) => {
    await installApiMocks(page, eightSeasonFixtures)
    await page.goto("/esports/valorant")
    const swipe = page.getByTestId("season-swipe")
    await swipe.waitFor()

    // The season already shown. Nothing has changed, so nothing should move.
    await page.getByTestId(`esports-season-node-${NEWEST}`).click()

    await expect(swipe).toHaveAttribute("data-swipe", "same")
    expect(await crossing(page)).toEqual([0])
  })

  test("arrives at the last season clicked, however fast they were clicked", async ({page}) => {
    await installApiMocks(page, eightSeasonFixtures)
    await page.goto("/esports/valorant")
    await page.getByTestId("season-swipe").waitFor()

    await page.getByTestId(`esports-season-node-${OLDER}`).click()
    await page.getByTestId(`esports-season-node-${NEWEST}`).click()

    // An answer for the season abandoned mid-flight must not land on top of the one wanted.
    await expect(page.getByTestId("team-roster-51")).toBeAttached()
    await expect.poll(async () => await crossing(page), {timeout: 5000}).toEqual([0])
    expect(new URL(page.url()).searchParams.get("season")).toBe(String(NEWEST))
  })
})
