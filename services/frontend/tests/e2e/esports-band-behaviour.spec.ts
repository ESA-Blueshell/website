import {expect, test, type Page} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

/**
 * Two seasons of the same size, for the specs about where the reader is left standing.
 *
 * The seeded seasons are not the same size — the newer one fields two teams and the older
 * one fields a single team — and a page that is genuinely shorter than the offset its reader
 * is standing at is pulled up the window by the browser, which no amount of not scrolling can
 * prevent. That is a real answer to "there is less to read now" and it is not the movement
 * these specs are about, so it is designed out rather than tolerated: four squads either way,
 * of the same shape, named apart only so the season that has arrived can be told from the one
 * that left.
 */
const evenSeasons = [
  {id: 20, name: "Autumn 2025/26", startDate: "2025-09-01", endDate: "2026-01-31"},
  {id: 19, name: "Spring 2024/25", startDate: "2025-02-01", endDate: "2025-08-31"},
]

// Never id 1: that team's line-up is answered from the roster the admin specs write, which
// would make one of the four a different height from the other three.
const squad = (id: number, name: string) => ({
  id,
  name: `BS ${name}`,
  banner: null,
  members: [
    {role: "PLAYER", handle: `${name}One`},
    {role: "PLAYER", handle: `${name}Two`},
    {role: "SUBSTITUTE", handle: `${name}Sub`},
  ],
})

const evenSeasonFixtures = {
  esportsSeasons: evenSeasons,
  esportsPages: {
    "20": {
      game: "VALORANT",
      season: evenSeasons[0],
      seasons: evenSeasons,
      teams: [squad(101, "Alpha"), squad(102, "Bravo"), squad(103, "Charlie"), squad(104, "Delta")],
    },
    "19": {
      game: "VALORANT",
      season: evenSeasons[1],
      seasons: evenSeasons,
      teams: [squad(201, "Echo"), squad(202, "Foxtrot"), squad(203, "Golf"), squad(204, "Hotel")],
    },
  },
}

/**
 * Stands as far down the page as it can be read from with the strip clear of the app bar, and
 * answers where the window settled.
 *
 * Playwright brings an element into view before it clicks it, and that scrolling is the
 * test's own rather than the router's — so a spec that left anything for it to do would be
 * asserting against itself. Clear of the bar rather than merely inside the window, because
 * the bar is fixed: a strip tucked under it is not in view as far as the click is concerned,
 * and the page is scrolled to the top to free it. What is left is the height of the page's
 * own header, which is far enough that being thrown back up it is unmistakable.
 */
const standBelowTheHeader = async (page: Page) => {
  await page.getByTestId("esports-season-timeline").waitFor()
  await page.evaluate(() => {
    const strip = document.querySelector("[data-testid=\"esports-season-timeline\"]") as HTMLElement
    const bar = document.querySelector(".v-app-bar")
    const clear = (bar?.getBoundingClientRect().bottom ?? 0) + 8
    window.scrollTo(0, window.scrollY + strip.getBoundingClientRect().top - clear)
  })
  return page.evaluate(() => window.scrollY)
}

/** Waits out the pass, so what is asserted is where the reader was left and not a moment in it. */
const seasonSettled = async (page: Page) => {
  await expect
    .poll(() => page.locator("[data-testid=\"season-swipe\"] > *").count())
    .toBe(1)
}

/**
 * How the band and the strip behave once a reader is moving around them: what stays open,
 * what a click follows, and how much of a long history the strip shows at once.
 */
