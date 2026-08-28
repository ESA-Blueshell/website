import {expect, test} from "./test"
import {installApiMocks, loginAsBoard, loginAsMember} from "./mocks"

/**
 * Taking things away, from where each of them is shown.
 *
 * Dropping a team from a season is not the same as removing the team, and the difference is
 * what the question has to make plain: a team fielded in five seasons and dropped from one
 * still played the other four.
 */
const GAME_PAGE = "/esports/valorant"

test.describe("taking things off the esports pages", () => {
  test("somebody who may not edit is offered none of it", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())
    await page.goto(GAME_PAGE)

    await expect(page.getByTestId("team-roster-1")).toBeVisible()
    await expect(page.getByTestId("team-roster-drop-1")).toHaveCount(0)
    await expect(page.getByTestId("esports-season-edit-20")).toHaveCount(0)
  })

  test("dropping a team from a season asks first, and says what it played with", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-1").hover()
    await page.getByTestId("team-roster-drop-1").click()

    const question = page.getByTestId("confirm-question")
    await expect(question).toContainText("BS Waterboarders")
    await expect(question).toContainText("Autumn 2025/26")
    await expect(question).toContainText("3 people listed")
    // The difference between dropping from a season and removing the team is said plainly.
    await expect(question).toContainText("leaves the team")
  })

  test("keeping it leaves the team where it was", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-1").hover()
    await page.getByTestId("team-roster-drop-1").click()
    await page.getByTestId("confirm-cancel").click()

    await expect(page.getByTestId("team-drop-dialog")).toBeHidden()
    await expect(page.getByTestId("team-roster-1")).toBeVisible()
  })

  test("a dropped team leaves the season without a reload", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-1").hover()
    await page.getByTestId("team-roster-drop-1").click()
    await page.getByTestId("confirm-go").click()

    await expect(page.getByTestId("team-drop-dialog")).toBeHidden()
    await expect(page.getByTestId("team-roster-1")).toHaveCount(0)
    // The other team is untouched by the one that went.
    await expect(page.getByTestId("team-roster-2")).toBeVisible()
  })

  test("a refused drop takes nothing away and says why", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.route("**/esports/seasons/*/teams/*", async (route) => {
      if (route.request().method() !== "DELETE") return route.fallback()
      await route.fulfill({
        status: 409,
        contentType: "application/json",
        body: JSON.stringify({status: 409, title: "Conflict", detail: "That team is still being counted."}),
      })
    })
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-1").hover()
    await page.getByTestId("team-roster-drop-1").click()
    await page.getByTestId("confirm-go").click()

    await expect(page.getByTestId("confirm-failure")).toHaveText("That team is still being counted.")
    // Nothing went, so the team is still there behind the question.
    await expect(page.getByTestId("team-drop-dialog")).toBeVisible()
    await page.getByTestId("confirm-cancel").click()
    await expect(page.getByTestId("team-roster-1")).toBeVisible()
  })

  test("removing a season says how many teams and players go with it", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("esports-season-node-20").hover()
    await page.getByTestId("esports-season-edit-20").click()
    await page.getByTestId("season-dialog-remove").click()

    const question = page.getByTestId("confirm-question")
    await expect(question).toContainText("Autumn 2025/26")
    await expect(question).toContainText("1 team")
    await expect(question).toContainText("3 people listed")
  })

  test("a removed season leaves the strip", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("esports-season-node-20").hover()
    await page.getByTestId("esports-season-edit-20").click()
    await page.getByTestId("season-dialog-remove").click()
    await page.getByTestId("confirm-go").click()

    await expect(page.getByTestId("season-remove-dialog")).toBeHidden()
    await expect(page.getByTestId("esports-season-node-20")).toHaveCount(0)
  })

  test("taking a player off the line-up asks first, and names them", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-1").hover()
    await page.getByTestId("team-roster-edit-1").click()
    await page.getByTestId("lineup-remove-1").click()

    await expect(page.getByTestId("confirm-question")).toContainText("Loafine")

    await page.getByTestId("confirm-go").click()
    // Off the form, and off the roster once it is saved.
    await expect(page.getByTestId("lineup-handle-1")).toHaveValue("Blackout")
    await page.getByTestId("lineup-save").click()
    await expect(page.getByTestId("team-roster-1")).not.toContainText("Loafine")
  })

  test("somebody added a moment ago goes without being asked about", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-1").hover()
    await page.getByTestId("team-roster-edit-1").click()
    await page.getByTestId("lineup-add").click()
    await page.getByTestId("lineup-handle-3").fill("Fleeting")

    await page.getByTestId("lineup-remove-3").click()

    // Nobody's record, so there is nothing to ask about.
    await expect(page.getByTestId("lineup-remove-dialog")).toBeHidden()
    await expect(page.getByTestId("lineup-handle-3")).toHaveCount(0)
  })
})
