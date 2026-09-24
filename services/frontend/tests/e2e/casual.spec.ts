import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

test.describe("the casual pages", () => {
  test("the index runs the played games on the reel, the archived ones past it, and every game below", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/casual")

    await expect(page.getByTestId("casual-island")).toBeVisible()
    await expect(page.getByTestId("casual-reel").locator('[data-testid^="casual-rail-"]')).toHaveCount(5)
    await expect(page.getByTestId("casual-olden")).toContainText("The games we used to play")
    await expect(page.getByTestId("casual-olden-tile-DOTA_2")).toHaveAttribute("href", "/casual/dota-2")
    await expect(page.getByTestId("casual-every-cell-OVERWATCH")).toContainText("Archived")
    await expect(page.getByTestId("casual-every-cell-CHESS")).toHaveAttribute("href", "/casual/chess")
  })

  test("a game in competition leads there, and one that is not says so", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/casual")
    await page.getByTestId("casual-every-cell-VALORANT").click()

    await expect(page).toHaveURL(/\/casual\/valorant$/)
    await expect(page.getByTestId("casual-game-head")).toContainText("Valorant")
    await expect(page.getByTestId("casual-game-competition")).toHaveAttribute("href", "/competition/valorant")

    await page.goto("/casual/minecraft")
    await expect(page.getByTestId("casual-game-not-competitive")).toHaveText("We don't currently play this game competitively")
  })

  test("an archived game keeps its page, marked", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/casual/overwatch")

    await expect(page.getByTestId("casual-game-archived")).toHaveText("Archived")
  })

  test("an address no game answers to is not found", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/casual/tiddlywinks")

    await expect(page.getByText(/Uh oh, we made a fucky wucky!/i)).toBeVisible()
  })
})
