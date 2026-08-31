import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin} from "./mocks"

/** A seat as the api reports one, with a photograph, so the page has sides to alternate. */
const seatWithPhoto = (id: number, name: string, image: string) => ({
  id, boardId: 9, userId: null, role: "Chair", name,
  description: "A blurb.", image,
  startDate: "2025-09-01", endDate: "2026-08-31", version: 0,
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
})

const boardOfFour = [{
  id: 9, name: "9th Board", candidate: "9th Board",
  startDate: "2025-09-01", endDate: "2026-08-31",
  image: "board9/board9.jpg", version: 0,
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
  members: [
    seatWithPhoto(91, "Emma Dokter", "board9/Emma.jpg"),
    seatWithPhoto(92, "Viktor Petrov", "board9/Viktor.jpg"),
    seatWithPhoto(93, "Boris Boersma", "board9/Boris.jpg"),
    seatWithPhoto(94, "Sylwia Nowak", "board9/Sylwia.jpg"),
  ],
}]

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

  // The row used to place its columns with Vuetify's `order-md-*`. Tailwind generates
  // `.order-1`/`.order-2` from anything it scans into a cascade layer that beats those, so
  // every photograph sat on the left and the page read as one column of pictures.
  test("sits each seat's photograph on the side opposite the one before it", async ({page}) => {
    await page.setViewportSize({width: 1400, height: 1000})
    await installApiMocks(page, {boards: boardOfFour})

    await page.goto("/board")

    const sitting = page.getByTestId("board-9")
    await expect(sitting.getByTestId("board-seat-photo")).toHaveCount(4)

    const sides: string[] = []
    for (let seat = 0; seat < 4; seat++) {
      const photo = (await sitting.getByTestId("board-seat-photo").nth(seat).boundingBox())!
      const blurb = (await sitting.getByTestId("board-seat-blurb").nth(seat).boundingBox())!
      sides.push(photo.x < blurb.x ? "left" : "right")
    }

    expect(sides).toEqual(["left", "right", "left", "right"])
  })

  test("stacks a seat's photograph over its blurb on a phone", async ({page}) => {
    await page.setViewportSize({width: 700, height: 1000})
    await installApiMocks(page, {boards: boardOfFour})

    await page.goto("/board")

    const sitting = page.getByTestId("board-9")
    const photo = (await sitting.getByTestId("board-seat-photo").first().boundingBox())!
    const blurb = (await sitting.getByTestId("board-seat-blurb").first().boundingBox())!

    expect(photo.x).toEqual(blurb.x)
    expect(photo.y).toBeLessThan(blurb.y)
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

    // Same reason: the dialog's fields exist before it is shown.
    await expect(page.getByTestId("board-seat-name")).toBeVisible()
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

    // The menu's items are mounted before the menu is shown, so waiting for the
    // one being clicked to actually be on screen is what says the menu is open.
    // Clicking on its presence alone lands on nothing and the request never comes.
    const detach = page.getByTestId("board-seat-unlink-91")
    await expect(detach).toBeVisible()

    const detached = page.waitForRequest(
      (request) => request.method() === "PUT" && /\/members\/\d+\/member$/.test(new URL(request.url()).pathname),
    )
    // Detaching is one click on the row's own menu, with no picker in the way.
    await detach.click()

    const request = await detached
    expect(JSON.parse(request.postData() ?? "{}").userId).toBeUndefined()
  })
})
