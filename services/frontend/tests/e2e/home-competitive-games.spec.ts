import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

/**
 * The home page's competitive block, drawn from the game records.
 *
 * Five titles were literals in `Home.vue` with bundled art and hardcoded links, so a game the
 * board renamed or re-addressed through the dialogs did not follow it here — and Trackmania,
 * fielded in the records, was listed only as a community game. These go through the browser
 * because what is being asserted is what a visitor meets: the tiles that exist, where they lead,
 * and that a picture actually decodes rather than that its attribute matches a string.
 */
const banner = (name: string) => ({
  url: `/files/public/game-banners/${name}.webp`,
  path: `game-banners/${name}.webp`,
  width: 1280,
  height: 720,
  renditions: [
    {url: `/files/public/game-banners/${name}-320.webp`, width: 320},
    {url: `/files/public/game-banners/${name}-640.webp`, width: 640},
  ],
})

const icon = (name: string) => ({
  url: `/files/public/game-icons/${name}.webp`,
  path: `game-icons/${name}.webp`,
  width: 512,
  height: 512,
  renditions: [
    {url: `/files/public/game-icons/${name}-128.webp`, width: 128},
    {url: `/files/public/game-icons/${name}-256.webp`, width: 256},
  ],
})

const games = [
  {
    game: "VALORANT", name: "Valorant", slug: "valorant", accent: "#ff4655",
    banner: banner("valorant"), icon: icon("valorant"), intro: null, sortIndex: 1, current: true,
  },
  {
    game: "TRACKMANIA", name: "Trackmania", slug: "trackmania", accent: null,
    banner: banner("trackmania"), icon: icon("trackmania"), intro: null, sortIndex: 2, current: true,
  },
  // Retired: its history stays readable, and the home page stops saying it is played.
  {
    game: "CSGO", name: "CS:GO", slug: "counter-strike-global-offensive", accent: "#e8842a",
    banner: banner("csgo"), icon: icon("csgo"), intro: null, sortIndex: 3, current: false,
  },
]

/** The competitive tiles, which the block identifies by the picture each carries. */
const competitiveIcons = (page: import("@playwright/test").Page) =>
  page.locator('img[src*="/files/public/game-icons/"]')

/**
 * Scrolls the block into view and waits for it.
 *
 * The tiles sit inside `v-lazy`, so nothing below the fold mounts an `img` at all until it
 * intersects. A test that asserted without scrolling would find no pictures and read that as
 * the records not arriving.
 */
const openGamesBlock = async (page: import("@playwright/test").Page) => {
  const heading = page.getByText("Games we play")
  await heading.waitFor()
  await heading.scrollIntoViewIfNeeded()
  await page.getByText("Competitive", {exact: true}).scrollIntoViewIfNeeded()
}

test.describe("the home page's competitive games", () => {
  test("draws a tile for each game currently played, and none for a retired one", async ({page}) => {
    await installApiMocks(page, {esportsGames: games})

    await page.goto("/")
    await openGamesBlock(page)

    await expect(competitiveIcons(page)).toHaveCount(2)
    await expect(page.locator('img[src*="game-icons/csgo"]')).toHaveCount(0)
  })

  test("a game re-addressed through the dialog is linked at its new address", async ({page}) => {
    await installApiMocks(page, {
      esportsGames: [{...games[0]!, slug: "valorant-two"}],
    })

    await page.goto("/")
    await openGamesBlock(page)
    // The tile carries the click, not the picture inside it.
    await page.getByTestId("games-we-play-tile").filter({has: competitiveIcons(page)}).first().click()

    // No deploy changed this: the link is the record's address.
    await expect(page).toHaveURL(/\/esports\/valorant-two$/)
  })

  test("each picture is offered at the widths its record is stored at", async ({page}) => {
    await installApiMocks(page, {esportsGames: games})

    await page.goto("/")
    await openGamesBlock(page)

    const first = competitiveIcons(page).first()
    const srcset = await first.getAttribute("srcset")
    expect(srcset).toContain("game-icons/valorant-128.webp 128w")
    expect(srcset).toContain("256w")
    // A logo is drawn in a box of a fixed width, so a phone is told that width rather than a
    // share of the viewport.
    expect(await first.getAttribute("sizes")).toBeTruthy()
  })

  test("a game with no art draws a tile rather than a broken picture", async ({page}) => {
    await installApiMocks(page, {
      esportsGames: [{...games[0]!, banner: null, icon: null}],
    })

    await page.goto("/")
    await openGamesBlock(page)

    // No record, no picture — and nothing pointing at a bundled file that may not be there.
    await expect(competitiveIcons(page)).toHaveCount(0)
    await expect(page.getByText("Competitive", {exact: true})).toBeVisible()
  })

  test("the community games keep their own names and art", async ({page}) => {
    await installApiMocks(page, {esportsGames: games})

    await page.goto("/")
    await openGamesBlock(page)

    // Written down in the page rather than read from a record: they are not games the
    // association fields, so nothing about this block changed.
    await expect(page.getByText("Community", {exact: true})).toBeVisible()
    const bundled = page.locator('img[src*="/assets/"], img[src^="/dota2"], img[src*="dota2"]')
    await expect(bundled.first()).toBeVisible()
  })
})
