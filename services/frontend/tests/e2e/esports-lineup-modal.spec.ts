import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"
import type {Page} from "@playwright/test"

/**
 * A line-up is changed over the page, the same way a team is added and a season is edited.
 *
 * It was briefly changed in the band instead, in the slice that showed it. A form squeezed
 * into a share of a row is not a form, and the band rearranging itself around one read as the
 * page coming apart rather than as something being filled in.
 */
const GAME_PAGE = "/esports/valorant"

const openLineup = async (page: Page) => {
  await page.getByTestId("team-roster-1").hover()
  await page.getByTestId("team-roster-edit-1").click()
  await expect(page.getByTestId("lineup-editor")).toBeVisible()
}

test.describe("editing a line-up over the page", () => {
  test("the editor opens in a dialog", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await expect(page.getByTestId("lineup-dialog")).toBeVisible()
    await expect(page.locator(".island-dialog__scrim")).toHaveCount(1)
  })

  test("the dialog names the team and the season it is editing", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    const dialog = page.getByTestId("lineup-dialog")
    await expect(dialog).toContainText("BS Waterboarders")
    await expect(dialog).toContainText("Autumn 2025/26")
  })

  test("the band is left as it was underneath", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    // Nothing is put inside the slice, and no slice is widened to make room for a form.
    await expect(page.getByTestId("team-roster-1").getByTestId("lineup-editor")).toHaveCount(0)
    await expect(page.getByTestId("team-roster-2")).toBeAttached()
  })

  test("escape shuts it, and nothing typed into it is kept", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await page.getByTestId("lineup-title-1").fill("In-game leader")
    await page.keyboard.press("Escape")

    await expect(page.getByTestId("lineup-editor")).toHaveCount(0)
    await expect(page.getByTestId("team-roster-1")).not.toContainText("In-game leader")
  })

  test("adding a team is asked for the same way", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add").click()

    await expect(page.getByTestId("add-team-dialog")).toBeVisible()
    await expect(page.locator(".island-dialog__scrim")).toHaveCount(1)
  })

  test("what the editor could do, it still does", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await expect(page.getByTestId("lineup-handle-0")).toHaveValue("AriosFury")
    await page.getByTestId("lineup-title-1").fill("In-game leader")
    await page.getByTestId("lineup-save").click()

    await expect(page.getByTestId("lineup-editor")).toHaveCount(0)
    await expect(page.getByTestId("team-roster-1")).toContainText("In-game leader")
  })
})
