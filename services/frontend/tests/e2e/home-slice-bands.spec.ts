import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

test.describe("the home page's slice bands", () => {
  test("runs the newest season's games with their team counts, each leading to its own page", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/")

    const band = page.getByTestId("home-esports")
    await band.scrollIntoViewIfNeeded()
    await expect(band.getByTestId("home-esports-VALORANT")).toContainText(/\d+ teams? this season/)
    await expect(band.getByTestId("home-esports-link-VALORANT")).toHaveAttribute("href", "/esports/valorant")
    await expect(band.getByTestId("home-esports-more")).toHaveAttribute("href", "/esports")
  })

  test("runs the casual games into the Discord, with nothing after them", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/")

    const band = page.getByTestId("home-casual")
    await band.scrollIntoViewIfNeeded()
    await expect(band.locator('[data-testid^="home-casual-link-"]')).toHaveCount(5)
    await expect(band.getByTestId("home-casual-link-Minecraft"))
      .toHaveAttribute("href", /\/api\/discord\/invite\/welcome$/)
    await expect(band.getByTestId("home-casual-add")).toHaveCount(0)
  })

  // The mocked games carry no art, which is the case this is about.
  test("draws no picture for a game with no art, rather than a broken one", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/")

    const slice = page.getByTestId("home-esports-VALORANT")
    await slice.scrollIntoViewIfNeeded()
    await expect(slice.locator("img")).toHaveCount(0)
    await expect(slice).toContainText("Valorant")
  })
})
