import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

/**
 * The season strip's own behaviour: what a click does to it, and how the seasons that do not
 * fit are reached.
 *
 * Eight seasons rather than the two the other specs get, because both questions only arise
 * once the strip holds more than its window shows.
 */
const GAME_PAGE = "/esports/valorant"

const eight = Array.from({length: 8}, (_, i) => ({
  id: 60 + i,
  name: `${i % 2 === 0 ? "Autumn" : "Spring"} ${2018 + i}/${19 + i}`,
  startDate: `${2018 + i}-09-01`,
  endDate: `${2019 + i}-01-31`,
}))

const pageOf = (index: number, teamId: number, teamName: string) => ({
  game: "VALORANT",
  season: eight[index],
  seasons: eight,
  teams: [{
    id: teamId,
    name: teamName,
    image: "valorantesports1.jpg",
    members: [{role: "PLAYER", handle: "AriosFury"}],
  }],
})

/** The newest season is what the api answers with when none was asked for. */
const NEWEST = pageOf(7, 51, "BS Waterboarders")

const fixtures = {
  esportsSeasons: eight,
  esportsPages: {
    "20": NEWEST,
    "67": NEWEST,
    "64": pageOf(4, 52, "BS Tempra"),
  },
}

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

    // Held long enough to look at: the answer to the next season arrives after the assertions
    // below, which is the window a visitor sees as a blink today.
    await page.route("**/esports/games/**", async route => {
      await new Promise(resolve => setTimeout(resolve, 1200))
      await route.fallback()
    })

    await page.getByTestId("esports-season-node-64").click()

    // The season before it is still readable, and nothing pulses in its place.
    await expect(page.getByTestId("team-roster-51")).toBeVisible()
    await expect(page.getByTestId("esports-loading")).toHaveCount(0)
    await expect(page.getByTestId("team-roster-52")).toBeVisible()
  })

  test("offers the way to the seasons that do not fit, and travels while it is hovered", async ({page}) => {
    await installApiMocks(page, fixtures)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await expect(page.getByTestId("team-roster-51")).toBeVisible()

    // It opens on the newest season, which is the far end: there is a way back and no way on.
    const back = page.getByTestId("esports-season-pan-back")
    await expect(back).toBeVisible()
    await expect(page.getByTestId("esports-season-pan-on")).toHaveCount(0)

    const before = await scrolledTo(page)
    await back.hover()

    await expect.poll(() => scrolledTo(page)).toBeLessThan(before)
    // And once it has travelled, the way on appears.
    await expect(page.getByTestId("esports-season-pan-on")).toBeVisible()
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
