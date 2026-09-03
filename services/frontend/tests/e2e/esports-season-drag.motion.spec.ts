import {devices, type Route} from "@playwright/test"
import {expect, test} from "./test"
import {dragBand, standing} from "./bandSwipe"
import {everySeasonFixtures} from "./esportsStrip"
import {installApiMocks} from "./mocks"
import {recordScrolls, scrolledIn, scrollsAsked} from "./stripScrolls"

/**
 * The choreography of a drag on the esports pages: the neighbouring season's real teams arriving
 * beside the one being read, the band holding where they are slow, springing home where they are
 * not coming at all, and the strip travelling alongside.
 *
 * The board page proved the gesture itself — the commit rule, the resistance at the ends, the
 * handover — with every stop already in memory. What is watched here is the one thing that page
 * could not show: a stop that has to be fetched before it can be drawn.
 *
 * Runs only in the motion project, which is the one project that does not emulate reduced motion
 * — and that project runs a desktop device, so this file overrides its own context with a
 * phone's. Those are per-file context options, so the desktop motion specs beside it are
 * untouched and no fifth project is added. Stated for the file rather than inside the describe
 * below because the browser to launch is a worker's business, and a describe that changed it
 * would force a worker of its own.
 */

test.use({...devices["Pixel 7"]})

const SWIPE = "[data-testid=\"season-swipe\"]"
const CARRIED = `${SWIPE} .band-swipe__carried`
const ASIDE = `${SWIPE} > .band-swipe__aside`
const STRIP = "[data-testid=\"esports-season-timeline\"] .timeline__scroll"

/**
 * An answer the test decides the arrival of, rather than one a clock decides.
 *
 * "The answer has not arrived yet" is a state, and a `setTimeout` in the route is a guess that
 * the test will reach its assertions before the clock runs out. On CI, where eight workers share
 * four cores, it does not: the answer landed first, the band correctly stopped holding, and the
 * test reported a page that was working as a page that was broken. So the read is held open
 * until the test says otherwise, and the state is then a fact for as long as it is needed.
 */
const heldOpen = () => {
  let release: () => void = () => undefined
  const opened = new Promise<void>(resolve => { release = resolve })
  let landed = false
  return {
    /** Await this in a route handler: it answers only once the test lets it. */
    wait: async () => { await opened; landed = true },
    /** Let the answer through. */
    release: () => release(),
    /** Whether the read has actually answered. */
    get landed() { return landed },
  }
}

/** One game's page for one season, which is the read a season change on that page makes. */
const seasonRead = (page: import("@playwright/test").Page, seasonId: string, answer: (route: Route) => Promise<unknown>) =>
  page.route("**/esports/games/VALORANT*", async (route) => {
    if (new URL(route.request().url()).searchParams.get("seasonId") !== seasonId) return route.fallback()
    return answer(route)
  })

