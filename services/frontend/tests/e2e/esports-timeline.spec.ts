import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"
import {eightSeasonFixtures} from "./esportsStrip"

/**
 * The season strip's own behaviour: what a click does to it, and how the seasons that do not
 * fit are reached.
 *
 * Eight seasons rather than the two the other specs get, because both questions only arise
 * once the strip holds more than its window shows. The strip travelling while an arrow is
 * hovered is choreography, so it is asserted in `esports-timeline.motion.spec.ts` instead:
 * every project but that one runs with reduced motion emulated.
 */
const GAME_PAGE = "/esports/valorant"

const fixtures = eightSeasonFixtures

const scrolledTo = (page: import("./test").Page) =>
  page.locator(".season-strip__scroll").evaluate(el => el.scrollLeft)

test.describe("the season strip", () => {
  test("leaves the node that was clicked where it was clicked", async ({page}) => {
    await installApiMocks(page, fixtures)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await expect(page.getByTestId("team-roster-51")).toBeVisible()

    // Hovering settles the strip on the node the way reaching for it would, so what is
    // measured afterwards is the strip at rest rather than mid-scroll.
    const older = page.getByTestId("esports-season-node-64")
    await older.hover()
    const before = await scrolledTo(page)

    await older.click()

    // The season changed, and the strip did not travel to put it in the middle: the node the
    // visitor aimed at is still under the pointer.
    await expect(page.getByTestId("team-roster-52")).toBeVisible()
    expect(await scrolledTo(page)).toBe(before)
  })

  test("changes the teams in place rather than rebuilding the band", async ({page}) => {
    await installApiMocks(page, fixtures)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await expect(page.getByTestId("team-roster-51")).toBeVisible()

    // Marked on the element itself: a mark that survives is the same element, and an element
    // that survives has not been thrown away and built again — which is what made the band
    // blink and replay its entrance on every switch.
    const band = page.getByTestId("team-roster-slices")
    await band.evaluate(el => {
      (el as HTMLElement & {dataset: Record<string, string>}).dataset.survived = "yes"
    })

    await page.getByTestId("esports-season-node-64").click()

    await expect(page.getByTestId("team-roster-52")).toBeVisible()
    await expect(band).toHaveAttribute("data-survived", "yes")
  })

  test("keeps the season on screen while the next one is being asked about", async ({page}) => {
    await installApiMocks(page, fixtures)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await expect(page.getByTestId("team-roster-51")).toBeVisible()

    // Held open rather than delayed: the next season's answer arrives when this test says so,
    // so the window a visitor sees as a blink is inspected rather than raced against a clock.
    let release = () => {}
    const held = new Promise<void>(resolve => {
      release = resolve
    })
    await page.route("**/esports/games/**", async route => {
      await held
      await route.fallback()
    })

    const asked = page.waitForRequest(/esports\/games/)
    await page.getByTestId("esports-season-node-64").click()
    await asked

    // While it is in flight: the season before it is still readable, and nothing pulses in
    // its place.
    await expect(page.getByTestId("team-roster-51")).toBeVisible()
    await expect(page.getByTestId("esports-loading")).toHaveCount(0)

    release()

    await expect(page.getByTestId("team-roster-52")).toBeVisible()
  })

  test("offers the way to the seasons that do not fit", async ({page}) => {
    await installApiMocks(page, fixtures)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await expect(page.getByTestId("team-roster-51")).toBeVisible()

    // It opens on the newest season, which is the far end: there is a way back and no way on.
    await expect(page.getByTestId("esports-season-pan-back")).toBeVisible()
    await expect(page.getByTestId("esports-season-pan-on")).toHaveCount(0)
  })

  test("keeps a season under the arrow clickable", async ({page}) => {
    await installApiMocks(page, fixtures)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await expect(page.getByTestId("team-roster-51")).toBeVisible()

    // Nothing has been chosen yet, so the url says nothing about a season.
    await expect(page).not.toHaveURL(/season=/)

    // The fade down the side is a picture and the chevron is small, so a band under either is
    // still a band to be clicked. Which season sits at that edge depends on how wide the
    // window is, so what is asserted is that one of them answered rather than which.
    const strip = page.getByTestId("esports-season-timeline")
    const box = (await strip.boundingBox())!
    await page.mouse.click(box.x + 12, box.y + box.height - 12)

    await expect(page).toHaveURL(/season=\d+/)
  })

  test("offers no way anywhere when every season fits", async ({page}) => {
    // The two seasons the other specs get, which fit any window this suite runs in.
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await expect(page.getByTestId("esports-season-timeline")).toBeVisible()

    await expect(page.getByTestId("esports-season-pan-back")).toHaveCount(0)
    await expect(page.getByTestId("esports-season-pan-on")).toHaveCount(0)
  })
})
