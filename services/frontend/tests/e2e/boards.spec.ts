import type {Locator} from "@playwright/test"
import {expect, test} from "./test"
import {dragBand} from "./bandSwipe"
import {installApiMocks, loginAsBoard} from "./mocks"

/**
 * Presses a member, having first put it where pressing it will not scroll the page.
 *
 * Stacked, the scroll decides which member is open, and a scroll releases a tap by design: the
 * choice stands until the visitor scrolls, at which point the scroll is their intent again.
 * Playwright scrolls an element into view as part of clicking it, and that scroll event is
 * delivered asynchronously, so a press can be undone by its own scroll arriving after it.
 *
 * Scrolling first and pressing second is the order a finger makes, and it is deterministic.
 */
async function press(slice: Locator): Promise<void> {
  await slice.scrollIntoViewIfNeeded()
  await expect(slice).toBeInViewport()
  await slice.getByRole("button").click()
}

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

/** A board with no name recorded and a member whose nickname is its own field. */
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
 * A member, spelled out once so a board can be assembled out of them.
 *
 * Roles rather than positions: the page reads the seniority out of the words the board wrote,
 * so a fixture that gave every member the same role would never show the ordering at all.
 */
const member = (id: number, boardId: number, name: string, role: string, over: Record<string, unknown> = {}) => ({
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
 * board in office is the ninth, whose term is open until the autumn: a fixture whose terms all
 * closed would be answered by the newest board that has sat, which is the same answer for the
 * wrong reason.
 */
const wholeHistory = [
  board({
    id: 10, number: 10, name: "Rainbow road", startDate: "2099-09-01", endDate: "2100-08-31",
    // Elected and not sitting: no photograph and nobody recorded yet.
    members: [],
  }),
  board({
    id: 9, number: 9, name: "Eeveelutions", cheer: "RNG, Be With Me!",
    startDate: "2025-09-01", endDate: null, photo: photo("board9"),
    // The three cases the rows have to read well with, on one board: a member with a portrait, a
    // nickname and a blurb; one with a blurb and no portrait; and one with neither.
    members: [
      member(92, 9, "Viktor Petrov", "Treasurer", {description: "Keeping the books."}),
      member(91, 9, "Emma Dokter", "Chair", {
        nickname: "Emmz", description: "Chairing the ninth board.", portrait: portrait("emma"),
      }),
      member(93, 9, "Roos Kruk", "Commissioner of Internal Affairs"),
    ],
  }),
  board({
    id: 7, number: 7, name: "Overcooked", cheer: "Krijg de tering!",
    startDate: "2023-09-01", endDate: "2024-08-31", photo: photo("board7"),
    members: [member(71, 7, "Thijs Lieverse", "Chairman")],
  }),
  board({
    id: 4, number: 4, name: null, startDate: "2020-09-01", endDate: "2021-08-31",
    members: [member(41, 4, "Anne Schrader", "Chairman")],
  }),
]

test.describe("board page", () => {
  test("opens on the board in office, and says which board that is", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    // The url named no board, so the page answered with the one running the association, not
    // the newest board recorded, which is a candidate and has nobody on it.
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IX · 2025-2026")
    await expect(page.getByTestId("board-band-name")).toHaveText("Eeveelutions")
    await expect(page).toHaveURL(/\/board$/)
  })

  test("carries every board on the timeline, named and dated", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    const strip = page.getByTestId("board-timeline")
    await expect(strip).toBeVisible()

    // Every board, whether or not it has a photograph, members or a name of its own.
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

    // Reachable (a board has to be reachable before it can be worked on) and never the one a
    // visitor arrives on: the association is still run by the board in office.
    await expect(page.getByTestId("board-node-10")).toHaveCount(1)
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IX · 2025-2026")
  })

  test("shows a candidate board when it is chosen, members or no members", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")
    await page.getByTestId("board-node-10").click()

    await expect(page).toHaveURL(/\?board=10$/)
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD X · 2099-2100")
    await expect(page.getByTestId("board-no-members")).toBeVisible()
  })

  test("puts the board being read in the url, and the back button returns to the one before", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IX · 2025-2026")

    await page.getByTestId("board-node-7").click()

    await expect(page).toHaveURL(/\?board=7$/)
    await expect(page.getByTestId("board-band-name")).toHaveText("Overcooked")
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD VII · 2023-2024")

    await page.getByTestId("board-node-4").click()
    await expect(page).toHaveURL(/\?board=4$/)

    // Browsing the history behaves like browsing.
    await page.goBack()
    await expect(page).toHaveURL(/\?board=7$/)
    await expect(page.getByTestId("board-band-name")).toHaveText("Overcooked")

    await page.goBack()
    await expect(page).toHaveURL(/\/board$/)
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IX · 2025-2026")
  })

  test("opens on the board a link names, rather than on the one in office", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board?board=4")

    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IV · 2020-2021")
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
    // measured rather than guessed, and on a phone it is nowhere near the 2560 master.
    await expect.poll(() => banner.getAttribute("sizes")).toMatch(/^\d+px$/)
    const asked = Number((await banner.getAttribute("sizes"))!.replace("px", ""))
    expect(asked).toBeLessThanOrEqual(960)

    // One of the stored copies rather than the master. Which one depends on the screen's own
    // density, and a phone with three device pixels to a css one is right to want a wider copy,
    // what must never happen is 2560 pixels of photograph arriving to be drawn across 390.
    const fetched = await banner.evaluate((img: HTMLImageElement) => img.currentSrc)
    expect(fetched, "the copy a phone fetched").toMatch(/board9-\d+\.webp$/)
  })

  test("gives a board with no photograph the height of its words, not of a photograph", async ({page}) => {
    await page.setViewportSize({width: 1280, height: 900})
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")
    const photographed = (await page.getByTestId("board-band").boundingBox())!

    await page.getByTestId("board-node-4").click()
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IV · 2020-2021")

    // Half the association's history has no photograph. A hero is a photograph, and a strip
    // held to a photograph's height with nothing in it is a field of colour with a line of
    // writing at the top of it, so what is there is the words and the words decide the height.
    const bare = (await page.getByTestId("board-band").boundingBox())!
    expect(bare.height).toBeLessThan(photographed.height)
    await expect(page.getByTestId("board-photo")).toHaveCount(0)
    await expect(page.getByTestId("board-band-eyebrow")).toBeVisible()
  })

  test("reads a board's cheer distinctly from what it wrote about itself", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board?board=7")

    // Quoted where it is drawn, because it is something a board shouted rather than wrote.
    await expect(page.getByTestId("board-band-cheer")).toContainText("Krijg de tering!")
    // Shouted rather than said: the cheer is set in the display face, the prose is not.
    const face = await page.getByTestId("board-band-cheer").evaluate(
      (node) => getComputedStyle(node).fontFamily,
    )
    expect(face).toContain("Shellhouse One")
  })

  test("renders nothing at all where a board has no name, cheer or description", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board?board=4")

    // Only three of ten boards have a cheer and none has a description, so the blank case is
    // the normal one: it renders nothing rather than a placeholder saying so.
    await expect(page.getByTestId("board-band-cheer")).toHaveCount(0)
    await expect(page.getByTestId("board-band-description")).toHaveCount(0)
    // The eyebrow above has just said BOARD IV, so a heading repeating it is the placeholder.
    await expect(page.getByTestId("board-band-name")).toHaveCount(0)
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IV · 2020-2021")
  })

  test("reads the members chair first, with each nickname back in the name it sits inside", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    const members = page.getByTestId("board-members")
    // Chair, treasurer, then the commissioners: the order the association thinks in, out of the
    // words the board wrote rather than out of the order the api answered in.
    await expect(members).toContainText(/Emma[\s\S]*Viktor[\s\S]*Roos/)
    // The name, the nickname and the role are drawn on the face itself, so they are read
    // through the slice rather than through ids of their own.
    await expect(page.getByTestId("board-member-91")).toContainText('Emma "Emmz" Dokter')
    // Five of the members in the real history have no nickname, and read as the name alone.
    await expect(page.getByTestId("board-member-92")).toContainText("Viktor Petrov")
    await expect(page.getByTestId("board-member-91")).toContainText("Chair")
    await expect(members).toContainText("Chairing the ninth board.")
  })

  test("draws a member with no portrait as a slice of its own, name and role and nothing else", async ({page}) => {
    await page.setViewportSize({width: 1280, height: 1000})
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    // Twenty-six of the forty-six members in the history have no portrait, so this is the normal
    // case rather than the broken one. The one who has a portrait shows it; the ones who do
    // not show no picture at all rather than something standing in for one.
    await expect(page.getByTestId("board-member-91").locator("img")).toHaveCount(1)
    await expect(page.getByTestId("board-member-92").locator("img")).toHaveCount(0)
    await expect(page.getByTestId("board-member-92")).toContainText("Viktor Petrov")
    await expect(page.getByTestId("board-member-93")).toContainText("Roos Kruk")

    // And the band stays a band: every slice is the same height whether it has art or not.
    const boxes = await Promise.all([91, 92, 93].map(
      (id) => page.getByTestId(`board-member-${id}`).boundingBox(),
    ))
    for (const box of boxes.slice(1)) {
      expect(box!.height).toBeCloseTo(boxes[0]!.height, 0)
      expect(box!.y).toBeCloseTo(boxes[0]!.y, 0)
    }
  })

  test("asks for a copy of a portrait the size of the column it is drawn in", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    const face = page.getByTestId("board-member-91").locator("img")
    await expect(face).toHaveAttribute("srcset", /emma-160\.webp 160w/)
    // A face holds a column a few hundred pixels across, so one of the stored copies is what
    // a slice needs and the 640 master is never asked for by name.
    const fetched = await face.evaluate((img: HTMLImageElement) => img.currentSrc)
    expect(fetched, "the copy a slice fetched").toMatch(/emma-\d+\.webp$/)
  })

  test("offers an expansion only where something was written about the member", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    // Nobody wrote anything about the third member, so there is nothing to open onto and every
    // route to opening refuses it: the pointer, the click, the slice the band settles on, and
    // the scroll that decides on a phone.
    await expect(page.getByTestId("board-member-blurb-93")).toHaveCount(0)

    await page.getByTestId("board-member-93").click()
    await expect(page.getByTestId("board-member-93")).not.toHaveClass(/slice--open/)
    // What was open stays open: a slice that cannot open does not take the band with it.
    await expect(page.getByTestId("board-member-blurb-91")).toBeVisible()

    // And the two who did write something do open, on the pointer alone.
    await page.getByTestId("board-member-92").hover()
    await expect(page.getByTestId("board-member-blurb-92")).toBeVisible()
  })

  test("opens the chair when a board first appears, and one member at a time", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    // A reader meets an open member rather than a stack of shut ones and no clue that any open.
    // Which member is open is the slice's own business: a shut slice keeps its words in the
    // document and gives them no room, so the class is what says shut and not the words.
    await expect(page.getByTestId("board-member-91")).toHaveClass(/slice--open/)
    await expect(page.getByTestId("board-member-blurb-91")).toBeVisible()
    await expect(page.getByTestId("board-member-92")).not.toHaveClass(/slice--open/)

    await press(page.getByTestId("board-member-92"))

    await expect(page.getByTestId("board-member-92")).toHaveClass(/slice--open/)
    await expect(page.getByTestId("board-member-91")).not.toHaveClass(/slice--open/)

    // One at a time, and what shuts a member is another member opening rather than a second press:
    // a band with nothing open says a reader is nowhere.
    await press(page.getByTestId("board-member-91"))
    await expect(page.getByTestId("board-member-91")).toHaveClass(/slice--open/)
    await expect(page.getByTestId("board-member-92")).not.toHaveClass(/slice--open/)
  })

  test("opens nothing on a board where nobody wrote anything about anybody", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board?board=7")

    // A whole board of the real history is like this. Its members still read as people: a face,
    // a name and what they were, and nothing that offers to open.
    await expect(page.getByTestId("board-member-71")).toContainText("Thijs Lieverse")
    await expect(page.getByTestId("board-member-71")).toContainText("Chairman")
    await expect(page.getByTestId("board-member-blurb-71")).toHaveCount(0)
    await expect(page.getByTestId("board-member-71")).not.toHaveClass(/slice--open/)
  })

  test("reads a member as a face with the name on it and the words beside it", async ({page}) => {
    await page.setViewportSize({width: 1400, height: 1000})
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    const slice = (await page.getByTestId("board-member-91").boundingBox())!
    const face = (await page.getByTestId("board-member-91").locator("img").boundingBox())!
    const name = (await page.getByTestId("board-member-91").getByText('Emma "Emmz" Dokter').boundingBox())!
    const blurb = (await page.getByTestId("board-member-blurb-91").boundingBox())!

    // The face holds the left of the slice at its full height, and the name is on it rather
    // than beside it: one thing, which is why opening the slice does not rearrange it.
    expect(face.x).toBeCloseTo(slice.x, 0)
    expect(face.height).toBeCloseTo(slice.height, 0)
    expect(name.x).toBeLessThan(face.x + face.width)
    expect(name.y).toBeGreaterThan(face.y + face.height / 2)

    // What the slice grows is room for the words, to the right of the face.
    expect(blurb.x).toBeGreaterThan(face.x)
  })

  test("names a board with no recorded name from its number", async ({page}) => {
    await installApiMocks(page, {boards: namelessBoard})

    await page.goto("/board")

    // A board's name may never have been written down, and no board reads as blank.
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD VI · 2022-2023")
  })

  test("puts a member's nickname back between the name it sits inside", async ({page}) => {
    await installApiMocks(page, {boards: namelessBoard})

    await page.goto("/board")

    // The name and the nickname are two fields now. A reader still sees the one string.
    await expect(page.getByTestId("board-members")).toContainText('Roos "SkyeWolf" Kruk')
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
    // island's own: the same one the esports pages end on.
    await expect(page.getByTestId("board-join")).toBeVisible()
    // Two ways of asking and nothing in front of them: the Discord the board answers on, and
    // the address a motivation letter goes to.
    await expect(page.getByTestId("board-join-discord")).toHaveAttribute("href", /discord\.gg/)
    await expect(page.getByTestId("board-join-mail")).toHaveAttribute("href", /^mailto:/)
  })

  test("keeps a member a row on a phone, and opens it there", async ({page}) => {
    await page.setViewportSize({width: 390, height: 900})
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    const row = (await page.getByTestId("board-member-91").boundingBox())!
    const face = (await page.getByTestId("board-member-91").locator("img").boundingBox())!
    const blurb = (await page.getByTestId("board-member-blurb-91").boundingBox())!

    // The face still holds the left and the words are still beside it, so the page reads the
    // same at both widths. Nothing runs off the side of the phone.
    expect(face.x).toBeCloseTo(row.x, 0)
    expect(face.width).toBeLessThan(row.width)
    expect(row.width).toBeLessThanOrEqual(390)
    expect(blurb.x).toBeGreaterThan(face.x)
    expect(blurb.x + blurb.width).toBeLessThanOrEqual(391)

    await press(page.getByTestId("board-member-92"))
    await expect(page.getByTestId("board-member-92")).toHaveClass(/slice--open/)
    await expect(page.getByTestId("board-member-91")).not.toHaveClass(/slice--open/)
  })

  test("stacks the timeline, the banner and the faces on a phone", async ({page}) => {
    await page.setViewportSize({width: 390, height: 900})
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board")

    const boxes = () => Promise.all(["board-timeline", "board-band", "board-members"].map(
      async (id) => (await page.getByTestId(id).boundingBox())!,
    ))

    /*
     * One above the next: how far the deepest of them reaches past the top of the one below.
     *
     * Polled, because the band's own height follows its photograph and the photograph arrives
     * when it arrives. Read on the way there, the boundary between the band and the faces is a
     * fraction of a pixel out and the read says the two overlap — which is a read of a page
     * still being laid out, not of a stack that does not stack. What is asserted is the stack
     * the page settles in, so the read has to be of the page settled.
     */
    const over = async () => {
      const [strip, band, members] = await boxes()
      return Math.max((strip.y + strip.height) - band.y, (band.y + band.height) - members.y)
    }
    await expect.poll(over, {timeout: 5000}).toBeLessThanOrEqual(0.5)

    // And none of them wider than the phone, so nothing scrolls sideways. The board's own words
    // are inside the banner now, which is why there are three boxes here and not four.
    for (const box of await boxes()) expect(box.width).toBeLessThanOrEqual(390)
  })
})

