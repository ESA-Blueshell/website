import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"
import type {Page} from "@playwright/test"

/**
 * A line-up is changed on its own page, the same way a team is added and a season is edited.
 *
 * The page carries the band the team is drawn in beside the form, live, so what the form makes is
 * seen before it is saved. The way back returns to the game page on the season it was opened from.
 */
const GAME_PAGE = "/competition/valorant"

const openLineup = async (page: Page) => {
  await page.getByTestId("team-roster-1").hover()
  await page.getByTestId("team-roster-edit-1").click()
  await expect(page.getByTestId("lineup-editor")).toBeVisible()
  await expect(page.getByTestId("lineup-loading")).toHaveCount(0)
}

test.describe("editing a line-up on its own page", () => {
  test("the editor is a page rather than a dialog over one", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await expect(page).toHaveURL(/\/competition\/valorant\/teams\/1\/edit\?season=\d+$/)
    await expect(page.locator(".island-dialog__scrim")).toHaveCount(0)
    await expect(page.getByTestId("team-roster-slices")).toHaveCount(0)
  })

  test("the page names the team and the season it is editing", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await expect(page.getByRole("heading", {level: 1})).toHaveText("BS Waterboarders")
    await expect(page.getByTestId("team-edit")).toContainText("Autumn 2025")
  })

  test("the preview is a picture of the band, not a second copy to operate", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await page.getByTestId("lineup-title-1").locator("input").fill("In-game leader")

    const preview = page.getByTestId("team-edit-preview")
    await expect(preview).toContainText("In-game leader")
    await expect(preview.locator("[inert]")).toHaveCount(1)
  })

  test("the way back leaves without keeping anything typed", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await page.getByTestId("lineup-title-1").locator("input").fill("In-game leader")
    await page.getByTestId("team-edit-back").click()

    await expect(page).toHaveURL(/\/competition\/valorant(\?season=\d+)?$/)
    await expect(page.getByTestId("team-roster-1")).not.toContainText("In-game leader")
  })

  test("adding a team is asked for the same way", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add").click()

    await expect(page).toHaveURL(/\/competition\/valorant\/teams\/new(\?season=\d+)?$/)
    await expect(page.locator(".island-dialog__scrim")).toHaveCount(0)
  })

  test("what the editor could do, it still does", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await expect(page.getByTestId("lineup-handle-0").locator("input")).toHaveValue("AriosFury")
    await page.getByTestId("lineup-title-1").locator("input").fill("In-game leader")
    await page.getByTestId("lineup-save").click()

    await expect(page).toHaveURL(/\/competition\/valorant(\?season=\d+)?$/)
    await expect(page.getByTestId("team-roster-1")).toContainText("In-game leader")
  })

  test("a team this game never fielded is not found", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/competition/valorant/teams/999/edit")

    await expect(page.getByTestId("not-found")).toBeVisible()
  })

  test("on a phone the preview sits above the form and folds away", async ({page}) => {
    await page.setViewportSize({width: 390, height: 844})
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/competition/valorant/teams/1/edit")
    await expect(page.getByTestId("lineup-editor")).toBeVisible()

    const preview = page.getByTestId("team-edit-preview")
    const form = page.getByTestId("lineup-editor")
    expect((await preview.boundingBox())!.y).toBeLessThan((await form.boundingBox())!.y)

    const toggle = preview.locator(".edit-page__preview-toggle")
    await expect(toggle).toHaveAttribute("aria-expanded", "true")
    await toggle.click()
    await expect(toggle).toHaveAttribute("aria-expanded", "false")
    await expect(preview.locator(".edit-page__preview-body")).toBeHidden()
  })
})
