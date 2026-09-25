import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"
import type {Page} from "@playwright/test"

/**
 * Scrolled to before it is hovered, not by clicking it: a click scrolls its target into view
 * first, and that scroll takes the header out from under the pointer that is revealing the
 * pencil.
 */
const openGameEditor = async (page: Page) => {
  const header = page.getByTestId("esports-island").locator("header").first()
  const pencil = page.getByTestId("esports-game-edit")

  await header.scrollIntoViewIfNeeded()
  await header.hover()
  await expect(pencil).toBeVisible()
  await pencil.click()
}

/**
 * Taking a game off the site from its edit page: a game is archived first, and only an archived
 * game is offered for removal, which asks for its name typed out. A game that fielded teams stays.
 */
test.describe("removing a game", () => {
  const PONG_ONLY = {
    esportsGames: [
      {code: "VALORANT", name: "Valorant", slug: "valorant", accent: "#ff4655", banner: null, icon: null,
        intro: null, sortIndex: 1, current: true},
      {code: "PONG", name: "Pong", slug: "pong", accent: null, banner: null, icon: null,
        intro: null, sortIndex: 2, current: true},
    ],
    esportsTeams: [],
  }

  const archive = async (page: Page) => {
    await page.getByTestId("game-edit-archive").click()
    await page.getByTestId("confirm-go").click()
  }

  test("a visitor is offered no way to remove one", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/competition/valorant")
    await page.getByTestId("esports-island").waitFor()

    await expect(page.getByTestId("esports-game-edit")).toHaveCount(0)
  })

  test("a game still played is archived before it can be removed", async ({page, context}) => {
    await installApiMocks(page, PONG_ONLY)
    await loginAsBoard(context)

    await page.goto("/competition/pong")
    await openGameEditor(page)
    await expect(page.getByTestId("game-edit-remove")).toHaveCount(0)
    await archive(page)

    // Archiving saves and goes back; the edit page opened again offers the removal.
    await expect(page).toHaveURL(/\/competition\/pong$/)
    await openGameEditor(page)
    await expect(page.getByTestId("game-edit-archive")).toHaveText("Bring back")
    await expect(page.getByTestId("game-edit-remove")).toBeVisible()
  })

  test("a game holding teams says what it holds, and cannot be removed", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/competition/valorant")
    await openGameEditor(page)
    await archive(page)
    await openGameEditor(page)
    await page.getByTestId("game-edit-remove").click()

    await expect(page.getByTestId("remove-game-touches")).toContainText("Valorant holds 2 teams and 6 people")
    await expect(page.getByTestId("remove-game-touches")).toContainText("cannot be removed")
    await expect(page.getByTestId("remove-game-next")).toHaveCount(0)
  })

  test("a game added by mistake is removed once its name is typed, and its page stops answering", async ({page, context}) => {
    await installApiMocks(page, PONG_ONLY)
    await loginAsBoard(context)

    await page.goto("/competition/pong")
    await openGameEditor(page)
    await archive(page)
    await openGameEditor(page)
    await page.getByTestId("game-edit-remove").click()
    await page.getByTestId("remove-game-next").click()
    await expect(page.getByTestId("remove-game-confirm")).toBeDisabled()
    await page.getByTestId("remove-game-name").fill("Pong")
    await page.getByTestId("remove-game-confirm").click()

    // Sent to the index, which no longer carries it, and the address stops answering.
    await expect(page).toHaveURL(/\/competition$/)
    await expect(page.getByTestId("esports-game-PONG")).toHaveCount(0)
    await page.goto("/competition/pong")
    await expect(page.getByTestId("not-found")).toBeVisible()
  })
})
