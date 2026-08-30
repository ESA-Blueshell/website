import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

const INDEX = "/esports/competitive-scene"

/**
 * Putting a game into the season on show, from the band where the games are shown.
 *
 * Two ways in rather than one, because they are two different acts. A game the association has
 * played before already exists and is one click; a game it has just started playing is described
 * in full, and entered in the season by the same save. A game used to arrive by somebody editing
 * a compiled list and deploying.
 */
test.describe("adding a game", () => {
  test("a visitor is offered neither way in", async ({page}) => {
    await installApiMocks(page)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-slices").waitFor()

    await expect(page.getByTestId("esports-game-add-played-before")).toHaveCount(0)
    await expect(page.getByTestId("esports-game-add-new-game")).toHaveCount(0)
  })

  test("the board is offered both, and they say which is which", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)

    await expect(page.getByTestId("esports-game-add-played-before")).toContainText("played before")
    await expect(page.getByTestId("esports-game-add-new-game")).toContainText("started playing")
  })

  test("a game played before is put in with one press, and is not offered twice", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-add-played-before").click()

    // Valorant and CS2 already play this season, so there is nothing to enter of them.
    await expect(page.getByTestId("enter-game-ROCKET_LEAGUE")).toBeVisible()
    await expect(page.getByTestId("enter-game-VALORANT")).toHaveCount(0)

    await page.getByTestId("enter-game-ROCKET_LEAGUE").click()

    await expect(page.getByTestId("enter-game-dialog")).toBeHidden()
    await expect(page.getByTestId("esports-game-ROCKET_LEAGUE")).toBeVisible()
  })

  test("a game entered with nobody in it says it is not public yet", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-add-played-before").click()
    await page.getByTestId("enter-game-ROCKET_LEAGUE").click()

    // The board's list of what is left to do, said rather than left to be inferred from a
    // slice that would otherwise read as a finished one.
    await expect(page.getByTestId("esports-quiet-ROCKET_LEAGUE"))
      .toContainText("visitors do not see it")
  })

  test("a visitor is not shown a game nobody is fielded in", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)
    await page.goto(INDEX)
    await page.getByTestId("esports-game-add-played-before").click()
    await page.getByTestId("enter-game-ROCKET_LEAGUE").click()
    await expect(page.getByTestId("esports-game-ROCKET_LEAGUE")).toBeVisible()

    // The same season, read by somebody who may not edit. The api decides this, not the page.
    await context.clearCookies()
    await page.goto(INDEX)
    await page.getByTestId("esports-game-slices").waitFor()

    await expect(page.getByTestId("esports-game-ROCKET_LEAGUE")).toHaveCount(0)
  })

  test("the board-only slice leads to the game's page, on the season being read", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-add-played-before").click()
    await page.getByTestId("enter-game-ROCKET_LEAGUE").click()

    await page.getByTestId("esports-link-ROCKET_LEAGUE").click()

    // The season goes with them, because what they are going to do is add a team to it.
    await expect(page).toHaveURL(/\/esports\/rocketleague\?season=\d+/)
  })

  test("a game taken back out of the season leaves the band", async ({page, context}, testInfo) => {
    // Side by side, the slice just entered is the one showing its details, and the way out of
    // the season is in them. Stacked, which slice is open is decided by what the reader has
    // scrolled to -- that is the whole of how the band works on a phone -- so the control is
    // not reliably under the pointer there. What it does is not different.
    test.skip(testInfo.project.name === "mobile-chrome", "the band's open slice is scroll-driven on a phone")
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-add-played-before").click()
    await page.getByTestId("enter-game-ROCKET_LEAGUE").click()
    await expect(page.getByTestId("esports-game-ROCKET_LEAGUE")).toBeVisible()
    // The band rearranges around the slice that just arrived, and the way out of the season is
    // inside it. Waiting for what it says means waiting for it to have stopped moving.
    await expect(page.getByTestId("esports-quiet-ROCKET_LEAGUE")).toBeVisible()

    await page.getByTestId("esports-take-out-ROCKET_LEAGUE").click()

    await expect(page.getByTestId("esports-game-ROCKET_LEAGUE")).toHaveCount(0)
  })

  test("a new game is described in full, and its address is shown as it is typed", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-add-new-game").click()

    await expect(page.getByTestId("game-dialog-name")).toBeVisible()
    await expect(page.getByTestId("game-dialog-intro")).toBeVisible()
    await expect(page.getByTestId("game-dialog-accent")).toBeVisible()
    await expect(page.getByTestId("game-dialog-banner")).toBeVisible()
    await expect(page.getByTestId("game-dialog-icon")).toBeVisible()

    await page.getByTestId("game-dialog-slug").fill("  Age Of Empires II  ")

    // What is stored is what is reachable, so what is typed is shown tidied.
    await expect(page.getByTestId("game-dialog")).toContainText("/esports/age-of-empires-ii")
  })

  test("nothing is offered to remove a game that does not exist yet", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-add-new-game").click()

    await expect(page.getByTestId("game-dialog-remove")).toHaveCount(0)
  })

  test("a new game joins the season on show, without a reload", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-add-new-game").click()
    await page.getByTestId("game-dialog-name").fill("Pong")
    await page.getByTestId("game-dialog-slug").fill("pong")
    await page.getByTestId("game-dialog-save").click()

    await expect(page.getByTestId("game-dialog")).toBeHidden()
    await expect(page.getByTestId("esports-game-PONG")).toBeVisible()
    await expect(page.getByTestId("esports-game-PONG")).toContainText("Pong")
  })

  test("an address another game claims is refused, and what was typed stays", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-add-new-game").click()
    await page.getByTestId("game-dialog-name").fill("Valorant Two")
    await page.getByTestId("game-dialog-slug").fill("valorant")
    await page.getByTestId("game-dialog-save").click()

    // Losing the whole form to find out what the objection was would mean typing it again.
    await expect(page.getByTestId("game-dialog-failure")).toContainText("already answers to")
    await expect(page.getByTestId("game-dialog-name")).toHaveValue("Valorant Two")
    await expect(page.getByTestId("game-dialog-slug")).toHaveValue("valorant")
  })

  test("a game added without art reads on the island's own colour", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-add-new-game").click()
    await page.getByTestId("game-dialog-name").fill("Pong")
    await page.getByTestId("game-dialog-slug").fill("pong")
    await page.getByTestId("game-dialog-save").click()

    const slice = page.getByTestId("esports-game-PONG")
    await expect(slice).toBeVisible()
    await expect(slice).not.toHaveAttribute("style", /--slice-accent:\s*#/)
  })
})