test.describe("dragging a game's page between seasons", () => {
  test("draws the neighbouring season's own teams beside the one being read", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/valorant?season=20")
    await expect(page.getByTestId("team-roster-1")).toContainText("BS Waterboarders")

    const band = page.getByTestId("season-swipe")
    const width = (await band.boundingBox())!.width
    await dragBand(page, band, {by: 60, release: false})

    // The band stands where the finger left it, to within a pixel of rounding, because that is
    // the whole of what direct manipulation means.
    const [carried] = await standing(page, CARRIED)
    expect(carried).toBeGreaterThan(50)
    expect(carried).toBeLessThan(70)

    // And the season it is heading for stands a width to the left of it, drawing the real thing
    // rather than a box that turns into it — which is only possible because the season was asked
    // for when the gesture began rather than when it committed. The finger is still down: this
    // drag has gone a sixth of the way to the quarter-screen that would commit it.
    await expect(page.locator(ASIDE)).toContainText("BS Tempra")
    const [aside] = await standing(page, ASIDE)
    expect(aside).toBeCloseTo(carried! - width, 0)

    await page.mouse.up()
  })

  test("holds the season it brought in where the answer is slow, and lands once it arrives", async ({page}) => {
    await installApiMocks(page)
    // Slow enough that the gesture's own ease is long over before the answer lands, which is the
    // case the board page could not produce: every board was in hand before the first gesture.
    const answer = heldOpen()
    await seasonRead(page, "19", async (route) => {
      await answer.wait()
      return route.fallback()
    })
    await page.goto("/esports/valorant?season=20")
    await expect(page.getByTestId("team-roster-1")).toContainText("BS Waterboarders")

    const band = page.getByTestId("season-swipe")
    const width = (await band.boundingBox())!.width
    await dragBand(page, band, {by: 260})

    // The gesture finished its own pass and the page was asked, so the url already names the
    // season. What has not happened is the arrival.
    await expect(page).toHaveURL(/\?season=19$/)

    // And the band holds. Waited for rather than assumed: the url changes when the page is
    // asked, and the track is established a frame or two either side of that, so sampling the
    // instant the url lands races the thing being measured. An earlier version read the panel's
    // offset with `?? 0`, which turned "no panel yet" into "a panel sitting at nought" and
    // reported a race as a failure to hold — the two are the one distinction this test exists
    // to make, so the wait is bounded and the absence is loud.
    await expect(page.locator(ASIDE), "the season brought in was never held").toHaveCount(1)
    await expect.poll(
      async () => (await standing(page, CARRIED)).length,
      {message: "the season it came from left the track"},
    ).toBe(1)

    // Now it is held, nothing may move while the answer is awaited. What is asserted is that
    // successive samples agree, which is that claim, rather than that one sample equals a
    // figure: the width comes from a bounding box and the offset from an animation that has
    // just settled, so the two agree to about a pixel rather than to a fraction of one.
    const held: number[] = []
    for (let sample = 0; sample < 6; sample += 1) {
      const [aside] = await standing(page, ASIDE)
      const [carried] = await standing(page, CARRIED)
      // `aside` is asserted to exist before it is measured. An absent panel used to come back as
      // `-1` and pass a `< 2` check, which is the very defect the wait above was written to
      // remove — removed there for the panel travelling and missed here for the one arriving.
      expect(aside, "the season brought in left the track mid-hold").toBeDefined()
      expect(Math.abs(aside ?? -1), `the season brought in stood at ${aside}`).toBeLessThan(2)
      expect(
        Math.abs((carried ?? 0) - width),
        `the season it came from stood at ${carried}, a width being ${width}`,
      ).toBeLessThan(2)
      held.push(carried ?? 0)
      await page.waitForTimeout(50)
    }
    expect(Math.max(...held) - Math.min(...held), `the band drifted across ${held}`).toBeLessThan(1)

    // Then the answer lands, and the track is put away under contents that are already there.
    answer.release()
    await expect(page.getByTestId("team-roster-3")).toContainText("BS Tempra")
    await expect.poll(async () => Math.round((await standing(page, CARRIED))[0] ?? -1)).toBe(0)
    await expect(page.locator(ASIDE)).toHaveCount(0)
  })

  test("springs the band home where the page never arrives on the season asked for", async ({page}) => {
    await installApiMocks(page)
    // The api answers about a season other than the one asked about, which is the shape of every
    // way this page can fail to answer a gesture: a read the api refused comes back as a body
    // rather than as an exception, so what a page in that position has is not an error but a
    // season that is not the one it went looking for. The band is holding a season the page will
    // now never arrive on, and without a way out it would hold it for the life of the page.
    await seasonRead(page, "19", route => route.fulfill({
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
    }))
    await page.goto("/esports/valorant?season=20")
    await expect(page.getByTestId("team-roster-1")).toContainText("BS Waterboarders")

    const band = page.getByTestId("season-swipe")
    await dragBand(page, band, {by: 260})

    // So the page says the season is not coming, and the journey is handed back: the band comes
    // home to the season the visitor is in fact still reading.
    await expect.poll(async () => Math.round((await standing(page, CARRIED))[0] ?? -1)).toBe(0)
    await expect(page.locator(ASIDE)).toHaveCount(0)
    await expect(page.getByTestId("team-roster-1")).toContainText("BS Waterboarders")

    // Which is the whole point of saying it: the band takes a further grab rather than refusing
    // every gesture for the rest of the page's life.
    await dragBand(page, band, {by: 60, release: false})
    await expect(page.locator(ASIDE)).toHaveCount(1)
    await page.mouse.up()
  })

  /**
   * A second gesture while the first is still waiting on its season.
   *
   * Invisible under an api that answers at once, which is why the read here is slowed down: with
   * both seasons in hand the first journey is over before a thumb could start another, and the
   * band would take the second gesture whatever it did with the first. What is proved is that a
   * finger arriving on a band that is holding a season supersedes it rather than being swallowed
   * by it — and that the season the superseded gesture asked for does not then land on top of the
   * one the visitor actually asked for last.
   */
  test("takes a second gesture while the first is still waiting, and lands on the season asked for last", async ({page}) => {
    await installApiMocks(page, everySeasonFixtures)
    // Held open rather than slowed by a clock, for the reason `heldOpen` exists: the state being
    // described is the season asked for and the answer still coming, and how long the drag and
    // its ease take is the runner's business rather than a figure this test can pick.
    const answer = heldOpen()
    await seasonRead(page, "63", async (route) => {
      await answer.wait()
      return route.fallback()
    })
    await page.goto("/esports/valorant?season=64")
    await expect(page.getByTestId("team-roster-74")).toBeAttached()

    const band = page.getByTestId("season-swipe")
    // Back down the line, which commits: the url names the older season and the band is holding
    // it on screen, because the answer for it is still a second away.
    await dragBand(page, band, {by: 260})
    await expect(page).toHaveURL(/\?season=63$/)
    expect(answer.landed).toBe(false)

    // And now the other way, on the band that is mid-hold. The visitor has changed their mind, so
    // the hold is given up and this gesture steps from the season **on screen** — the one the
    // first gesture brought in and is holding — rather than from the season still drawn behind
    // it. Forward from Autumn 2023 is therefore Autumn 2024, the season they came from and can
    // see the edge of. Measured from the drawn season it landed on Autumn 2025, stepping clean
    // over the season in front of the reader.
    await dragBand(page, band, {by: -260})
    await expect(page).toHaveURL(/\?season=64$/)
    await expect(page.getByTestId("team-roster-74")).toBeAttached()
    await expect.poll(async () => Math.round((await standing(page, CARRIED))[0] ?? -1)).toBe(0)
    await expect(page.locator(ASIDE)).toHaveCount(0)

    // Then the season the first gesture asked for finally answers, and lands nowhere: it is not
    // the season being waited on any more, and a read that arrives late may not put the page
    // somewhere the visitor has already left.
    answer.release()
    await expect.poll(() => answer.landed).toBe(true)
    await expect(page).toHaveURL(/\?season=64$/)
    await expect(page.getByTestId("team-roster-74")).toBeAttached()
    await expect(page.getByTestId("team-roster-73")).toHaveCount(0)
  })

  test("travels the line to a season a finger arrived at, and jumps to one a node was hit for", async ({page}) => {
    await recordScrolls(page)
    await installApiMocks(page, everySeasonFixtures)
    // A season in the middle of eight, because a line only a screenful and a half long cannot
    // centre the stops at its ends and a scroll clamped at nought would prove nothing either way.
    await page.goto("/esports/valorant?season=64")
    await page.getByTestId("esports-season-node-64").waitFor()
    const opened = await scrolledIn(page, STRIP)

    await dragBand(page, page.getByTestId("season-swipe"), {by: 260})
    await expect(page).toHaveURL(/\?season=63$/)

    // The line travels alongside the band rather than snapping ahead of it, and ends with the
    // arrived season's node in the middle of the window.
    await expect.poll(async () => (await scrollsAsked(page)).at(-1)).toBe("smooth")
    await expect.poll(async () => Math.round(await scrolledIn(page, STRIP))).not.toBe(Math.round(opened))
    const node = (await page.getByTestId("esports-season-node-63").boundingBox())!
    const box = (await page.locator(STRIP).boundingBox())!
    expect(Math.abs((node.x + node.width / 2) - (box.x + box.width / 2))).toBeLessThan(24)

    // A hit on one of the strip's own nodes is what it always was: the line does not move at
    // all, because the node the visitor aimed at would slide out from under their finger.
    const asked = (await scrollsAsked(page)).length
    await page.getByTestId("esports-season-node-65").click()
    await expect(page).toHaveURL(/\?season=65$/)
    await expect(page.getByTestId("team-roster-75")).toBeAttached()
    expect(await scrollsAsked(page)).toHaveLength(asked)
  })
})

