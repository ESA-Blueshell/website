import {expect, test, type Page} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

const INDEX = "/esports/competitive-scene"

/**
 * Waits until a control has stopped moving.
 *
 * The band rearranges around a slice that has just arrived, and a click landing mid-rearrange
 * is refused for being unstable. Playwright retries, but the retry window is shorter than the
 * band takes on a loaded machine, so this waits for the box to repeat rather than for a clock.
 */
const stillOnScreen = async (page: Page, testid: string) => {
  const control = page.getByTestId(testid)
  await expect(control).toBeVisible()
  let last = ""
  await expect.poll(async () => {
    const box = await control.boundingBox()
    const now = JSON.stringify(box)
    const same = now === last
    last = now
    return same
  }).toBe(true)
}

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

    await expect(page.getByTestId("esports-game-add")).toHaveCount(0)
  })

  test("one way in, and the choice is made inside it", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await expect(page.getByTestId("esports-game-add")).toContainText("Add a game")
    await page.getByTestId("esports-game-add").click()

    // Adding a game the association has played and adding one it has just started are one
    // intention answered two ways, so they are one pane and a choice rather than two panes.
    await expect(page.getByTestId("game-dialog-kind-played-before")).toContainText("An existing game")
    await expect(page.getByTestId("game-dialog-kind-new-game")).toContainText("A new game")
  })

  test("a game played before is put in with one press, and is not offered twice", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-add").click()

    // Valorant and CS2 already play this season, so there is nothing to enter of them.
    await expect(page.getByTestId("game-dialog-known-ROCKET_LEAGUE")).toBeVisible()
    await expect(page.getByTestId("game-dialog-known-VALORANT")).toHaveCount(0)

    await page.getByTestId("game-dialog-known-ROCKET_LEAGUE").click()

    await expect(page.getByTestId("game-dialog")).toBeHidden()
    await expect(page.getByTestId("esports-game-ROCKET_LEAGUE")).toBeVisible()
  })

  test("a game entered with nobody in it says it is not public yet", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("game-dialog-known-ROCKET_LEAGUE").click()

    // The board's list of what is left to do, said rather than left to be inferred from a
    // slice that would otherwise read as a finished one.
    await expect(page.getByTestId("esports-quiet-ROCKET_LEAGUE"))
      .toContainText("visitors do not see it")
  })

  test("a visitor is not shown a game nobody is fielded in", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)
    await page.goto(INDEX)
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("game-dialog-known-ROCKET_LEAGUE").click()
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
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("game-dialog-known-ROCKET_LEAGUE").click()

    // Where it leads is the claim; following it would be a fight with whichever slice the
    // band has open, which is a different thing and is asserted where the band is.
    await expect(page.getByTestId("esports-link-ROCKET_LEAGUE"))
      .toHaveAttribute("href", /\/esports\/rocketleague\?season=\d+/)
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
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("game-dialog-known-ROCKET_LEAGUE").click()
    await expect(page.getByTestId("esports-game-ROCKET_LEAGUE")).toBeVisible()
    // The way out of the season is inside the slice, so the slice has to be the open one. The
    // band opens what the pointer is on, which is how a reader reaches it too.
    await page.getByTestId("esports-game-ROCKET_LEAGUE").hover()
    await stillOnScreen(page, "esports-take-out-ROCKET_LEAGUE")

    await page.getByTestId("esports-take-out-ROCKET_LEAGUE").click()

    await expect(page.getByTestId("esports-game-ROCKET_LEAGUE")).toHaveCount(0)
  })

  test("a new game is described in full, and its address is shown as it is typed", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("game-dialog-kind-new-game").click()

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
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("game-dialog-kind-new-game").click()

    await expect(page.getByTestId("game-dialog-remove")).toHaveCount(0)
  })

  test("a new game joins the season on show, without a reload", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto(INDEX)
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("game-dialog-kind-new-game").click()
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
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("game-dialog-kind-new-game").click()
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
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("game-dialog-kind-new-game").click()
    await page.getByTestId("game-dialog-name").fill("Pong")
    await page.getByTestId("game-dialog-slug").fill("pong")
    await page.getByTestId("game-dialog-save").click()

    const slice = page.getByTestId("esports-game-PONG")
    await expect(slice).toBeVisible()
    await expect(slice).not.toHaveAttribute("style", /--slice-accent:\s*#/)
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
