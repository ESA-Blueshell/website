import {expect, test} from "./test"
import {dragBand} from "./bandSwipe"
import {installApiMocks, loginAsBoard} from "./mocks"

test.describe("esports mobile layout", () => {
  test("opens the first team on arrival, and another on a tap", async ({page}) => {
    await installApiMocks(page)
    await page.setViewportSize({width: 390, height: 844})

    await page.goto("/esports/valorant")

    await expect(page).toHaveURL(/\/esports\/valorant$/)
    const first = page.getByTestId("team-roster-1")
    const second = page.getByTestId("team-roster-2")
    await expect(first).toContainText("BS Waterboarders")

    // The first slice opens itself, so the page never lands with everything shut.
    await expect(first.getByRole("button")).toHaveAttribute("aria-expanded", "true")
    await expect(first).toContainText("AriosFury")
    await expect(second.getByRole("button")).toHaveAttribute("aria-expanded", "false")

    // A touch screen has no hover to give, so a tap opens a slice instead.
    await second.getByRole("button").click()

    await expect(second.getByRole("button")).toHaveAttribute("aria-expanded", "true")
    await expect(first.getByRole("button")).toHaveAttribute("aria-expanded", "false")
  })

  test("keeps the seasons on the line, and scrolls to the shown one", async ({page}) => {
    await installApiMocks(page)
    await page.setViewportSize({width: 390, height: 844})

    await page.goto("/esports/valorant")
    const strip = page.getByTestId("esports-season-timeline")
    await strip.waitFor()

    // The strip scrolls rather than shrinking, so a band keeps the width its labels need and
    // the highlighted one says which season it is without a caption underneath.
    await expect(strip.locator(".stop__label--lead").first()).toBeVisible()
    await expect(page.getByTestId("esports-season-caption")).toHaveCount(0)

    const selected = page.locator(".stop--on")
    await expect(selected).toHaveCount(1)
    await expect(selected).toBeInViewport()
  })
})

/**
 * Travelling between seasons with a finger.
 *
 * Observable in the ordinary phone project, and that is the point of the reduced-motion decision
 * rather than an accident of it: the band follows the finger whatever the visitor has asked for,
 * because content under direct manipulation is not the unbidden movement the preference is about.
 * What the preference clamps is the ease onto the arrived season and the spring home, which is
 * choreography, and which the motion spec beside this one watches instead.
 *
 * Unlike the board page, a season is read one season at a time: the season being dragged in does
 * not exist until the gesture asks for it, which is why the last test here counts requests.
 */
