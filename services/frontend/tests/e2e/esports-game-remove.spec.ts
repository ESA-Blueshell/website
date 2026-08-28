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

  test("the question says how many teams and roster places the game holds", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/esports/valorant")
    await openGameEditor(page)
    await page.getByTestId("game-dialog-remove").click()

    // Read before the question is put, rather than discovered after it is answered.
    await expect(page.getByTestId("confirm-question")).toContainText("2 teams")
    await expect(page.getByTestId("confirm-question")).toContainText("roster place")
  })

  test("a game holding teams offers the softer act instead", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/esports/valorant")
    await openGameEditor(page)
    await page.getByTestId("game-dialog-remove").click()

    await expect(page.getByTestId("confirm-question")).toContainText("still fielded")
    await expect(page.getByTestId("confirm-question")).toContainText("stays readable")
  })

  test("a game holding teams is refused, and nothing goes", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/esports/valorant")
    await openGameEditor(page)
    await page.getByTestId("game-dialog-remove").click()
    await page.getByTestId("confirm-go").click()

    await expect(page.getByTestId("confirm-failure")).toContainText("no longer fielded")
    // Still here: the refusal is the api's, and the page did not act as though it had gone.
    await page.goto("/esports/valorant")
    await expect(page.getByRole("heading", {level: 1})).toHaveText("Valorant")
  })

  test("a game added moments ago already holds what was added with it", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("add-team-game").selectOption("__another__")
    await page.getByTestId("add-game-name").fill("Pong")
    await page.getByTestId("add-game-slug").fill("pong")
    await page.getByTestId("add-team-name").fill("BS Paddlers")
    await page.getByTestId("add-team-save").click()
    await page.getByTestId("esports-game-PONG").waitFor()

    // A game joins a season by having a team fielded in it, so the team added with it counts
    // against removing it from the moment it exists.
    await page.goto("/esports/pong")
    await openGameEditor(page)
    await page.getByTestId("game-dialog-remove").click()
    await expect(page.getByTestId("confirm-question")).toContainText("1 team")
    await expect(page.getByTestId("confirm-question")).toContainText("cannot be removed")
  })

  test("a game added by mistake is removed and its page stops answering", async ({page, context}) => {
    await installApiMocks(page, {
      esportsGames: [
        {game: "VALORANT", name: "Valorant", slug: "valorant", accent: "#ff4655", mark: null,
          banner: null, intro: null, sortIndex: 1, fielded: true},
        {game: "PONG", name: "Pong", slug: "pong", accent: null, mark: null, banner: null,
          intro: null, sortIndex: 2, fielded: true},
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
        {game: "VALORANT", name: "Valorant", slug: "valorant", accent: "#ff4655", mark: null,
          banner: null, intro: null, sortIndex: 1, fielded: true},
        {game: "PONG", name: "Pong", slug: "pong", accent: null, mark: null, banner: null,
          intro: null, sortIndex: 2, fielded: true},
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
