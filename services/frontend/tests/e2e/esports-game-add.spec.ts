import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

const INDEX = "/competition"

/**
 * Putting a game into the shown season. The band's plus leads to the season's own page, where a
 * game the association has played before is entered with one pick, and a game it has just started
 * playing is described in full on the game's edit page and entered by the same save.
 */
test.describe("adding a game", () => {
  test("a visitor is offered no way in", async ({page}) => {
    await installApiMocks(page)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-slices").waitFor()

    await expect(page.getByTestId("esports-game-add")).toHaveCount(0)
  })

  test("the plus leads to the shown season's own page", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await expect(page.getByTestId("esports-game-add")).toContainText("Add a game")
    await page.getByTestId("esports-game-add").click()

    await expect(page).toHaveURL(/\/competition\/seasons\/\d+\/edit$/)
    await expect(page.getByTestId("season-edit-games")).toBeVisible()
  })

  test("a game played before is entered with one pick, is not offered twice, and joins the band", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("season-edit-enter-search").click()

    // Valorant and CS2 already play this season, so there is nothing to enter of them.
    await expect(page.getByTestId("season-edit-enter-ROCKET_LEAGUE")).toBeVisible()
    await expect(page.getByTestId("season-edit-enter-VALORANT")).toHaveCount(0)
    await page.getByTestId("season-edit-enter-ROCKET_LEAGUE").click()
    await expect(page.getByTestId("season-edit-game-ROCKET_LEAGUE")).toContainText("Rocket League")

    await page.getByTestId("season-edit-back").click()
    await expect(page.getByTestId("esports-game-ROCKET_LEAGUE")).toBeVisible()
    // The board's list of what is left to do, said rather than left to be inferred.
    await expect(page.getByTestId("esports-quiet-ROCKET_LEAGUE")).toContainText("visitors do not see it")
    await expect(page.getByTestId("esports-link-ROCKET_LEAGUE"))
      .toHaveAttribute("href", /\/competition\/rocketleague\?season=\d+/)

    // The same season, read by somebody who may not edit. The api decides this, not the page.
    await context.clearCookies()
    await page.goto(INDEX)
    await page.getByTestId("esports-game-slices").waitFor()
    await expect(page.getByTestId("esports-game-ROCKET_LEAGUE")).toHaveCount(0)
  })

  test("a game taken back out of the season leaves it", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("season-edit-enter-search").click()
    await page.getByTestId("season-edit-enter-ROCKET_LEAGUE").click()
    await page.getByTestId("season-edit-take-out-ROCKET_LEAGUE").click()

    await expect(page.getByTestId("season-edit-game-ROCKET_LEAGUE")).toHaveCount(0)
    await page.getByTestId("season-edit-back").click()
    await page.getByTestId("esports-game-slices").waitFor()
    await expect(page.getByTestId("esports-game-ROCKET_LEAGUE")).toHaveCount(0)
  })

  test("a game fielding a team is not taken out, and the page says why", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("season-edit-take-out-VALORANT").click()

    await expect(page.getByTestId("season-edit-games-failure")).toContainText("Valorant still has")
    await expect(page.getByTestId("season-edit-game-VALORANT")).toBeVisible()
  })

  test("a new game is described in full on its own page, previewed as the competition draws it", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("season-edit-new-game").click()

    await expect(page).toHaveURL(/\/competition\/new\?season=\d+$/)
    await expect(page.getByTestId("game-edit-archive")).toHaveCount(0)
    await page.getByTestId("game-edit-name").fill("Age Of Empires II")
    await expect(page.getByTestId("game-edit-slug")).toHaveValue("age-of-empires-ii")
    await expect(page.getByTestId("game-edit-preview")).toContainText("Age Of Empires II")
  })

  test("a new game joins the season it was added from, reading on the island's own colour", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("season-edit-new-game").click()
    await page.getByTestId("game-edit-name").fill("Pong")
    await page.getByTestId("game-edit-save").click()

    // Back on the season it was added from, where it is now entered.
    await expect(page.getByTestId("season-edit-game-PONG")).toContainText("Pong")
    await page.getByTestId("season-edit-back").click()
    const slice = page.getByTestId("esports-game-PONG")
    await expect(slice).toContainText("Pong")
    await expect(slice).not.toHaveAttribute("style", /--slice-accent:\s*#/)
  })

  test("an address another game claims is refused, and what was typed stays", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(`${INDEX}/new?season=20`)
    await page.getByTestId("game-edit-name").fill("Valorant Two")
    await page.getByTestId("game-edit-slug").fill("valorant")
    await page.getByTestId("game-edit-save").click()

    // Losing the whole form to find out what the objection was would mean typing it again.
    await expect(page.getByTestId("game-edit-failure"))
      .toContainText("The address 'valorant' is already used by Valorant.")
    await expect(page.getByTestId("game-edit-name")).toHaveValue("Valorant Two")
    await expect(page.getByTestId("game-edit-slug")).toHaveValue("valorant")
  })

  test("a season nothing ran in still offers the way to put a game into it", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    // The empty season is the one nothing was fielded in, which is exactly where a board
    // needs to start. Saying so and offering nothing to do about it is a dead end.
    await page.goto(`${INDEX}?season=41`)

    // Said in the band, in a slice, with the way in beside it rather than under it.
    await expect(page.getByTestId("esports-game-empty-slice")).toContainText("No games ran")
    await expect(page.getByTestId("esports-game-add")).toBeVisible()
  })

  test("a visitor reading an empty season is offered no way in", async ({page}) => {
    await installApiMocks(page)

    await page.goto(`${INDEX}?season=41`)
    await expect(page.getByTestId("esports-index-empty")).toBeVisible()

    await expect(page.getByTestId("esports-game-add")).toHaveCount(0)
  })
})
