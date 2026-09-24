import {expect, test} from "./test"
import {installApiMocks, loginAsBoard, loginAsMember} from "./mocks"

test.describe("the committees pages", () => {
  test("the index runs the committees on the reel, the archived ones past it, and every listed one below", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/committees")

    await expect(page.getByTestId("committees-reel").locator('[data-testid^="committees-rail-"]')).toHaveCount(2)
    await expect(page.getByTestId("committees-olden")).toContainText("The committees we used to have")
    await expect(page.getByTestId("committees-olden-tile-903")).toHaveAttribute("href", "/committees/oldcie")
    await expect(page.getByTestId("committees-every-cell-903")).toContainText("Archived")
    await expect(page.getByTestId("committees-every-cell-900")).toContainText("Chess")
    await expect(page.getByTestId("committees-every-cell-902")).toHaveCount(0)
    await expect(page.getByTestId("committees-add")).toHaveCount(0)
  })

  test("a committee's page names its members by Discord and its games, and a visitor gets no buttons", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/committees")
    await page.getByTestId("committees-every-cell-900").click()

    await expect(page).toHaveURL(/\/committees\/events-committee$/)
    await expect(page.getByTestId("committee-head")).toContainText("Events Committee")
    await expect(page.getByTestId("committee-seat-0")).toContainText("@nelly")
    await expect(page.getByTestId("committee-games")).toContainText("Chess")
    await expect(page.getByTestId("committee-edit")).toHaveCount(0)

    await page.goto("/committees/oldcie")
    await expect(page.getByTestId("committee-archived")).toHaveText("Archived")
    await page.goto("/committees/nobody")
    await expect(page.getByTestId("not-found")).toBeVisible()
  })

  test("a committee's own member edits its description, and the board's fields stay read-only", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsMember(context)
    await page.goto("/committees/events-committee")

    await expect(page.getByTestId("committee-add-event")).toBeVisible()
    await page.getByTestId("committee-edit").click()
    await expect(page.getByTestId("committee-dialog-fixed")).toContainText("The board changes these.")
    await expect(page.getByTestId("committee-dialog-name")).toHaveCount(0)
    const saved = page.waitForRequest(request => request.method() === "PUT" && /\/committees\/900\/page$/u.test(new URL(request.url()).pathname))
    await page.getByTestId("committee-dialog-description").fill("We run the events, and the pub quiz.")
    await page.getByTestId("committee-dialog-save").click()

    expect((await saved).postDataJSON()).toMatchObject({description: "We run the events, and the pub quiz.", gameCodes: ["CHESS"]})
    await expect(page.getByTestId("committee-head")).toContainText("and the pub quiz")
  })

  test("the board archives a committee from its cell, and it joins the committees we used to have", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)
    await page.goto("/committees")

    await page.getByTestId("committees-every-archive-901").click()
    await page.getByTestId("confirm-go").click()

    await expect(page.getByTestId("committees-every-cell-901")).toContainText("Archived")
    await expect(page.getByTestId("committees-olden-tile-901")).toHaveCount(1)
  })

  test("the board names a game's organisers from the game's own form", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)
    await page.goto("/casual/chess")
    await expect(page.getByTestId("casual-game-organisers")).toContainText("Events Committee")

    await page.getByTestId("casual-game-edit").click()
    await page.getByTestId("casual-game-dialog-organisers-picker-search").click()
    await page.getByTestId("casual-game-dialog-organisers-picker-901").click()
    await page.getByTestId("casual-game-dialog-save").click()

    await expect(page.getByTestId("casual-game-organisers")).toContainText("LanCie")
  })
})
