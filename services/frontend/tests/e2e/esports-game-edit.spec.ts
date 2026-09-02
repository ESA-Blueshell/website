import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"
import type {Page} from "@playwright/test"

/**
 * The header's pencil waits for a pointer, the way the slices' do.
 *
 * Scrolled to before it is hovered, not by clicking it. A click scrolls its target into view
 * first, that scroll takes the header out from under the pointer, and a pencil is only visible
 * while what it belongs to is hovered: the click loses the hover it needs. Whether the scroll
 * is needed at all depends on where the page happens to be sitting.
 */
const openGameEditor = async (page: Page) => {
  // The island's header, not the app bar's, which is also a <header>.
  const header = page.getByTestId("esports-island").locator("header").first()
  const pencil = page.getByTestId("esports-game-edit")

  await header.scrollIntoViewIfNeeded()
  await header.hover()
  await expect(pencil).toBeVisible()
  await pencil.click()
}

/**
 * Correcting a game from the page it is on, by the same affordance the seasons and the teams
 * already carry.
 */
test.describe("changing a game", () => {
  test("a visitor is offered none of it", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/valorant")
    await page.getByTestId("esports-island").waitFor()

    await expect(page.getByTestId("esports-game-edit")).toHaveCount(0)

    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-slices").waitFor()
    await expect(page.getByTestId("esports-game-edit-VALORANT")).toHaveCount(0)
  })

  test("a game's code is shown but is not editable", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/esports/valorant")
    await openGameEditor(page)

    // It is what a team, a roster and a member's handle already point at.
    await expect(page.getByTestId("game-dialog")).toContainText("VALORANT")
    await expect(page.getByTestId("game-dialog").locator("input[value='VALORANT']")).toHaveCount(0)
  })

  test("renames a game on its own page, without a reload", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/esports/valorant")
    await openGameEditor(page)
    await page.getByTestId("game-dialog-name").fill("Valorant Reborn")
    await page.getByTestId("game-dialog-save").click()

    await expect(page.getByRole("heading", {level: 1})).toHaveText("Valorant Reborn")
  })

  test("changes what the page says about the game", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/esports/valorant")
    await openGameEditor(page)
    await page.getByTestId("game-dialog-intro").fill("Aim, plus everything else.")
    await page.getByTestId("game-dialog-save").click()

    await expect(page.getByTestId("esports-game-intro")).toContainText("Aim, plus everything else.")
  })

  test("takes a game's art away, leaving the island's own colour", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/esports/valorant")
    await openGameEditor(page)
    await page.getByTestId("game-dialog-accent").fill("")
    await page.getByTestId("game-dialog-save").click()

    await expect(page.getByTestId("game-dialog")).toHaveCount(0)
    const painted = await page.evaluate(() =>
      Array.from(document.querySelectorAll<HTMLElement>("[style*='rgb(255, 70, 85)']")).length)
    expect(painted).toBe(0)
  })

  test("an address another game claims is refused, and what was typed stays", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/esports/valorant")
    await openGameEditor(page)
    await page.getByTestId("game-dialog-slug").fill("geoguessr")
    await page.getByTestId("game-dialog-save").click()

    await expect(page.getByTestId("game-dialog-failure"))
      .toContainText("The address 'geoguessr' is already used by GeoGuessr.")
    await expect(page.getByTestId("game-dialog-slug")).toHaveValue("geoguessr")
  })

  test("a game is corrected from the band as well as from its own page", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/esports/competitive-scene")
    const slice = page.getByTestId("esports-game-VALORANT")
    const pencil = page.getByTestId("esports-game-edit-VALORANT")

    // Scrolled to, hovered, and only then pressed: see `openGameEditor` above.
    await slice.scrollIntoViewIfNeeded()
    await slice.hover()
    await expect(pencil).toBeVisible()
    await pencil.click()

    await expect(page.getByTestId("game-dialog-name")).toHaveValue("Valorant")
  })
})
