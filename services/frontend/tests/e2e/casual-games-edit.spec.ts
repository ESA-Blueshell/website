import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

test.describe("the board keeping the casual games", () => {
  test("a visitor is offered no way to add, archive or remove a game", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/casual")

    await expect(page.getByTestId("casual-every-cell-CHESS")).toBeVisible()
    await expect(page.getByTestId("casual-add")).toHaveCount(0)
    await expect(page.getByTestId("casual-every-archive-CHESS")).toHaveCount(0)
  })

  test("the board adds a game and lands on its page", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)
    await page.goto("/casual")

    await page.getByTestId("casual-add").click()
    await page.getByTestId("casual-game-dialog-name").fill("Tetris")
    await page.getByTestId("casual-game-dialog-intro").fill("Falling blocks, fast.")
    await page.getByTestId("casual-game-dialog-save").click()

    await expect(page).toHaveURL(/\/casual\/tetris$/)
    await expect(page.getByTestId("casual-game-head")).toContainText("Falling blocks, fast.")
  })

  test("the board archives a game from its cell, and it joins the games we used to play", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)
    await page.goto("/casual")

    await page.getByTestId("casual-every-archive-CHESS").click()
    await page.getByTestId("confirm-go").click()

    await expect(page.getByTestId("casual-every-cell-CHESS")).toContainText("Archived")
    await expect(page.getByTestId("casual-olden-tile-CHESS")).toHaveCount(1)
  })

  test("removing a game is offered only once archived, and asks twice", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)
    await page.goto("/casual/wordle")
    await expect(page.getByTestId("casual-game-remove")).toHaveCount(0)

    await page.goto("/casual/overwatch")
    await page.getByTestId("casual-game-remove").click()
    await expect(page.getByTestId("remove-game-touches")).toContainText("1 channel, 0 committees and 2 events")
    await page.getByTestId("remove-game-next").click()
    await expect(page.getByTestId("remove-game-confirm")).toBeDisabled()
    await page.getByTestId("remove-game-name").fill("Overwatch")
    await page.getByTestId("remove-game-confirm").click()

    await expect(page).toHaveURL(/\/casual$/)
    await expect(page.getByTestId("casual-every-cell-OVERWATCH")).toHaveCount(0)
  })
})
