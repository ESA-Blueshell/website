import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin} from "./mocks"

/** A photograph as the api answers with one, at the widths a board photo is stored at. */
const photo = (name: string) => ({
  path: `board-photos/${name}.webp`,
  url: `/files/public/board-photos/${name}.webp`,
  width: 2560,
  height: 1440,
  renditions: [320, 640, 960, 1280, 1920].map((width) => ({
    url: `/files/public/board-photos/${name}-${width}.webp`,
    width,
  })),
})

/** A seat as the api reports one, with a photograph, so the page has sides to alternate. */
const seatWithPhoto = (id: number, name: string, image: string) => ({
  id, boardId: 9, userId: null, role: "Chair", name, nickname: null,
  description: "A blurb.", image, portrait: null,
  startDate: "2025-09-01", endDate: "2026-08-31", version: 0,
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
})

const boardOfFour = [{
  id: 9, number: 9, name: "9th Board", candidate: "9th Board",
  cheer: null, accent: null, description: null,
  startDate: "2025-09-01", endDate: "2026-08-31",
  image: "board9/board9.jpg", photo: null, version: 0,
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
  image: null, photo: null, version: 0,
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
  members: [{
    id: 61, boardId: 6, userId: null, role: "Commissioner of Internal Affairs",
    name: "Roos Kruk", nickname: "SkyeWolf", description: null, image: null, portrait: null,
    startDate: "2022-09-01", endDate: "2023-08-31", version: 0,
    createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
  }],
}]

/**
 * A seat, spelled out once so a board can be assembled out of them.
 *
 * Roles rather than positions: the page reads the seniority out of the words the board wrote,
 * so a fixture that gave every seat the same role would never show the ordering at all.
 */
const seat = (id: number, boardId: number, name: string, role: string, over: Record<string, unknown> = {}) => ({
  id, boardId, userId: null, role, name, nickname: null,
  description: null, image: null, portrait: null,
  startDate: "2025-09-01", endDate: "2026-08-31", version: 0,
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
  ...over,
})

const board = (over: Record<string, unknown>) => ({
  id: 1, number: 1, name: null, candidate: "Board", cheer: null, accent: null, description: null,
  startDate: "2017-09-01", endDate: "2018-08-31", image: null, photo: null, version: 0,
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z", members: [],
  ...over,
})

/**
 * The line as the association really has it: a board in office, the years behind it, and a board
 * elected and not yet sitting.
 *
 * The candidate's term opens far enough out that it is one whatever day the suite runs on. The
 * board in office is the ninth, whose term is open until the autumn — a fixture whose terms all
 * closed would be answered by the newest board that has sat, which is the same answer for the
 * wrong reason.
 */
const wholeHistory = [
  board({
    id: 10, number: 10, name: "Rainbow road", startDate: "2099-09-01", endDate: "2100-08-31",
    // Elected and not sitting: no photograph and nobody seated yet.
    members: [],
  }),
  board({
    id: 9, number: 9, name: "Eeveelutions", cheer: "RNG, Be With Me!",
    startDate: "2025-09-01", endDate: null, photo: photo("board9"),
    members: [
      seat(92, 9, "Viktor Petrov", "Treasurer"),
      seat(91, 9, "Emma Dokter", "Chair", {nickname: "Emmz", description: "Chairing the ninth board."}),
      seat(93, 9, "Roos Kruk", "Commissioner of Internal Affairs"),
    ],
  }),
  board({
    id: 7, number: 7, name: "Overcooked", cheer: "Krijg de tering!",
    startDate: "2023-09-01", endDate: "2024-08-31", photo: photo("board7"),
    members: [seat(71, 7, "Thijs Lieverse", "Chairman")],
  }),
  board({
    id: 4, number: 4, name: null, startDate: "2020-09-01", endDate: "2021-08-31",
    members: [seat(41, 4, "Anne Schrader", "Chairman")],
  }),
]

