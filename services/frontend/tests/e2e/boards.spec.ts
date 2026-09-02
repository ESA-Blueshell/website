import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

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

/** A portrait as the api answers with one, at the widths one is stored at. */
const portrait = (name: string) => ({
  path: `board-portraits/${name}.webp`,
  url: `/files/public/board-portraits/${name}.webp`,
  width: 640,
  height: 640,
  renditions: [160, 320, 640].map((width) => ({
    url: `/files/public/board-portraits/${name}-${width}.webp`,
    width,
  })),
})

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
    // The three cases the rows have to read well with, on one board: a seat with a portrait, a
    // nickname and a blurb; one with a blurb and no portrait; and one with neither.
    members: [
      seat(92, 9, "Viktor Petrov", "Treasurer", {description: "Keeping the books."}),
      seat(91, 9, "Emma Dokter", "Chair", {
        nickname: "Emmz", description: "Chairing the ninth board.", portrait: portrait("emma"),
      }),
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
    await expect(page.getByTestId("board-seat-name-91")).toHaveText('Emma "Emmz" Dokter')
    // Five of the seats in the real history have no nickname, and read as the name alone.
    await expect(page.getByTestId("board-seat-name-92")).toHaveText("Viktor Petrov")
    await expect(page.getByTestId("board-seat-role-91")).toHaveText("Chair")
    await expect(seats).toContainText("Chairing the ninth board.")
  })

  test("shows a seat's initials where nobody has a portrait of them", async ({page}) => {
    await page.setViewportSize({width: 1280, height: 1000})
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    // Twenty-six of the forty-six seats in the history have no portrait, so this is the normal
    // case rather than the broken one: the initials of the person, never of the nickname.
    await expect(page.getByTestId("board-seat-portrait-91")).toBeVisible()
    await expect(page.getByTestId("board-seat-monogram-92")).toHaveText("VP")
    await expect(page.getByTestId("board-seat-monogram-93")).toHaveText("RK")

    // And the column stays a column: the plate is the same box either way.
    const plates = await Promise.all([
      page.getByTestId("board-seat-portrait-91").boundingBox(),
      page.getByTestId("board-seat-monogram-92").boundingBox(),
      page.getByTestId("board-seat-monogram-93").boundingBox(),
    ])
    for (const plate of plates.slice(1)) {
      expect(plate!.x).toBeCloseTo(plates[0]!.x, 0)
      expect(plate!.width).toBeCloseTo(plates[0]!.width, 0)
    }
  })

  test("asks for a copy of a portrait the size of the plate it is drawn on", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    const plate = page.getByTestId("board-seat-portrait-91")
    await expect(plate).toHaveAttribute("srcset", /emma-160\.webp 160w/)
    // A plate is 88 css pixels at the most, so the 640 master is never what a row needs.
    const fetched = await plate.evaluate((img: HTMLImageElement) => img.currentSrc)
    expect(fetched, "the copy a plate fetched").toMatch(/emma-\d+\.webp$/)
  })

  test("offers an expansion only where something was written about the seat", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    await expect(page.getByTestId("board-seat-chevron-91")).toBeVisible()
    await expect(page.getByTestId("board-seat-chevron-92")).toBeVisible()
    // Nobody wrote anything about the third seat, so it neither offers to open nor can be.
    await expect(page.getByTestId("board-seat-chevron-93")).toHaveCount(0)
    await expect(page.getByTestId("board-seat-blurb-93")).toHaveCount(0)
    await expect(page.getByTestId("board-seat-93").getByRole("button")).toHaveCount(0)

    await page.getByTestId("board-seat-93").click()
    await expect(page.getByTestId("board-seat-blurb-91")).toBeVisible()
  })

  test("opens the chair's seat when a board first appears, and one seat at a time", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    // A reader meets an open seat rather than a stack of shut ones and no clue that any open.
    await expect(page.getByTestId("board-seat-blurb-91")).toBeVisible()
    await expect(page.getByTestId("board-seat-blurb-92")).toBeHidden()

    await page.getByTestId("board-seat-92").getByRole("button").click()

    await expect(page.getByTestId("board-seat-blurb-92")).toBeVisible()
    await expect(page.getByTestId("board-seat-blurb-91")).toBeHidden()

    // The gesture that opened a seat shuts it again, so nothing is ever stuck open.
    await page.getByTestId("board-seat-92").getByRole("button").click()
    await expect(page.getByTestId("board-seat-blurb-92")).toBeHidden()
  })

  test("opens nothing on a board where nobody wrote anything about anybody", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board?board=7")

    // A whole board of the real history is like this. Its seats still read as seats.
    await expect(page.getByTestId("board-seat-name-71")).toHaveText("Thijs Lieverse")
    await expect(page.getByTestId("board-seat-role-71")).toHaveText("Chairman")
    await expect(page.getByTestId("board-seat-chevron-71")).toHaveCount(0)
    await expect(page.getByTestId("board-seat-blurb-71")).toHaveCount(0)
  })

  test("reads a seat as a row: the plate, then the name and the role, then the chevron", async ({page}) => {
    await page.setViewportSize({width: 1400, height: 1000})
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    const plate = (await page.getByTestId("board-seat-portrait-91").boundingBox())!
    const name = (await page.getByTestId("board-seat-name-91").boundingBox())!
    const role = (await page.getByTestId("board-seat-role-91").boundingBox())!
    const chevron = (await page.getByTestId("board-seat-chevron-91").boundingBox())!

    expect(plate.x + plate.width).toBeLessThanOrEqual(name.x + 1)
    expect(name.x + name.width).toBeLessThanOrEqual(chevron.x + 1)
    // The role qualifies the name, so it sits under it rather than beside the plate.
    expect(role.x).toBeCloseTo(name.x, 0)
    expect(role.y).toBeGreaterThan(name.y)
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

  test("keeps a seat a row on a phone, and opens it there", async ({page}) => {
    await page.setViewportSize({width: 390, height: 900})
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    const row = (await page.getByTestId("board-seat-91").boundingBox())!
    const plate = (await page.getByTestId("board-seat-portrait-91").boundingBox())!
    const name = (await page.getByTestId("board-seat-name-91").boundingBox())!

    // A row rather than a stack even here: the plate is small enough to keep the name beside it,
    // and nothing runs off the side of the phone.
    expect(plate.x + plate.width).toBeLessThanOrEqual(name.x + 1)
    expect(row.width).toBeLessThanOrEqual(390)
    expect(name.x + name.width).toBeLessThanOrEqual(390)

    const blurb = (await page.getByTestId("board-seat-blurb-91").boundingBox())!
    expect(blurb.width).toBeLessThanOrEqual(390)

    await page.getByTestId("board-seat-92").getByRole("button").click()
    await expect(page.getByTestId("board-seat-blurb-92")).toBeVisible()
    await expect(page.getByTestId("board-seat-blurb-91")).toBeHidden()
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

/**
 * What the management board editor used to guarantee, asserted where each of those things now
 * happens. The editor is gone; none of what it covered went with it.
 *
 * Its own two guarantees — that the address is a page, and that the navigation offers it — are
 * the ones that had to invert, so they are asserted here rather than deleted.
 */
test.describe("what the management editor used to do, where it happens now", () => {
  test("its address answers nothing at all", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/management/boards")

    // No route claims it any more, so the catch-all does: a board is edited on /board and the
    // management address is not an address.
    await expect(page.getByTestId("not-found")).toBeVisible()
  })

  test("the navigation offers it to nobody, board member or visitor", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/")
    await expect(page.locator("a[href='/management/boards']")).toHaveCount(0)

    // It was a management link, so the reader who used to be offered it is the one to check.
    await loginAsBoard(page.context())
    await page.goto("/")
    await page.getByTestId("nav-management").click()

    // The emails entry beside it says the menu opened for this reader, so the absence is the
    // entry's own rather than a menu that never appeared.
    await expect(page.locator("a[href='/management/emails']").first()).toBeVisible()
    await expect(page.locator("a[href='/management/boards']")).toHaveCount(0)
  })
})
