import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

/**
 * Adding a game from the index band. A game used to require editing a compiled enum and deploying.
 */
test.describe("adding a game", () => {
  test("a visitor is offered no way to add one", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-slices").waitFor()

    await expect(page.getByTestId("esports-game-add")).toHaveCount(0)
  })

  test("asks for the game name and page address", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("add-team-game").selectOption("__another__")

    await expect(page.getByTestId("add-game-name")).toBeVisible()
    await expect(page.getByTestId("add-game-slug")).toBeVisible()
  })

  test("shows where the page will answer as the address is typed", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("add-team-game").selectOption("__another__")
    await page.getByTestId("add-game-slug").fill("  Age Of Empires II  ")

    // What is stored is what is reachable, so what is typed is shown tidied.
    await expect(page.getByTestId("add-team-dialog")).toContainText("/esports/age-of-empires-ii")
  })

  test("the game joins the season on show, without a reload", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("add-team-game").selectOption("__another__")
    await page.getByTestId("add-game-name").fill("Pong")
    await page.getByTestId("add-game-slug").fill("pong")
    await page.getByTestId("add-team-name").fill("BS Paddlers")
    await page.getByTestId("add-team-save").click()

    await expect(page.getByTestId("esports-game-PONG")).toBeVisible()
    await expect(page.getByTestId("esports-game-PONG")).toContainText("Pong")
  })

  test("an address another game claims is refused, and what was typed stays", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-add").click()
    await page.getByTestId("add-team-game").selectOption("__another__")
    await page.getByTestId("add-game-name").fill("Valorant Two")
    await page.getByTestId("add-game-slug").fill("valorant")
    await page.getByTestId("add-team-name").fill("BS Seconds")
    await page.getByTestId("add-team-save").click()

    await expect(page.getByTestId("add-team-dialog")).toContainText("is already used by")
    // The address is the thing to correct, so nothing typed is thrown away.
    await expect(page.getByTestId("add-game-name")).toHaveValue("Valorant Two")
    await expect(page.getByTestId("add-game-slug")).toHaveValue("valorant")
  })

  test("a game added without art reads on the island's own colour", async ({page, context}) => {
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

    // Its page answers straight away, on the association's blue and with no mark.
    await page.goto("/esports/pong")
    await expect(page.getByRole("heading", {level: 1})).toHaveText("Pong")
  })

  test("the game is offered to field a team in from then on", async ({page, context}) => {
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

    await page.goto("/account")
    // Every game a member can be given a handle in, which is every game there is.
    await expect(page.getByTestId("game-handle-pong")).toBeVisible()
  })
})
