import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

test.describe("the home page's slice bands", () => {
  test("runs the newest season's teams, named with their game and season, each leading to that season", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/")

    const band = page.getByTestId("home-esports")
    await band.scrollIntoViewIfNeeded()
    await expect(band.getByTestId("home-esports-VALORANT-1")).toContainText("BS Waterboarders")
    await expect(band.getByTestId("home-esports-VALORANT-1")).toContainText("Valorant · Autumn 2025")
    await expect(band.getByTestId("home-esports-VALORANT-2")).toContainText("BS SpicyWater")
    await expect(band.getByTestId("home-esports-link-VALORANT-1")).toHaveAttribute("href", "/competition/valorant?season=20")
    await expect(band.getByTestId("home-esports-more")).toHaveAttribute("href", "/competition")
  })

  test("runs the games that are played on the reel, each leading to its own page", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/")

    const band = page.getByTestId("home-casual")
    await band.scrollIntoViewIfNeeded()
    // The archived games wait on the casual page; the reel carries only what is played.
    await expect(band.locator('[data-testid^="home-casual-rail-"]')).toHaveCount(11)
    await expect(band.getByTestId("home-casual-slice-DOTA_2")).toHaveCount(0)
    await expect(band.getByTestId("home-casual-slice-MINECRAFT")).toHaveAttribute("href", "/casual/minecraft")
    await expect(band.getByTestId("home-casual-more")).toHaveAttribute("href", "/casual")
  })

  test("brings a game to the middle from the rail, then follows it", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/")

    const band = page.getByTestId("home-casual")
    await band.scrollIntoViewIfNeeded()
    await band.getByTestId("home-casual-rail-CHESS").click()
    await expect(band.getByTestId("home-casual-slice-CHESS")).toHaveAttribute("aria-current", "true")
    await band.getByTestId("home-casual-slice-CHESS").click()

    await expect(page).toHaveURL(/\/casual\/chess$/)
  })

  // The mocked games carry no art, which is the case this is about.
  test("draws no picture for a game with no art, rather than a broken one", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/")

    const slice = page.getByTestId("home-esports-VALORANT-1")
    await slice.scrollIntoViewIfNeeded()
    await expect(slice.locator("img")).toHaveCount(0)
    await expect(slice).toContainText("BS Waterboarders")
  })
})