test.describe("board page", () => {
  test("opens on the board in office, and says which board that is", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    // The url named no board, so the page answered with the one running the association — not
    // the newest board recorded, which is a candidate and has nobody on it.
    await expect(page.getByTestId("board-eyebrow")).toHaveText("BOARD IX · 2025-2026")
    await expect(page.getByTestId("board-name")).toHaveText("Eeveelutions")
    await expect(page).toHaveURL(/\/board$/)
  })

  test("carries every board on the timeline, named and dated", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    const strip = page.getByTestId("board-timeline")
    await expect(strip).toBeVisible()

    // Every board, whether or not it has a photograph, seats or a name of its own.
    for (const number of [4, 7, 9, 10]) {
      await expect(page.getByTestId(`board-node-${number}`)).toHaveCount(1)
    }

    await expect(page.getByTestId("board-node-9")).toContainText("Eeveelutions")
    await expect(page.getByTestId("board-node-9")).toContainText("2025-2026")
    // A board that never recorded a name is still named, from its number, in Roman numerals.
    await expect(page.getByTestId("board-node-4")).toContainText("Board IV")
    await expect(page.getByTestId("board-node-4")).toContainText("2020-2021")
  })

  test("marks the board in office, and the board that has not taken office yet", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    await expect(page.getByTestId("board-mark-9")).toHaveText("In office")
    await expect(page.getByTestId("board-mark-10")).toHaveText("Candidate")
    // Every other board is another year of the history and is marked as nothing.
    await expect(page.getByTestId("board-mark-7")).toHaveCount(0)
    await expect(page.getByTestId("board-mark-4")).toHaveCount(0)
  })

  test("never opens on a candidate board, however new it is", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    // Reachable — a board has to be reachable before it can be worked on — and never the one a
    // visitor arrives on: the association is still run by the board in office.
    await expect(page.getByTestId("board-node-10")).toHaveCount(1)
    await expect(page.getByTestId("board-eyebrow")).not.toHaveText(/BOARD X\b/)
    await expect(page.getByTestId("board-numeral")).toHaveText("IX")
  })

  test("shows a candidate board when it is chosen, seats or no seats", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")
    await page.getByTestId("board-node-10").click()

    await expect(page).toHaveURL(/\?board=10$/)
    await expect(page.getByTestId("board-eyebrow")).toHaveText("BOARD X · 2099-2100")
    await expect(page.getByTestId("board-no-seats")).toBeVisible()
  })

  test("puts the board being read in the url, and the back button returns to the one before", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")
    await expect(page.getByTestId("board-eyebrow")).toHaveText("BOARD IX · 2025-2026")

    await page.getByTestId("board-node-7").click()

    await expect(page).toHaveURL(/\?board=7$/)
    await expect(page.getByTestId("board-name")).toHaveText("Overcooked")
    await expect(page.getByTestId("board-numeral")).toHaveText("VII")

    await page.getByTestId("board-node-4").click()
    await expect(page).toHaveURL(/\?board=4$/)

    // Browsing the history behaves like browsing.
    await page.goBack()
    await expect(page).toHaveURL(/\?board=7$/)
    await expect(page.getByTestId("board-name")).toHaveText("Overcooked")

    await page.goBack()
    await expect(page).toHaveURL(/\/board$/)
    await expect(page.getByTestId("board-eyebrow")).toHaveText("BOARD IX · 2025-2026")
  })

  test("opens on the board a link names, rather than on the one in office", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board?board=4")

    await expect(page.getByTestId("board-numeral")).toHaveText("IV")
    await expect(page.getByTestId("board-eyebrow")).toHaveText("BOARD IV · 2020-2021")
  })

  test("draws the board photograph as a band, and asks for a copy that fits the screen", async ({page}) => {
    await page.setViewportSize({width: 390, height: 900})
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    const banner = page.getByTestId("board-photo")
    await expect(banner).toBeVisible()
    // The widths the api published, so a phone has something narrower than the master to pick.
    await expect(banner).toHaveAttribute("srcset", /board9-320\.webp 320w/)

    // The band is as wide as the window and covers its box, so what it promises the browser is
    // measured rather than guessed — and on a phone it is nowhere near the 2560 master.
    await expect.poll(() => banner.getAttribute("sizes")).toMatch(/^\d+px$/)
    const asked = Number((await banner.getAttribute("sizes"))!.replace("px", ""))
    expect(asked).toBeLessThanOrEqual(960)

    // One of the stored copies rather than the master. Which one depends on the screen's own
    // density, and a phone with three device pixels to a css one is right to want a wider copy —
    // what must never happen is 2560 pixels of photograph arriving to be drawn across 390.
    const fetched = await banner.evaluate((img: HTMLImageElement) => img.currentSrc)
    expect(fetched, "the copy a phone fetched").toMatch(/board9-\d+\.webp$/)
  })

  test("keeps the band and its height for a board with no photograph", async ({page}) => {
    await page.setViewportSize({width: 1280, height: 900})
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")
    const photographed = (await page.getByTestId("board-band").boundingBox())!

    await page.getByTestId("board-node-4").click()
    await expect(page.getByTestId("board-numeral")).toHaveText("IV")

    // Half the association's history has no photograph, so the band is the shape of the page
    // rather than something a photograph gives it: same height, no image, numeral in the middle.
    const bare = (await page.getByTestId("board-band").boundingBox())!
    expect(bare.height).toBeCloseTo(photographed.height, 0)
    await expect(page.getByTestId("board-photo")).toHaveCount(0)
    await expect(page.getByTestId("board-numeral")).toBeVisible()
  })

  test("reads a board's cheer distinctly from what it wrote about itself", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board?board=7")

    await expect(page.getByTestId("board-cheer")).toHaveText("Krijg de tering!")
    // Shouted rather than said: the cheer is set in the display face, the prose is not.
    const face = await page.getByTestId("board-cheer").evaluate(
      (node) => getComputedStyle(node).fontFamily,
    )
    expect(face).toContain("Fugaz One")
  })

  test("renders nothing at all where a board has no name, cheer or description", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board?board=4")

    // Only three of ten boards have a cheer and none has a description, so the blank case is
    // the normal one: it renders nothing rather than a placeholder saying so.
    await expect(page.getByTestId("board-cheer")).toHaveCount(0)
    await expect(page.getByTestId("board-description")).toHaveCount(0)
    // The eyebrow above has just said BOARD IV, so a heading repeating it is the placeholder.
    await expect(page.getByTestId("board-name")).toHaveCount(0)
    await expect(page.getByTestId("board-eyebrow")).toHaveText("BOARD IV · 2020-2021")
  })

  test("reads the seats chair first, with each nickname back in the name it sits inside", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    const seats = page.getByTestId("board-seats")
    // Chair, treasurer, then the commissioners: the order the association thinks in, out of the
    // words the board wrote rather than out of the order the api answered in.
    await expect(seats).toContainText(/Emma[\s\S]*Viktor[\s\S]*Roos/)
    await expect(seats).toContainText('Emma "Emmz" Dokter')
    await expect(seats).toContainText("Chairing the ninth board.")
  })

  test("names a board with no recorded name from its number", async ({page}) => {
    await installApiMocks(page, {boards: namelessBoard})

    await page.goto("/board")

    // A board's name may never have been written down, and no board reads as blank.
    await expect(page.getByTestId("board-eyebrow")).toHaveText("BOARD VI · 2022-2023")
    await expect(page.getByTestId("board-numeral")).toHaveText("VI")
  })

  test("puts a seat's nickname back between the name it sits inside", async ({page}) => {
    await installApiMocks(page, {boards: namelessBoard})

    await page.goto("/board")

    // The name and the nickname are two fields now. A reader still sees the one string.
    await expect(page.getByTestId("board-seats")).toContainText('Roos "SkyeWolf" Kruk')
  })

  test("says so where no boards are recorded at all", async ({page}) => {
    await installApiMocks(page, {boards: []})

    await page.goto("/board")

    await expect(page.getByTestId("board-empty")).toBeVisible()
    await expect(page.getByTestId("board-timeline")).toHaveCount(0)
  })

  test("ends on the invitation to stand for a board", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    // The history is an invitation rather than a museum, and the band that says so is the
    // island's own — the same one the esports pages end on.
    await expect(page.getByTestId("board-join")).toBeVisible()
    await expect(page.getByTestId("board-join-member")).toHaveAttribute("href", "/membership")
  })

  // The row used to place its columns with Vuetify's `order-md-*`. Tailwind generates
  // `.order-1`/`.order-2` from anything it scans into a cascade layer that beats those, so
  // every photograph sat on the left and the page read as one column of pictures.
  test("sits each seat's photograph on the side opposite the one before it", async ({page}) => {
    await page.setViewportSize({width: 1400, height: 1000})
    await installApiMocks(page, {boards: boardOfFour})

    await page.goto("/board")

    const seats = page.getByTestId("board-seats")
    await expect(seats.getByTestId("board-seat-photo")).toHaveCount(4)

    const sides: string[] = []
    for (const index of [0, 1, 2, 3]) {
      const photograph = (await seats.getByTestId("board-seat-photo").nth(index).boundingBox())!
      const blurb = (await seats.getByTestId("board-seat-blurb").nth(index).boundingBox())!
      sides.push(photograph.x < blurb.x ? "left" : "right")
    }

    expect(sides).toEqual(["left", "right", "left", "right"])
  })

  test("stacks a seat's photograph over its blurb on a phone", async ({page}) => {
    await page.setViewportSize({width: 700, height: 1000})
    await installApiMocks(page, {boards: boardOfFour})

    await page.goto("/board")

    const seats = page.getByTestId("board-seats")
    const photograph = (await seats.getByTestId("board-seat-photo").first().boundingBox())!
    const blurb = (await seats.getByTestId("board-seat-blurb").first().boundingBox())!

    expect(photograph.x).toEqual(blurb.x)
    expect(photograph.y).toBeLessThan(blurb.y)
  })

  test("stacks the timeline, the band and the identity on a phone", async ({page}) => {
    await page.setViewportSize({width: 390, height: 900})
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    const strip = (await page.getByTestId("board-timeline").boundingBox())!
    const band = (await page.getByTestId("board-band").boundingBox())!
    const identity = (await page.getByTestId("board-identity").boundingBox())!

    // One above the next, none of them wider than the phone, and nothing scrolling sideways.
    expect(strip.y + strip.height).toBeLessThanOrEqual(band.y + 1)
    expect(band.y + band.height).toBeLessThanOrEqual(identity.y + 1)
    for (const box of [strip, band, identity]) expect(box.width).toBeLessThanOrEqual(390)
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
