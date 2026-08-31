import {expect, test} from "./test"
import {installApiMocks, loginAsBoard, loginAsMember} from "./mocks"

/**
 * Adding a season from the strip itself.
 *
 * Seasons are written down twice a year and always at the end, and the strip is where their
 * absence is noticed — so that is where the plus lives.
 */
const GAME_PAGE = "/esports/valorant"

test.describe("adding a season from the timeline", () => {
  test("the strip ends in a plus for somebody who may edit", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await expect(page.getByTestId("esports-season-add")).toBeVisible()
  })

  test("somebody who may not edit is offered no plus", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())
    await page.goto(GAME_PAGE)

    await expect(page.getByTestId("esports-season-timeline")).toBeVisible()
    await expect(page.getByTestId("esports-season-add")).toHaveCount(0)
  })

  test("the plus opens the dialog with nothing filled in", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("esports-season-add").click()

    await expect(page.getByTestId("season-dialog")).toBeVisible()
    await expect(page.getByTestId("season-dialog")).toContainText("Add season")
    await expect(page.getByTestId("season-dialog-name")).toHaveValue("")
    await expect(page.getByTestId("season-dialog-start")).toHaveValue("")
    await expect(page.getByTestId("season-dialog-end")).toHaveValue("")
  })

  test("a saved season joins the strip in date order rather than at the end", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("esports-season-add").click()
    // Earlier than both seasons the page already knows, so its place is the front of the line.
    await page.getByTestId("season-dialog-name").fill("Spring 2023/24")
    await page.getByTestId("season-dialog-start").fill("2024-02-01")
    await page.getByTestId("season-dialog-end").fill("2024-08-31")
    await page.getByTestId("season-dialog-save").click()

    await expect(page.getByTestId("season-dialog")).toBeHidden()
    const added = page.getByTestId("esports-season-node-41")
    await expect(added).toBeVisible()

    // Oldest first along the strip, so the new season sits left of the two that existed.
    const order = await page.getByTestId("esports-season-timeline")
      .locator('[data-testid^="esports-season-node-"]')
      .evaluateAll(nodes => nodes.map(node => node.getAttribute("data-testid")))
    expect(order).toEqual(["esports-season-node-41", "esports-season-node-19", "esports-season-node-20"])
  })

  test("the season that was added is the shown one", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("esports-season-add").click()
    await page.getByTestId("season-dialog-name").fill("Spring 2026/27")
    await page.getByTestId("season-dialog-start").fill("2027-02-01")
    await page.getByTestId("season-dialog-end").fill("2027-08-31")
    await page.getByTestId("season-dialog-save").click()

    await expect(page.getByTestId("esports-season-node-41")).toHaveAttribute("aria-current", "true")
  })

  test("dates that overlap a season already written down are refused, and the form keeps them", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.route("**/esports/seasons", async (route) => {
      if (route.request().method() !== "POST") return route.fallback()
      await route.fulfill({
        status: 400,
        contentType: "application/json",
        body: JSON.stringify({status: 400, title: "Bad Request", detail: "Those dates overlap Autumn 2025/26"}),
      })
    })
    await page.goto(GAME_PAGE)

    await page.getByTestId("esports-season-add").click()
    await page.getByTestId("season-dialog-name").fill("Clashing")
    await page.getByTestId("season-dialog-start").fill("2025-11-01")
    await page.getByTestId("season-dialog-end").fill("2026-03-31")
    await page.getByTestId("season-dialog-save").click()

    await expect(page.getByTestId("season-dialog-failure")).toHaveText("Those dates overlap Autumn 2025/26")
    await expect(page.getByTestId("season-dialog")).toBeVisible()
    await expect(page.getByTestId("season-dialog-name")).toHaveValue("Clashing")
    await expect(page.getByTestId("season-dialog-start")).toHaveValue("2025-11-01")
  })

  test("the plus can be reached and taken up from the keyboard", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    const plus = page.getByTestId("esports-season-add")
    await plus.focus()
    await expect(plus).toBeFocused()
    await page.keyboard.press("Enter")

    await expect(page.getByTestId("season-dialog")).toBeVisible()
  })
})
