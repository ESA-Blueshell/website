import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin} from "./mocks"

test.describe("board page", () => {
  test("shows the board in office, with its seats and their blurbs", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/board")

    const sitting = page.getByTestId("board-9")
    await expect(sitting).toContainText("9th Board")
    await expect(sitting).toContainText("Emma Dokter")
    await expect(sitting).toContainText("Chair")
    await expect(sitting).toContainText("Chairing the ninth board.")
  })

  test("names a seat nobody is linked to, the same as any other", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/board")

    // Viktor's seat carries no account; the page cannot tell, and neither should a reader.
    await expect(page.getByTestId("board-9")).toContainText("Viktor Petrov")
  })

  test("keeps an older board behind its own heading until it is opened", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/board")

    const older = page.getByTestId("board-1")
    await expect(older).toContainText("1st Board")
    await expect(older.getByText("Thijs Lieverse")).toBeHidden()

    await page.getByTestId("board-toggle-1").click()

    await expect(older.getByText("Thijs Lieverse")).toBeVisible()
  })
})

test.describe("board manager", () => {
  test("lists every board with how many seats it holds", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto("/management/boards")

    await expect(page.getByTestId("board-row-9")).toContainText("9th Board")
    await expect(page.getByTestId("board-row-9")).toContainText("2")
    await expect(page.getByTestId("board-row-1")).toContainText("1st Board")
  })

  test("shows a board's seats, and marks the ones nobody is linked to", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto("/management/boards")
    await page.getByTestId("board-row-9").click()

    const seats = page.getByTestId("board-seat-table")
    await expect(seats).toContainText("Emma Dokter")
    await expect(seats).toContainText("Viktor Petrov")
    await expect(page.getByTestId("board-seat-row-92")).toContainText("Unlinked")
  })

  test("seats somebody who has no account", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto("/management/boards")
    await page.getByTestId("board-row-9").click()
    await page.getByTestId("board-add-seat").click()

    await page.getByTestId("board-seat-name").locator("input").fill("Thijs Lieverse")
    await page.getByTestId("board-seat-role").locator("input").fill("Chairman")

    const created = page.waitForRequest(
      (request) => request.method() === "POST" && /\/boards\/\d+\/members$/.test(new URL(request.url()).pathname),
    )
    await page.getByTestId("board-seat-save").click()

    const request = await created
    // No member is sent: the seat stands under the name it was given.
    expect(JSON.parse(request.postData() ?? "{}")).toMatchObject({
      displayName: "Thijs Lieverse",
      role: "Chairman",
    })
    expect(JSON.parse(request.postData() ?? "{}").userId).toBeUndefined()
  })

  test("detaches a member from a seat", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto("/management/boards")
    await page.getByTestId("board-row-9").click()
    await page.getByTestId("board-seat-menu-91").click()

    const detached = page.waitForRequest(
      (request) => request.method() === "PUT" && /\/members\/\d+\/member$/.test(new URL(request.url()).pathname),
    )
    // Detaching is one click on the row's own menu, with no picker in the way.
    await page.getByTestId("board-seat-unlink-91").click()

    const request = await detached
    expect(JSON.parse(request.postData() ?? "{}").userId).toBeUndefined()
  })
})
