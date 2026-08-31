import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

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
  {
    game: "CSGO", name: "CS:GO", slug: "counter-strike-global-offensive", accent: "#e8842a",
    banner: banner("csgo"), icon: icon("csgo"), intro: null, sortIndex: 3, current: false,
  },
]

const competitiveIcons = (page: import("@playwright/test").Page) =>
  page.getByTestId("games-we-play-tile").locator('img[src*="/files/public/game-icons/"]')

// The tiles sit inside `v-lazy`: nothing below the fold mounts an `img` until it intersects.
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
    await page.getByTestId("games-we-play-tile")
      .filter({has: page.locator('img[src*="/files/public/game-icons/"]')}).first().click()

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
    expect(await first.getAttribute("sizes")).toBeTruthy()
  })

  test("a picture the records named decodes, rather than merely having a src", async ({page}) => {
    await installApiMocks(page, {esportsGames: games})

    await page.goto("/")
    await openGamesBlock(page)

    const decoded = await competitiveIcons(page).first()
      .evaluate(async (el) => {
        const img = el as HTMLImageElement
        await img.decode()
        return img.naturalWidth
      })
    expect(decoded).toBeGreaterThan(0)
  })

  test("a game with no art draws a tile rather than a broken picture", async ({page}) => {
    await installApiMocks(page, {
      esportsGames: [{...games[0]!, banner: null, icon: null}],
    })

    await page.goto("/")
    await openGamesBlock(page)

    await expect(competitiveIcons(page)).toHaveCount(0)
    await expect(page.getByText("Competitive", {exact: true})).toBeVisible()
  })

  test("the community games keep their own names and art", async ({page}) => {
    await installApiMocks(page, {esportsGames: games})

    await page.goto("/")
    await openGamesBlock(page)

    await expect(page.getByText("Community", {exact: true})).toBeVisible()
    const community = page.getByTestId("games-we-play-tile")
      .filter({hasNot: page.locator('img[src*="/files/public/"]')})
    await expect(community).toHaveCount(9)
  })
})
