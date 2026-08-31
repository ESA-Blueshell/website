import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"
import type {Page} from "@playwright/test"

const openGameEditor = async (page: Page) => {
  await page.getByTestId("esports-island").locator("header").first().hover()
  await page.getByTestId("esports-game-edit").click()
}

/**
 * Taking a game off the site, and the far more common case of being told why it cannot go.
 */
test.describe("removing a game", () => {
  test("a visitor is offered no way to remove one", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/valorant")
    await page.getByTestId("esports-island").waitFor()

    await expect(page.getByTestId("game-dialog-remove")).toHaveCount(0)
  })

  test("the question says how many teams and people the game holds", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/esports/valorant")
    await openGameEditor(page)
    await page.getByTestId("game-dialog-remove").click()

    // Read before the question is put, rather than discovered after it is answered.
    await expect(page.getByTestId("confirm-question")).toContainText("2 teams")
    await expect(page.getByTestId("confirm-question")).toContainText("people")
  })

  test("a game whose contents cannot be read is not offered for removal", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)
    // Registered after the mocks so it wins: Playwright tries the most recent route first.
    await page.route(
      /\/esports\/games\/[A-Z0-9_]+\/contents$/,
      route => route.fulfill({status: 500, contentType: "application/json", body: "{}"}),
    )

    await page.goto("/esports/valorant")
    await openGameEditor(page)
    await page.getByTestId("game-dialog-remove").click()

    // A failed read is not an empty game. The question is not put at all, rather than put with
    // "holds no teams" standing in for an answer nobody got.
    await expect(page.getByTestId("confirm-question")).toHaveCount(0)
    await expect(page.getByTestId("game-dialog-failure")).toContainText("could not be read")
  })

  test("a game holding teams says what it holds and that its history stays", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/esports/valorant")
    await openGameEditor(page)
    await page.getByTestId("game-dialog-remove").click()

    // There is no softer act to offer any more: a game leaves the front of the site by not
    // being entered in a season, which is a thing that happens rather than a thing to press.
    await expect(page.getByTestId("confirm-question")).toContainText("cannot be removed")
    await expect(page.getByTestId("confirm-question")).toContainText("stays readable")
  })

  test("a game holding teams is refused, and nothing goes", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/esports/valorant")
    await openGameEditor(page)
    await page.getByTestId("game-dialog-remove").click()
    await page.getByTestId("confirm-go").click()

    await expect(page.getByTestId("confirm-failure"))
      .toContainText("Valorant holds 2 teams and 6 people, so it cannot be removed.")
    // Still here: the refusal is the api's, and the page did not act as though it had gone.
    await page.goto("/esports/valorant")
    await expect(page.getByRole("heading", {level: 1})).toHaveText("Valorant")
  })

  test("a game added moments ago holds nothing, so it can go again", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("game-dialog-kind-new-game").click()
    await page.getByTestId("game-dialog-name").fill("Pong")
    await page.getByTestId("game-dialog-slug").fill("pong")
    await page.getByTestId("game-dialog-save").click()
    await page.getByTestId("esports-game-PONG").waitFor()

    // Entering a game is not fielding a team in it, so a game added by mistake holds nothing
    // and goes without argument -- which is the whole reason removal is real rather than soft.
    await page.goto("/esports/pong")
    await openGameEditor(page)
    await page.getByTestId("game-dialog-remove").click()
    await expect(page.getByTestId("confirm-question")).toContainText("holds no teams")
  })

  test("a game added by mistake is removed and its page stops answering", async ({page, context}) => {
    await installApiMocks(page, {
      esportsGames: [
        {game: "VALORANT", name: "Valorant", slug: "valorant", accent: "#ff4655", banner: null, icon: null,
          intro: null, sortIndex: 1, current: true},
        {game: "PONG", name: "Pong", slug: "pong", accent: null, banner: null, icon: null,
          intro: null, sortIndex: 2, current: true},
      ],
      esportsTeams: [],
    })
    await loginAsBoard(context)

    await page.goto("/esports/pong")
    await openGameEditor(page)
    await page.getByTestId("game-dialog-remove").click()
    await page.getByTestId("confirm-go").click()

    // Sent away from a page that no longer exists, and the address stops answering.
    await expect(page).toHaveURL(/competitive-scene/)
    await page.goto("/esports/pong")
    await expect(page.getByTestId("not-found")).toBeVisible()
  })

  test("the band reflects the removal without a reload", async ({page, context}) => {
    await installApiMocks(page, {
      esportsGames: [
        {game: "VALORANT", name: "Valorant", slug: "valorant", accent: "#ff4655", banner: null, icon: null,
          intro: null, sortIndex: 1, current: true},
        {game: "PONG", name: "Pong", slug: "pong", accent: null, banner: null, icon: null,
          intro: null, sortIndex: 2, current: true},
      ],
      esportsTeams: [],
    })
    await loginAsBoard(context)

    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-slices").waitFor()
    await page.goto("/esports/pong")
    await openGameEditor(page)
    await page.getByTestId("game-dialog-remove").click()
    await page.getByTestId("confirm-go").click()
    await expect(page).toHaveURL(/competitive-scene/)

    // Landed on the index by the removal, not by a reload, and Pong is not on it.
    await expect(page.getByTestId("esports-game-PONG")).toHaveCount(0)
  })
})
