import type {Locator, Page} from "@playwright/test"
import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

/**
 * A row of slices on a screen just wide enough to be a row, where a shut slice is a sliver.
 *
 * Runs only in the motion project: under reduced motion a slice is open in the frame it is
 * reached, so the frames in which it is open but still shut-width never exist.
 */
test.use({viewport: {width: 800, height: 900}})

const icon = (code: string) => ({
  path: `game-icons/${code}.svg`,
  url: `/files/public/game-icons/${code}.svg`,
  width: null,
  height: null,
  renditions: [],
})

/**
 * Six games, so a shut slice at this width is a sliver, with names long enough to break in one.
 * All but one has an icon to stand for its name.
 */
const games = [
  {code: "VALORANT", name: "Valorant", icon: icon("valorant")},
  {code: "CS2", name: "Counter-Strike 2", icon: icon("cs2")},
  {code: "LEAGUE_OF_LEGENDS", name: "League of Legends", icon: icon("lol")},
  {code: "ROCKET_LEAGUE", name: "Rocket League", icon: null},
  {code: "OVERWATCH", name: "Overwatch", icon: icon("overwatch")},
  {code: "TRACKMANIA", name: "Trackmania", icon: icon("trackmania")},
].map((game, index) => ({
  ...game,
  slug: game.code.toLowerCase(),
  accent: null,
  banner: null,
  intro: null,
  sortIndex: index + 1,
  current: true,
}))

/** The season's band, holding every one of [games] and nothing the shared mocks field. */
const band = async (page: Page): Promise<{slices: Locator; each: Locator}> => {
  await installApiMocks(page, {esportsGames: games})
  await page.route("**/esports/seasons/*/games", async (route) => {
    if (route.request().method() !== "GET") return route.fallback()
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(games.map((game, index) => ({
        game: game.code,
        public: true,
        teams: [{id: 500 + index, name: `BS ${game.name}`, members: [{role: "PLAYER", handle: `${game.code}One`}]}],
      }))),
    })
  })
  await page.goto("/competition")
  const slices = page.getByTestId("esports-game-slices")
  const each = slices.locator(":scope > .slice:not(.slice--add)")
  await expect(each).toHaveCount(games.length)
  await expect.poll(async () => each.first().getAttribute("class")).toContain("slice--open")
  await slices.scrollIntoViewIfNeeded()
  return {slices, each}
}

// Height, not width: a squeezed name is no width at all, and a column of letters down the slice.
// No box at all is not a hidden name, it is a name the page never drew.
const nameHeight = async (slice: Locator) => (await slice.locator(".slice__name").boundingBox())?.height ?? Number.NaN

test.describe("a row of narrow slices", () => {
  test("a slice opening does not stretch the band while it widens", async ({page}) => {
    const {slices, each} = await band(page)

    // Every frame, since the stretch lasts two or three of them and a poll steps over it.
    await slices.evaluate((el) => {
      const heights: number[] = []
      Object.assign(window, {bandHeights: heights})
      const sample = () => {
        heights.push(el.getBoundingClientRect().height)
        requestAnimationFrame(sample)
      }
      requestAnimationFrame(sample)
    })
    const settled = (await slices.boundingBox())!.height

    // The pointer itself rather than `hover()`: the neighbours' cut edges overlap a slice's
    // own, and the actionability check reads that as the slice being covered.
    const reached = each.nth(2)
    const box = (await reached.boundingBox())!
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
    await expect.poll(async () => reached.getAttribute("class")).toContain("slice--open")
    await page.waitForTimeout(1000)

    const heights = await page.evaluate(() => (window as unknown as {bandHeights: number[]}).bandHeights)
    expect(Math.max(...heights)).toBeLessThanOrEqual(settled + 1)
  })

  test("a shut slice too narrow for its name shows its icon alone", async ({page}) => {
    const {each} = await band(page)

    const shut = each.nth(1)
    await expect.poll(() => nameHeight(shut)).toBeLessThanOrEqual(1)
    // Still what the toggle is called, for anybody not looking at the icon.
    await expect(shut.getByRole("button", {name: /Counter-Strike 2/})).toBeAttached()
    // The open slice has room for its name, and one with no icon has nothing else to show.
    await expect.poll(() => nameHeight(each.first())).toBeGreaterThan(1)
    await expect.poll(() => nameHeight(each.nth(3))).toBeGreaterThan(1)
  })
})