/**
 * What the management board editor used to guarantee, asserted where each of those things now
 * happens. The editor is gone; none of what it covered went with it.
 *
 * Its own two guarantees (that the address is a page, and that the navigation offers it) are
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

/**
 * Travelling between boards with a finger.
 *
 * Observable in the ordinary phone project, and that is the point of the reduced-motion decision
 * rather than an accident of it: the band follows the finger whatever the visitor has asked for,
 * because content under direct manipulation is not the unbidden movement the preference is about.
 * What the preference clamps is the ease onto the arrived board and the spring home, which is
 * choreography, and which the motion spec beside this one watches instead.
 *
 * Every board arrives with its members in one read, so nothing is fetched for any of this.
 */
test.describe("travelling between boards with a finger", () => {
  // The gesture binds where the pointer is coarse, so on the desktop project there is nothing
  // here to observe. That a mouse does not drag the band is asserted below, where a mouse is.
  test.skip(({isMobile}) => !isMobile, "the gesture binds only where the pointer is coarse")

  test("carries the page back to the board before this one, and the back button returns", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})
    await page.goto("/board")
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IX · 2025-2026")

    // Oldest sits left on the line, so pulling the page rightwards walks back down the history.
    await dragBand(page, page.getByTestId("board-swipe"), {by: 260})

    await expect(page).toHaveURL(/\?board=7$/)
    await expect(page.getByTestId("board-band-name")).toHaveText("Overcooked")

    // A swipe is a navigation like any other: it has left a history entry behind it.
    await page.goBack()
    await expect(page).toHaveURL(/\/board$/)
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IX · 2025-2026")
  })

  test("carries it on to the next board when the finger goes the other way", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})
    await page.goto("/board")
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IX · 2025-2026")

    await dragBand(page, page.getByTestId("board-swipe"), {by: -260})

    // The board elected and not yet sitting is on the line, so it is somewhere a finger reaches.
    await expect(page).toHaveURL(/\?board=10$/)
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD X · 2099-2100")
  })

  test("leaves the board alone for a drag that was neither far enough nor fast enough", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})
    await page.goto("/board")
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IX · 2025-2026")

    await dragBand(page, page.getByTestId("board-swipe"), {by: 48})

    await expect(page).toHaveURL(/\/board$/)
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IX · 2025-2026")
  })

  test("springs back where the finger was returned before it lifted", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})
    await page.goto("/board")

    // Most of the way there and then most of the way back, without lifting: a started gesture
    // is not a committed one.
    await dragBand(page, page.getByTestId("board-swipe"), {by: [200, -180]})

    await expect(page).toHaveURL(/\/board$/)
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IX · 2025-2026")
  })

  test("goes nowhere at either end of the line, however far the finger hauls", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})

    await page.goto("/board?board=4")
    // The oldest board recorded, dragged further back still.
    await dragBand(page, page.getByTestId("board-swipe"), {by: 300})

    await expect(page).toHaveURL(/\?board=4$/)
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IV · 2020-2021")

    await page.goto("/board?board=10")
    await dragBand(page, page.getByTestId("board-swipe"), {by: -300})

    await expect(page).toHaveURL(/\?board=10$/)
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD X · 2099-2100")
  })

  test("does not trip the pencil the drag started on", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})
    await loginAsBoard(page.context())
    await page.goto("/board")

    // The pencil on a member's slice, which is the press with the most to lose: travelling must
    // never open an editor. Far enough to be a gesture, not far enough to be taken, so the board
    // is still the one the drag began on when the assertion is made.
    await dragBand(page, page.getByTestId("board-swipe"), {by: 60, on: page.getByTestId("board-member-edit-92")})

    await expect(page).toHaveURL(/\/board$/)
    await expect(page.getByTestId("board-member-dialog")).toHaveCount(0)
    // And the pencil still works when it is pressed rather than dragged from.
    await page.getByTestId("board-member-edit-92").click()
    await expect(page.getByTestId("board-member-dialog")).toBeVisible()
  })

  test("lands on the board asked for last when two swipes follow one another", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})
    await page.goto("/board")
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IX · 2025-2026")

    const band = page.getByTestId("board-swipe")
    await dragBand(page, band, {by: 260})
    await expect(page).toHaveURL(/\?board=7$/)
    await dragBand(page, band, {by: 260})

    await expect(page).toHaveURL(/\?board=4$/)
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IV · 2020-2021")
  })

  test("keeps hitting a node on the line working exactly as it did", async ({page}) => {
    await installApiMocks(page, {boards: wholeHistory})
    await page.goto("/board")

    // The gesture has added a way rather than replaced one.
    await page.getByTestId("board-node-7").click()

    await expect(page).toHaveURL(/\?board=7$/)
    await expect(page.getByTestId("board-band-name")).toHaveText("Overcooked")
  })
})

test.describe("the band and a mouse", () => {
  test.skip(({isMobile}) => isMobile, "a coarse pointer is what the gesture is for")

  test("is not dragged by a mouse, however narrow the window", async ({page}) => {
    // A narrow desktop window is still a desktop: the band is a row of slices that open under a
    // pointer, so a mouse hauled across it would open every one it crossed while the page moved.
    await page.setViewportSize({width: 390, height: 900})
    await installApiMocks(page, {boards: wholeHistory})
    await page.goto("/board")
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IX · 2025-2026")

    await dragBand(page, page.getByTestId("board-swipe"), {by: 260})

    await expect(page).toHaveURL(/\/board$/)
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IX · 2025-2026")
  })
})