test.describe("dragging the esports index between seasons", () => {
  /**
   * What a visitor who asked for reduced motion gets, which is the gesture without its tails.
   *
   * The preference is emulated for this test rather than taken from the project, because
   * `use.reducedMotion` does not reach the page on Playwright 1.60 — #852 — so every
   * "deterministic" project in this suite is in fact running with full motion. The same line and
   * the same reasoning are in `boards-swipe.motion.spec.ts` and `esports-season-on-show.spec.ts`;
   * when #852 is fixed, all of them go.
   *
   * The durations are read off the animations themselves rather than timed with a clock. Eight
   * workers share four vCPUs here, so a wall-clock measurement of "it settled quickly" is a
   * measurement of the runner's mood; what the preference actually changes is the duration the
   * band asks for, and that is a number the page will state.
   */
  /**
   * The same season asked for twice over, the second time before the first has answered.
   *
   * Which is the case a page that read one season at a time used to get wrong in the other
   * direction: asked about the season it had already *chosen*, it declined to read anything,
   * answered the gesture instantly with nothing, and the band was told the season was not coming
   * while it was in fact on its way. Slowed down here because that is the only state it happens
   * in — the season chosen and not yet arrived.
   */
  test("takes a second gesture to the season the first one is still waiting for", async ({page}) => {
    await installApiMocks(page)
    // Held open rather than slowed by a clock: both drags and both of their eases happen while
    // this one read is in flight, and how long that takes is the runner's business, not a figure
    // this test can pick.
    const answer = heldOpen()
    await page.route("**/esports/seasons/19/games", async (route) => {
      await answer.wait()
      return route.fallback()
    })
    await page.goto("/esports/competitive-scene?season=20")
    await page.getByTestId("esports-game-slices").waitFor()

    const band = page.getByTestId("season-swipe")
    const width = (await band.boundingBox())!.width
    await dragBand(page, band, {by: 260})
    await expect(page).toHaveURL(/\?season=19$/)
    expect(answer.landed).toBe(false)

    // The same journey again, on a band that is holding the season the first one asked for. What
    // the second gesture must not be told is that the season is not coming.
    await dragBand(page, band, {by: 260})
    // Its own ease onto the neighbour first, which is choreography rather than the claim.
    await expect.poll(async () => Math.round((await standing(page, CARRIED))[0] ?? -1))
      .toBe(Math.round(width))
    for (let sample = 0; sample < 6; sample += 1) {
      const [carried] = await standing(page, CARRIED)
      expect(carried, `the band stood at ${carried}`).toBeCloseTo(width, 0)
      await page.waitForTimeout(50)
    }

    // And it lands, once the season answers, on the season it was holding all along.
    answer.release()
    await expect.poll(() => answer.landed).toBe(true)
    await expect(page.locator('a[href="/esports/valorant?season=19"]')).toHaveCount(1)
    await expect.poll(async () => Math.round((await standing(page, CARRIED))[0] ?? -1)).toBe(0)
    await expect(page.locator(ASIDE)).toHaveCount(0)
  })

  test("still follows the finger under reduced motion, and lands without the long ease", async ({page}) => {
    await page.emulateMedia({reducedMotion: "reduce"})
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene?season=20")
    await page.getByTestId("esports-game-slices").waitFor()

    // Held, not released: content moving under a finger is not the unbidden movement the
    // preference is about, so this half of the gesture survives it. A band that refused to
    // follow would leave a visitor who asked for less movement with no gesture at all.
    await dragBand(page, page.getByTestId("season-swipe"), {by: 260, release: false})
    expect(Math.round((await standing(page, CARRIED))[0] ?? 0)).toBeGreaterThan(80)

    await page.mouse.up()

    // The tails are what gets clamped: the island's ceiling is 120ms against the 850ms the
    // gesture would otherwise take, so nothing crosses the window under its own steam. Only the
    // gesture's own animations, which are the two panels it slides and the shell whose height it
    // carries between them: every animation on the page is the wrong question, because the band
    // is full of slices with transitions of their own.
    const asked = await page.evaluate(([swipe, carried, aside]) => {
      const mine = [
        document.querySelector(swipe),
        ...document.querySelectorAll(carried),
        ...document.querySelectorAll(aside),
      ].filter((el): el is Element => el != null)
      return document.getAnimations()
        .filter(one => mine.includes((one.effect as KeyframeEffect | null)?.target as Element))
        .map(one => one.effect?.getTiming().duration)
        .filter((ms): ms is number => typeof ms === "number" && ms > 0)
    }, [SWIPE, CARRIED, ASIDE] as const)
    expect(asked.length).toBeGreaterThan(0)
    for (const ms of asked) expect(ms).toBeLessThanOrEqual(200)

    // The end state is the one a visitor without the preference reaches: same season, band home.
    await expect(page).toHaveURL(/\?season=19$/)
    await expect(page.locator('a[href="/esports/valorant?season=19"]')).toHaveCount(1)
    await expect.poll(async () => Math.round((await standing(page, CARRIED))[0] ?? -1)).toBe(0)
    await expect(page.locator(ASIDE)).toHaveCount(0)
  })
})
