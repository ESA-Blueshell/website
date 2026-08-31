import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

/**
 * The team itself, from the same place its line-up is edited.
 *
 * A name and a banner belong to the team in every season, not to the shown one, so they are
 * marked as such. A recorded name is what tells an admin who a handle belongs to; whether it
 * reaches the public page is the api's decision and consent's, not this form's.
 */
const GAME_PAGE = "/esports/valorant"

const openLineup = async (page: import("@playwright/test").Page) => {
  await page.getByTestId("team-roster-1").hover()
  await page.getByTestId("team-roster-edit-1").click()
  await expect(page.getByTestId("lineup-editor")).toBeVisible()
}

test.describe("changing the team, not just its line-up", () => {
  test("the team's own name and banner open on what they are", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await expect(page.getByTestId("lineup-team-name")).toHaveValue("BS Waterboarders")
    // One control for the picture rather than a filename beside a picker, and the team the
    // mocks seed carries none, so it opens on the empty state.
    await expect(page.getByTestId("lineup-team-banner-empty")).toBeVisible()
    await expect(page.getByTestId("lineup-editor")).toContainText("The team")
  })

  test("renaming the team shows on the page without a reload", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await page.getByTestId("lineup-team-name").fill("BS Renamed")
    await page.getByTestId("lineup-save").click()

    await expect(page.getByTestId("lineup-editor")).toBeHidden()
    await expect(page.getByTestId("team-roster-1")).toContainText("BS Renamed")
  })

  test("a team cannot be left without a name", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await page.getByTestId("lineup-team-name").fill("   ")

    await expect(page.getByTestId("lineup-save")).toBeDisabled()
  })

  test("the recorded name is shown, so an admin can tell who a handle is", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    // On the page this member is named because they allowed it; here it is shown either way,
    // since recording a name is not publishing one.
    await expect(page.getByTestId("lineup-name-0")).toHaveValue("Viktor Petrov")
    await expect(page.getByTestId("lineup-name-1")).toHaveValue("")
  })

  test("a recorded name is written down without reaching the page", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await page.getByTestId("lineup-name-1").fill("Sanne Kok")
    await page.getByTestId("lineup-save").click()
    await expect(page.getByTestId("lineup-editor")).toBeHidden()

    // Written down: it comes back when the line-up is opened again.
    await openLineup(page)
    await expect(page.getByTestId("lineup-name-1")).toHaveValue("Sanne Kok")
    await page.getByTestId("lineup-cancel").click()

    // Not published: the api names only a member who allowed it, and that has not changed.
    await expect(page.getByTestId("team-roster-1")).not.toContainText("Sanne Kok")
  })

  test("deleting the team asks first, and says it is not the same as removing it", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await page.getByTestId("lineup-remove-team").click()

    const question = page.getByTestId("confirm-question")
    await expect(question).toContainText("BS Waterboarders")
    await expect(question).toContainText("all of them")
    await expect(question).toContainText("not the same as removing it from the shown season")
  })

  test("a removed team leaves every season it played", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await page.getByTestId("lineup-remove-team").click()
    await page.getByTestId("confirm-go").click()

    await expect(page.getByTestId("team-remove-dialog")).toBeHidden()
    await expect(page.getByTestId("lineup-editor")).toBeHidden()
    await expect(page.getByTestId("team-roster-1")).toHaveCount(0)
    // The other team is untouched.
    await expect(page.getByTestId("team-roster-2")).toBeVisible()
  })

  test("a refused removal takes nothing away and says why", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.route("**/esports/teams/*", async (route) => {
      if (route.request().method() !== "DELETE") return route.fallback()
      await route.fulfill({
        status: 409,
        contentType: "application/json",
        body: JSON.stringify({status: 409, title: "Conflict", detail: "That team is still fielded somewhere."}),
      })
    })
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await page.getByTestId("lineup-remove-team").click()
    await page.getByTestId("confirm-go").click()

    await expect(page.getByTestId("confirm-failure")).toHaveText("That team is still fielded somewhere.")
    await page.getByTestId("confirm-cancel").click()
    await page.getByTestId("lineup-cancel").click()
    await expect(page.getByTestId("team-roster-1")).toBeVisible()
  })
})
