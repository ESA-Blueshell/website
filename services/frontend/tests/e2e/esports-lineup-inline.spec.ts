import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

/**
 * A line-up is changed in the slice that shows it rather than in something over the page.
 *
 * What is being edited is the thing on the page, and it stays the thing on the page while it
 * is edited: the band is still a band, the other teams are still beside it, and nothing is
 * covering them.
 */
const GAME_PAGE = "/esports/valorant"

const openLineup = async (page: import("@playwright/test").Page) => {
  await page.getByTestId("team-roster-1").hover()
  await page.getByTestId("team-roster-edit-1").click()
  await expect(page.getByTestId("lineup-editor")).toBeVisible()
}

test.describe("editing a line-up in the band", () => {
  test("the editor belongs to the slice it edits", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    // Inside the slice, not merely somewhere on the page.
    await expect(page.getByTestId("team-roster-1").getByTestId("lineup-editor")).toBeVisible()
  })

  test("nothing is put over the page to edit it", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await expect(page.locator(".island-dialog__scrim")).toHaveCount(0)
    // The band is still a band: the other team is beside it, not behind something.
    await expect(page.getByTestId("team-roster-2")).toBeVisible()
  })

  test("the team's photograph does not read through the form", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    const banner = page.getByTestId("team-roster-1").locator(".team-slice__banner")
    await expect(banner).toBeHidden()
  })

  test("the editor takes the room a form needs", async ({page}, info) => {
    test.skip(info.project.name === "mobile-chrome", "Stacked, every slice already has the width.")
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    const measured = await page.evaluate(() => {
      const band = document.querySelector('[data-testid="team-roster-slices"]') as HTMLElement
      const editing = document.querySelector('[data-testid="team-roster-1"]') as HTMLElement
      return {
        band: Math.round(band.getBoundingClientRect().width),
        editing: Math.round(editing.getBoundingClientRect().width),
      }
    })

    // Most of the band, because a form squeezed into a share of it is not a form.
    expect(measured.editing).toBeGreaterThan(measured.band * 0.6)
  })

  test("adding a team is still asked for over the page", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add").click()

    // Adding was left as it was: only editing moved into the band.
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