test.describe("travelling between seasons with a finger", () => {
  // The gesture binds where the pointer is coarse, so on the desktop project there is nothing
  // here to observe.
  test.skip(({isMobile}) => !isMobile, "the gesture binds only where the pointer is coarse")

  const INDEX = "/esports/competitive-scene?season=20"

  test("carries the index back to the season before this one, and the back button returns", async ({page}) => {
    await installApiMocks(page)
    await page.goto(INDEX)
    await page.getByTestId("esports-game-slices").waitFor()

    // Oldest sits left on the strip, so pulling the page rightwards walks back down the seasons.
    await dragBand(page, page.getByTestId("season-swipe"), {by: 260})

    await expect(page).toHaveURL(/\?season=19$/)
    // The season's own games, read for it: Valorant fielded one team in it rather than two.
    await expect(page.locator('a[href="/esports/valorant?season=19"]')).toHaveCount(1)

    // A swipe is a navigation like any other: it has left a history entry behind it.
    await page.goBack()
    await expect(page).toHaveURL(/\?season=20$/)
    await expect(page.locator('a[href="/esports/valorant?season=20"]')).toHaveCount(1)
  })

  test("leaves the season alone for a drag that was neither far enough nor fast enough", async ({page}) => {
    await installApiMocks(page)
    await page.goto(INDEX)
    await page.getByTestId("esports-game-slices").waitFor()

    await dragBand(page, page.getByTestId("season-swipe"), {by: 48})

    await expect(page).toHaveURL(/\?season=20$/)
    await expect(page.locator('a[href="/esports/valorant?season=20"]')).toHaveCount(1)
  })

  test("goes nowhere at either end of the strip, however far the finger hauls", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene?season=19")
    await page.getByTestId("esports-game-slices").waitFor()

    // The oldest season recorded, hauled further back still.
    await dragBand(page, page.getByTestId("season-swipe"), {by: 300})
    await expect(page).toHaveURL(/\?season=19$/)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-slices").waitFor()
    await dragBand(page, page.getByTestId("season-swipe"), {by: -300})
    await expect(page).toHaveURL(/\?season=20$/)
  })

  test("keeps the game being read open across a season change", async ({page}) => {
    await installApiMocks(page)
    await page.goto(INDEX)
    await page.getByTestId("esports-game-slices").waitFor()

    // Scrolled to the middle of the screen rather than tapped: stacked, the scroll is what
    // decides which slice is open, and a tap on a slice that is already open follows it to the
    // game's own page instead. The middle, because only the middle band of the screen counts.
    const counterStrike = page.getByTestId("esports-game-CS2")
    await counterStrike.evaluate(slice => slice.scrollIntoView({block: "center"}))
    await expect(counterStrike.getByRole("button").first()).toHaveAttribute("aria-expanded", "true")

    await dragBand(page, page.getByTestId("season-swipe"), {by: 260})
    await expect(page).toHaveURL(/\?season=19$/)

    // The gesture costs the visitor their place no more than a tap does: the game they were
    // reading is the game open on the season that arrived.
    await expect(page.getByTestId("esports-game-CS2").getByRole("button").first())
      .toHaveAttribute("aria-expanded", "true")
  })

  test("asks for nothing until a finger moves, and never twice for the same season", async ({page}) => {
    await installApiMocks(page)

    const asked: string[] = []
    page.on("request", (request) => {
      const match = /\/esports\/seasons\/(\d+)\/games/.exec(request.url())
      if (match?.[1]) asked.push(match[1])
    })

    await page.goto(INDEX)
    await page.getByTestId("esports-game-slices").waitFor()

    // A visitor who never touches the screen pays for nothing: only the season being read has
    // been asked about, which is what an idle prefetch of every neighbour would not honour.
    expect(asked).toEqual(["20"])

    await dragBand(page, page.getByTestId("season-swipe"), {by: 260})
    await expect(page).toHaveURL(/\?season=19$/)

    // Asked for once, when the gesture began, and not again when the page arrived on it.
    expect(asked).toEqual(["20", "19"])

    await dragBand(page, page.getByTestId("season-swipe"), {by: -260})
    await expect(page).toHaveURL(/\?season=20$/)

    // And a season already read is free to return to.
    expect(asked).toEqual(["20", "19"])
  })
})

/**
 * The same gesture on one game's page, which is the other half of the claim that a strip looking
 * identical on two pages behaves identically on both.
 */
