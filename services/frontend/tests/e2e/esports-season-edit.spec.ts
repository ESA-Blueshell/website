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

  test("the affordance goes when the pointer does, however it was revealed", async ({page}, testInfo) => {
    test.skip(testInfo.project.name === "mobile-chrome", "There is no pointer to hover with.")
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    const node = page.getByTestId("esports-season-node-20")
    const pencil = page.getByTestId("esports-season-edit-20")
    await node.hover()
    await expect(pencil).toBeVisible()

    // Choosing a season focuses the band it is drawn on. That is not a reason for the season
    // to keep offering to be edited once the pointer has moved somewhere else.
    await node.click()
    await page.mouse.move(10, 10)

    await expect(pencil).toBeHidden()
  })

  test("a keyboard reveals the affordance the pointer does, and reaches it", async ({page}, testInfo) => {
    test.skip(testInfo.project.name === "mobile-chrome", "The affordances stand, so there is nothing to reveal.")
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await expect(page.getByTestId("team-roster-1")).toBeVisible()

    // The strip reads oldest first, so the earlier season is where a keyboard entering the
    // strip arrives. Clicking it leaves the focus there without revealing anything, which is
    // the pointer half of the rule; the tab that follows is the keyboard taking over.
    const pencil = page.getByTestId("esports-season-edit-19")
    await page.getByTestId("esports-season-node-19").click()
    await expect(page.getByTestId("team-roster-3")).toBeVisible()
    await page.mouse.move(10, 10)
    await expect(pencil).toBeHidden()

    // Revealed and reached in one step, because the focus never leaves the season it belongs
    // to: the tab that takes it off the band puts it on the affordance the band was hiding.
    await page.keyboard.press("Tab")
    await expect(pencil).toBeVisible()
    await expect(pencil).toBeFocused()

    // On to the next season, which offers its own and takes the first one's away again.
    await page.keyboard.press("Tab")
    await expect(page.getByTestId("esports-season-node-20")).toBeFocused()
    await expect(page.getByTestId("esports-season-edit-20")).toBeVisible()
    await expect(pencil).toBeHidden()
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
    await expect(page.getByTestId("season-dialog-name")).toHaveValue("Autumn 2025")
    await expect(page.getByTestId("season-dialog-start")).toHaveValue("2025-09-01")
    await expect(page.getByTestId("season-dialog-end")).toHaveValue("2026-01-31")

    await page.getByTestId("season-dialog-name").fill("Autumn 2026")
    await page.getByTestId("season-dialog-save").click()

    await expect(dialog).toBeHidden()
    // The strip says the new name without the page being fetched again.
    await expect(page.getByTestId("esports-season-node-20")).toContainText("Autumn 2026")
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
        body: JSON.stringify({status: 400, title: "Bad Request", detail: "That overlaps Spring 2025."}),
      })
    })
    await page.goto(GAME_PAGE)

    await openEditor(page, 20)
    await page.getByTestId("season-dialog-name").fill("Overlapping")
    await page.getByTestId("season-dialog-save").click()

    await expect(page.getByTestId("season-dialog-failure")).toHaveText("That overlaps Spring 2025.")
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
    // The island's own class, whole: the dialog is portalled out of the island and restates it.
    await expect(dialog).toHaveClass(/(^|\s)island(\s|$)/)
    await expect(dialog.locator(".v-btn, .v-card, .v-dialog, .v-text-field, .v-overlay")).toHaveCount(0)
  })
})