test.describe("moving around the esports pages", () => {
  test("a whole game slice is the way into its page", async ({page}, info) => {
    test.skip(info.project.name === "mobile-chrome", "There is no pointer to open a slice with.")
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-slices").waitFor()

    // Opening it and going to it are the same gesture, one after the other.
    const valorant = page.getByTestId("esports-game-VALORANT")
    await valorant.hover()
    await expect(valorant).toHaveClass(/team-slice--open/)
    await valorant.click()

    await expect(page).toHaveURL(/\/esports\/valorant\?season=20$/)
  })

  test("what was last looked at stays open", async ({page}, info) => {
    test.skip(info.project.name === "mobile-chrome", "Stacked, the scroll decides what is open.")
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-slices").waitFor()

    const second = page.getByTestId("esports-game-CS2")
    await second.hover()
    await expect(second).toHaveClass(/team-slice--open/)

    // The pointer leaves the band entirely; the slice it left holds rather than snapping back.
    await page.mouse.move(10, 10)
    await page.waitForTimeout(400)
    await expect(second).toHaveClass(/team-slice--open/)
  })

  test("a slice's affordances go when the pointer does, and do not latch on a click", async ({page}, info) => {
    test.skip(info.project.name === "mobile-chrome", "There is no pointer to hover with.")
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/esports/valorant")

    const slice = page.getByTestId("team-roster-1")
    const pencil = page.getByTestId("team-roster-edit-1")
    await slice.hover()
    await expect(pencil).toBeVisible()

    // Opening a slice focuses the body that was clicked. That is not a reason for the slice
    // to keep offering to be edited once the pointer has moved off it.
    await slice.click()
    await page.mouse.move(10, 10)

    await expect(pencil).toBeHidden()
  })

  test("a keyboard arriving at a slice reveals its affordances", async ({page}, info) => {
    test.skip(info.project.name === "mobile-chrome", "The affordances stand, so there is nothing to reveal.")
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/esports/valorant")

    const pencil = page.getByTestId("team-roster-edit-1")
    await expect(page.getByTestId("team-roster-1")).toBeVisible()
    await expect(pencil).toBeHidden()

    // Into the band from the strip above it, which is where a keyboard comes from.
    await page.getByTestId("esports-season-add").focus()
    await page.keyboard.press("Tab")
    await expect(pencil).toBeVisible()

    // Revealed by the keyboard and not by the click that would have latched it: moving on
    // takes it away again rather than leaving it behind.
    await page.keyboard.press("Tab")
    await expect(pencil).toBeHidden()
  })

  test("a tap leaves a slice's affordances standing, having nothing to hover with", async ({page}, info) => {
    test.skip(info.project.name !== "mobile-chrome", "Only a touch screen has nothing to hover with.")
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/esports/valorant")
    await expect(page.getByTestId("team-roster-1")).toBeVisible()

    // A tap is the only way to reach anything here, and it leaves the focus behind it — so a
    // rule about pointers must not be allowed to hide what the tap just reached.
    await page.getByTestId("esports-season-node-19").tap()
    await expect(page.getByTestId("team-roster-3")).toBeVisible()
    await expect(page.getByTestId("esports-season-edit-19")).toBeVisible()

    await page.getByTestId("team-roster-3").tap()
    await expect(page.getByTestId("team-roster-edit-3")).toBeVisible()
  })

  test("the strip holds a season's width however long the history is", async ({page}, info) => {
    test.skip(info.project.name === "mobile-chrome", "A phone scrolls the strip by design.")
    const many = Array.from({length: 12}, (_, i) => ({
      id: 40 + i,
      name: `Season ${2014 + i}/${String(15 + i).padStart(2, "0")}`,
      startDate: `${2014 + i}-09-01`,
      endDate: `${2015 + i}-01-31`,
    }))
    await installApiMocks(page, {esportsSeasons: many})
    await loginAsBoard(page.context())
    await page.route("**/esports/games/**", async (route) => {
      const url = new URL(route.request().url())
      if (!/\/esports\/games\/[A-Z0-9_]+$/.test(url.pathname)) return route.fallback()
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          game: url.pathname.split("/").pop(),
          season: many[many.length - 1],
          seasons: many,
          teams: [{id: 1, name: "BS One", image: null, members: [{role: "PLAYER", handle: "a"}]}],
        }),
      })
    })
    await page.goto("/esports/valorant")
    await page.getByTestId("esports-season-timeline").waitFor()

    const measured = await page.evaluate(() => {
      const strip = document.querySelector('[data-testid="esports-season-timeline"]') as HTMLElement
      const band = strip.querySelector(".season-slot") as HTMLElement
      return {
        strip: Math.round(strip.getBoundingClientRect().width),
        band: Math.round(band.getBoundingClientRect().width),
      }
    })

    // Twelve seasons and an offer of another do not squeeze a band into a sliver: the strip
    // keeps a band about a sixth of itself and scrolls the rest.
    expect(measured.band).toBeGreaterThan(measured.strip / 8)
  })

  /**
   * Choosing a season writes it into the url, which is a navigation as far as the router is
   * concerned — and a router that opens every page at the top used to throw the reader back
   * up the page each time they picked one, away from the very thing they had scrolled down to
   * read. Asserted on both pages, because the strip is on both and the reader is the same
   * person either way.
   */
  test("choosing a season on the index leaves the reader where they were reading", async ({page}) => {
    await installApiMocks(page, evenSeasonFixtures)
    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-slices").waitFor()

    const standing = await standBelowTheHeader(page)
    expect(standing).toBeGreaterThan(0)

    await page.getByTestId("esports-season-node-19").click()

    // The older season is the one CS:GO played, so its slice arriving is the season arriving.
    await expect(page.getByTestId("esports-game-CSGO")).toBeVisible()
    await seasonSettled(page)

    await expect(page).toHaveURL(/season=19/)
    // Within a pixel: on a phone the window's offset is not a whole number of them.
    expect(Math.abs(await page.evaluate(() => window.scrollY) - standing)).toBeLessThanOrEqual(1)
  })

  test("choosing a season on a game's page leaves the reader where they were reading", async ({page}) => {
    await installApiMocks(page, evenSeasonFixtures)
    await page.goto("/esports/valorant")
    await page.getByTestId("team-roster-101").waitFor()

    const standing = await standBelowTheHeader(page)
    expect(standing).toBeGreaterThan(0)

    await page.getByTestId("esports-season-node-19").click()

    await expect(page.getByTestId("team-roster-201")).toBeVisible()
    await seasonSettled(page)

    await expect(page).toHaveURL(/season=19/)
    expect(Math.abs(await page.evaluate(() => window.scrollY) - standing)).toBeLessThanOrEqual(1)
  })
})
