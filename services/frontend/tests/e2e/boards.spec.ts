import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin} from "./mocks"

/** A seat as the api reports one, with a photograph, so the page has sides to alternate. */
const seatWithPhoto = (id: number, name: string, image: string) => ({
  id, boardId: 9, userId: null, role: "Chair", name, nickname: null,
  description: "A blurb.", image,
  startDate: "2025-09-01", endDate: "2026-08-31", version: 0,
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
})

const boardOfFour = [{
  id: 9, number: 9, name: "9th Board", candidate: "9th Board",
  cheer: null, accent: null, description: null,
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

/** A board with no name recorded and a seat whose nickname is its own field. */
const namelessBoard = [{
  id: 6, number: 6, name: null, candidate: "Board 6",
  cheer: null, accent: null, description: null,
  startDate: "2022-09-01", endDate: "2023-08-31",
  image: null, version: 0,
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
  members: [{
    id: 61, boardId: 6, userId: null, role: "Commissioner of Internal Affairs",
    name: "Roos Kruk", nickname: "SkyeWolf", description: null, image: null,
    startDate: "2022-09-01", endDate: "2023-08-31", version: 0,
    createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
  }],
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

  test("names a board with no recorded name from its number", async ({page}) => {
    await installApiMocks(page, {boards: namelessBoard})

    await page.goto("/board")

    // A board's name may never have been written down, and no board reads as blank.
    await expect(page.getByTestId("board-6")).toContainText("Board 6")
  })

  test("puts a seat's nickname back between the name it sits inside", async ({page}) => {
    await installApiMocks(page, {boards: namelessBoard})

    await page.goto("/board")

    // The name and the nickname are two fields now. A reader still sees the one string.
    await expect(page.getByTestId("board-6")).toContainText('Roos "SkyeWolf" Kruk')
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
  test("lists every board with its number and how many seats it holds", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto("/management/boards")

    await expect(page.getByTestId("board-row-9")).toContainText("9th Board")
    await expect(page.getByTestId("board-row-9")).toContainText("2")
    await expect(page.getByTestId("board-row-1")).toContainText("1st Board")
  })

  test("sends a board's number, cheer and colour when it is corrected", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto("/management/boards")
    await page.getByTestId("board-row-9").click()
    await page.getByTestId("board-menu-9").click()
    await page.getByTestId("board-edit-9").click()

    await expect(page.getByTestId("board-cheer")).toBeVisible()
    await page.getByTestId("board-accent").locator("input").fill("#7b2ff7")

    const saved = page.waitForRequest(
      (request) => request.method() === "PUT" && /\/boards\/9$/.test(new URL(request.url()).pathname),
    )
    await page.getByTestId("board-save").click()

    const body = JSON.parse((await saved).postData() ?? "{}")
    expect(body).toMatchObject({number: 9, cheer: "RNG, Be With Me!", accent: "#7b2ff7"})
  })

  test("sends a seat's nickname beside its name", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto("/management/boards")
    await page.getByTestId("board-row-9").click()
    await page.getByTestId("board-add-seat").click()

    await expect(page.getByTestId("board-seat-nickname")).toBeVisible()
    await page.getByTestId("board-seat-name").locator("input").fill("Roos Kruk")
    await page.getByTestId("board-seat-nickname").locator("input").fill("SkyeWolf")
    await page.getByTestId("board-seat-role").locator("input").fill("Commissioner of Internal Affairs")

    const created = page.waitForRequest(
      (request) => request.method() === "POST" && /\/boards\/\d+\/members$/.test(new URL(request.url()).pathname),
    )
    await page.getByTestId("board-seat-save").click()

    expect(JSON.parse((await created).postData() ?? "{}")).toMatchObject({
      displayName: "Roos Kruk",
      nickname: "SkyeWolf",
    })
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
