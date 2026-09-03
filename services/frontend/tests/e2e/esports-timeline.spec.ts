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
  page.locator(".timeline__scroll").evaluate(el => el.scrollLeft)

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

  test("leaves one band on the page while a season is travelling, not two", async ({page}) => {
    await installApiMocks(page, fixtures)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await expect(page.getByTestId("team-roster-51")).toBeVisible()

    // The band is built again on a season change now, because the change is something the
    // visitor watches happen rather than something that has quietly already happened: for the
    // length of a pass both seasons are on the page at once. What used to stop the switch
    // reading as a page rebuilding itself was the band surviving it; what does that now is the
    // travel, and the game being read carrying across it.
    await page.getByTestId("esports-season-node-64").click()
    await expect(page.getByTestId("team-roster-52")).toBeVisible()

    // The season leaving is a picture of a season by then, still on screen while it goes but
    // out of the tab order, out of what is read aloud, and no longer answering to its name.
    await expect(page.getByTestId("team-roster-slices")).toHaveCount(1)
    await expect(page.getByTestId("team-roster-51")).toHaveCount(0)
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

  /*
   * A way out in the direction there is room in, and none in the direction there is not.
   *
   * Which end the strip opens at is not the strip's own business any more: a band has a floor,
   * and a phone's is a great deal wider than a pointer's — 320px against 94, so that a node is
   * hittable by a thumb and both its labels fit — so how many of eight seasons are in the window
   * is a question about the window. What is asserted is therefore each end rather than whichever
   * end this project happens to open at, which is both stronger and true on any width.
   */
  test("offers the way to the seasons that do not fit", async ({page}) => {
    await installApiMocks(page, fixtures)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await expect(page.getByTestId("team-roster-51")).toBeVisible()

    // Eight seasons fit no window this suite runs in, so there is always somewhere to go.
    const scroller = page.locator(".timeline__scroll")
    expect(await scroller.evaluate(el => el.scrollWidth - el.clientWidth)).toBeGreaterThan(1)

    await scroller.evaluate(el => {
      el.scrollLeft = 0
    })
    await expect(page.getByTestId("esports-season-pan-back")).toHaveCount(0)
    await expect(page.getByTestId("esports-season-pan-on")).toBeVisible()

    await scroller.evaluate(el => {
      el.scrollLeft = el.scrollWidth
    })
    await expect(page.getByTestId("esports-season-pan-on")).toHaveCount(0)
    await expect(page.getByTestId("esports-season-pan-back")).toBeVisible()
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
    /*
     * The two seasons the other specs get, in a window with room for them.
     *
     * The width is said here rather than left to the project, because "every season fits" is a
     * statement about the room there is and no longer one about how many seasons there are. Two
     * bands at a phone's floor are wider than a phone, which is the point of that floor and the
     * subject of the test below.
     */
    await page.setViewportSize({width: 1280, height: 900})
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await expect(page.getByTestId("esports-season-timeline")).toBeVisible()

    await expect(page.getByTestId("esports-season-pan-back")).toHaveCount(0)
    await expect(page.getByTestId("esports-season-pan-on")).toHaveCount(0)
  })

  /*
   * And on a phone even two seasons are somewhere to go.
   *
   * A band's floor is the reader's rather than the strip's. At the pointer's figure a 390px
   * screen fitted four bands, so every label was clipped at one end of the window or the other
   * and the outermost sat under the arrows that pan the strip. At a thumb's, a phone shows one
   * stop and a little of its neighbours — so a strip a desktop reads whole is a strip a phone
   * scrolls, and it has to say so.
   */
  test("offers the way on a phone, where two seasons are already more than fit", async ({page}) => {
    await page.setViewportSize({width: 390, height: 900})
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await expect(page.getByTestId("esports-season-timeline")).toBeVisible()

    const scroller = page.locator(".timeline__scroll")
    expect(await scroller.evaluate(el => el.scrollWidth - el.clientWidth)).toBeGreaterThan(1)

    await scroller.evaluate(el => {
      el.scrollLeft = 0
    })
    await expect(page.getByTestId("esports-season-pan-on")).toBeVisible()
    await expect(page.getByTestId("esports-season-pan-back")).toHaveCount(0)
  })
})
