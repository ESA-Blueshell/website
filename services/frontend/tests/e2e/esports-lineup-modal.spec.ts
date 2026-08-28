import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"
import type {Page} from "@playwright/test"

/**
 * A line-up is edited in a dialog over the page, the same as adding a team and editing a season.
 * It was briefly edited inline in the band instead, which did not look good.
 */
const GAME_PAGE = "/esports/valorant"

const openLineup = async (page: Page) => {
  await page.getByTestId("team-roster-1").hover()
  await page.getByTestId("team-roster-edit-1").click()
  await expect(page.getByTestId("lineup-editor")).toBeVisible()
}

test.describe("editing a line-up", () => {
  test("opens in a dialog over the page", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await expect(page.getByTestId("lineup-dialog")).toBeVisible()
    await expect(page.locator(".island-dialog__scrim")).toHaveCount(1)
  })

  test("names the team and season it is editing", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await expect(page.getByTestId("lineup-dialog")).toContainText("BS Waterboarders")
    await expect(page.getByTestId("lineup-dialog")).toContainText("Autumn 2025/26")
  })

  test("leaves the band alone while it is open", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    // The slice is not expanded or rearranged to make room for a form.
    await expect(page.getByTestId("team-roster-1").getByTestId("lineup-editor")).toHaveCount(0)
    await expect(page.getByTestId("team-roster-2")).toBeAttached()
  })

  test("closes without saving when cancelled", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await page.getByTestId("lineup-title-1").fill("In-game leader")
    await page.getByTestId("lineup-cancel").click()

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
