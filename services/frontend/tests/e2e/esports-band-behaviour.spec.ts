import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

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

    await expect(page).toHaveURL(/\/esports\/valorant$/)
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
    await expect(page.getByTestId("team-roster-drop-1")).toBeHidden()
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
})