test.describe("travelling between seasons on a game's page", () => {
  test.skip(({isMobile}) => !isMobile, "the gesture binds only where the pointer is coarse")

  test("carries the page to the season before this one", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/valorant?season=20")
    await expect(page.getByTestId("team-roster-1")).toContainText("BS Waterboarders")

    await dragBand(page, page.getByTestId("season-swipe"), {by: 260})

    await expect(page).toHaveURL(/\?season=19$/)
    await expect(page.getByTestId("team-roster-3")).toContainText("BS Tempra")
  })

  test("reaches the season a game did play from one it sat out", async ({page}) => {
    await installApiMocks(page)
    // CS:GO played the older of the two seasons and nothing since, so this page opens on the
    // association's newest season with nothing to show. The strip carries the season being read
    // whether the game played it or not, and the gesture offers exactly what the strip offers.
    await page.goto("/esports/counter-strike-global-offensive")
    await expect(page.getByTestId("esports-empty")).toBeVisible()

    await dragBand(page, page.getByTestId("season-swipe"), {by: 260})

    await expect(page).toHaveURL(/\?season=19$/)
    await expect(page.getByTestId("team-roster-3")).toContainText("BS Tempra")
  })

  test("arrives at a season the game was not fielded in with that season's own answer", async ({page, context}) => {
    await installApiMocks(page)
    // A board member's strip carries every season, because a season has to be reachable before
    // a team can be put in it — so a season this game sat out is somewhere a finger can go.
    await loginAsBoard(context)
    await page.goto("/esports/counter-strike-global-offensive?season=19")
    await expect(page.getByTestId("team-roster-3")).toContainText("BS Tempra")

    await dragBand(page, page.getByTestId("season-swipe"), {by: -260})

    // The season arrives as an answer of its own rather than by the band vanishing where it
    // stood: there is a band, it says the game played nobody, and a team can be added to it.
    await expect(page).toHaveURL(/\?season=20$/)
    await expect(page.getByTestId("team-roster-3")).toHaveCount(0)
    await expect(page.getByTestId("team-roster-slices")).toContainText("No teams played Autumn 2025 yet")
  })

  test("asks the api again for a season it could not reach the first time", async ({page}) => {
    await installApiMocks(page)
    let asked = 0
    await page.route("**/esports/games/VALORANT*", async (route) => {
      if (new URL(route.request().url()).searchParams.get("seasonId") !== "19") return route.fallback()
      asked += 1
      // The api answers about a season other than the one it was asked about, which is what a
      // read this page cannot make looks like: the sdk hands a refusal down as a body rather
      // than throwing, so what the page has is not an error but the wrong season.
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          game: "VALORANT",
          season: {id: 20, name: "Autumn 2025", startDate: "2025-09-01", endDate: "2026-01-31"},
          seasons: [
            {id: 20, name: "Autumn 2025", startDate: "2025-09-01", endDate: "2026-01-31"},
            {id: 19, name: "Spring 2025", startDate: "2025-02-01", endDate: "2025-08-31"},
          ],
          teams: [{id: 1, name: "BS Waterboarders", banner: null, icon: null, members: []}],
        }),
      })
    })
    await page.goto("/esports/valorant?season=20")
    await expect(page.getByTestId("team-roster-1")).toContainText("BS Waterboarders")

    const band = page.getByTestId("season-swipe")
    // The neighbour the gesture draws beside the season being read, which is on the page for the
    // length of a gesture and is waited on before a roster is: while it is there so are two
    // panels, and the one on its way home is a picture of a season rather than a season.
    const aside = page.locator('[data-testid="season-swipe"] > .band-swipe__aside')

    await dragBand(page, band, {by: 260})
    // The season did not arrive, so the band comes home to the one still being read.
    await expect(aside).toHaveCount(0)
    await expect(page.getByTestId("team-roster-1")).toContainText("BS Waterboarders")

    const first = asked

    // A second journey to the same season asks the api again, which is the whole of it: a season
    // the page holds nothing for is a season to read, whatever it answered last time. Held, one
    // refusal would be the last word on that season for the life of the page and every later
    // gesture towards it would be handed back without anything being asked at all.
    await dragBand(page, band, {by: 260})
    await expect.poll(() => asked).toBeGreaterThan(first)

    // Refused again, since the api has not changed its mind, and handed back again: the band is
    // never left holding a season that is not coming.
    await expect(aside).toHaveCount(0)
    await expect(page.getByTestId("team-roster-1")).toContainText("BS Waterboarders")
  })

  test("keeps hitting a node on the strip working exactly as it did", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/valorant?season=20")
    await expect(page.getByTestId("team-roster-1")).toContainText("BS Waterboarders")

    // The gesture has added a way rather than replaced one.
    await page.getByTestId("esports-season-node-19").click()

    await expect(page).toHaveURL(/\?season=19$/)
    await expect(page.getByTestId("team-roster-3")).toContainText("BS Tempra")
  })
})
