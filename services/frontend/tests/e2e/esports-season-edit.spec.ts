import {expect, test} from "./test"
import {installApiMocks, loginAsBoard, loginAsMember} from "./mocks"

/**
 * Editing a season from the page it is shown on.
 *
 * The affordance is offered by the same rule the api enforces, so what is asserted here is
 * that it appears for somebody who may take it up, that it is attached to the season being
 * pointed at rather than to all of them, and that it is absent for everybody else.
 */
const GAME_PAGE = "/esports/valorant"

/**
 * What a person does: bring the pointer to the season, then take up the affordance it
 * reveals. On a touch screen the hover is a no-op and the affordance is already standing.
 */
const openEditor = async (page: import("@playwright/test").Page, seasonId: number) => {
  await page.getByTestId(`esports-season-node-${seasonId}`).hover()
  await page.getByTestId(`esports-season-edit-${seasonId}`).click()
}

test.describe("editing a season where it is shown", () => {
  test("the affordance belongs to the season under the pointer", async ({page, browserName}, testInfo) => {
    test.skip(testInfo.project.name === "mobile-chrome", "There is no pointer to hover with.")
    void browserName
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    const first = page.getByTestId("esports-season-edit-20")
    const second = page.getByTestId("esports-season-edit-19")
    // Present in the page for every season, but shown on none of them until one is pointed at.
    await expect(first).toBeHidden()
    await expect(second).toBeHidden()

    await page.getByTestId("esports-season-node-20").hover()
    await expect(first).toBeVisible()
    await expect(second).toBeHidden()
  })

  test("with no pointer the affordance simply stands", async ({page}, testInfo) => {
    test.skip(testInfo.project.name !== "mobile-chrome", "Only a touch screen has nothing to hover with.")
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await expect(page.getByTestId("esports-season-edit-20")).toBeVisible()
  })

  test("somebody who may not edit esports is offered nothing", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())
    await page.goto(GAME_PAGE)

    await expect(page.getByTestId("esports-season-timeline")).toBeVisible()
    await expect(page.getByTestId("esports-season-edit-20")).toHaveCount(0)
    await expect(page.getByTestId("esports-season-edit-19")).toHaveCount(0)
  })

  test("a visitor who is not signed in is offered nothing", async ({page}) => {
    await installApiMocks(page)
    await page.goto(GAME_PAGE)

    await expect(page.getByTestId("esports-season-timeline")).toBeVisible()
    await expect(page.getByTestId("esports-season-edit-20")).toHaveCount(0)
  })

  test("the dialog opens on the season's own name and dates, and saving shows the change", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await openEditor(page, 20)
    const dialog = page.getByTestId("season-dialog")
    await expect(dialog).toBeVisible()
    await expect(page.getByTestId("season-dialog-name")).toHaveValue("Autumn 2025/26")
    await expect(page.getByTestId("season-dialog-start")).toHaveValue("2025-09-01")
    await expect(page.getByTestId("season-dialog-end")).toHaveValue("2026-01-31")

    await page.getByTestId("season-dialog-name").fill("Winter 2025/26")
    await page.getByTestId("season-dialog-save").click()

    await expect(dialog).toBeHidden()
    // The strip says the new name without the page being fetched again.
    await expect(page.getByTestId("esports-season-node-20")).toContainText("Winter 2025/26")
  })

  test("a refused save says why and keeps what was typed", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    // Registered after the mocks, so it is the one that answers.
    await page.route("**/esports/seasons/*", async (route) => {
      if (route.request().method() !== "PUT") return route.fallback()
      await route.fulfill({
        status: 400,
        contentType: "application/json",
        body: JSON.stringify({status: 400, title: "Bad Request", detail: "That overlaps Spring 2024/25."}),
      })
    })
    await page.goto(GAME_PAGE)

    await openEditor(page, 20)
    await page.getByTestId("season-dialog-name").fill("Overlapping")
    await page.getByTestId("season-dialog-save").click()

    await expect(page.getByTestId("season-dialog-failure")).toHaveText("That overlaps Spring 2024/25.")
    await expect(page.getByTestId("season-dialog")).toBeVisible()
    await expect(page.getByTestId("season-dialog-name")).toHaveValue("Overlapping")
  })

  test("the dialog is dismissed from the keyboard and gives focus back", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    const affordance = page.getByTestId("esports-season-edit-20")
    await openEditor(page, 20)
    await expect(page.getByTestId("season-dialog")).toBeVisible()

    await page.keyboard.press("Escape")
    await expect(page.getByTestId("season-dialog")).toBeHidden()
    // Focus returns to what opened it, rather than to the top of the document.
    await expect(affordance).toBeFocused()
  })

  test("nothing about the dialog carries the styling of the rest of the site", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await openEditor(page, 20)
    const dialog = page.getByTestId("season-dialog")
    await expect(dialog).toBeVisible()
    await expect(dialog).toHaveClass(/esports-island/)
    await expect(dialog.locator(".v-btn, .v-card, .v-dialog, .v-text-field, .v-overlay")).toHaveCount(0)
  })
})
