import type {Locator} from "@playwright/test"
import type {Page} from "./test"
import {expect, test} from "./test"
import {dragBand} from "./bandSwipe"
import {installApiMocks, loginAsBoard, preferLightTheme} from "./mocks"
import {pressSlice, pressSliceEdit} from "./sliceBand"

/** The phone the stacked band is read on. */
const PHONE = {width: 390, height: 900}

/**
 * The shape of a picture as the browser actually decoded it: how many times taller than wide.
 *
 * There is no figure a stacked portrait is drawn to, and asserting one is what these tests used
 * to do wrong. Two crops were tried — three by two, then four by three — and both were abandoned
 * for the same reason: every portrait the association has recorded is taller than it is wide,
 * between 1.36 and 1.55 times, so a landscape box of any depth threw away most of half of them.
 * The band's height is now the slice's own width times the *picture's* own aspect, so the figure
 * belongs to the photograph and a test naming one of its own would be back to guessing.
 *
 * So the property, not the number: whatever shape the picture is, that is the shape of its box,
 * which is what "nothing is cropped" means and what no single figure could ever say.
 *
 * A ratio rather than two lengths, because under a `w` srcset the browser divides an image's
 * intrinsic size by the density of the candidate it chose — `naturalWidth` is in css pixels and
 * is not the file's width at all. Both halves are divided by the same figure, so the ratio comes
 * through it unharmed.
 */
const decodedAspect = (face: Locator): Promise<number> => face.evaluate(
  (img: HTMLImageElement) => img.naturalHeight / img.naturalWidth,
)

/**
 * How deep the island says a photograph goes soft, as a fraction of the box: `--photo-dissolve`.
 *
 * Read off the page rather than written down here. Two things below are about this stretch of
 * the picture — the name sits inside it, and the ground under the name fades over the same one —
 * and a copy of the figure in a test is a copy that can quietly disagree with the token.
 */
const restingDissolve = (page: Page): Promise<number> => page.evaluate(() => (
  parseFloat(getComputedStyle(document.documentElement).getPropertyValue("--photo-dissolve")) / 100
))

/**
 * A mask the browser computed that fades downwards, which is what a stacked portrait's does.
 *
 * A gradient running downwards is the default, so the browser leaves the direction out of what
 * it computes. A named direction in here at all is a picture fading the wrong way — to the
 * right, across the reading direction, which is the shape this one replaced.
 *
 * The depth is left loose on purpose. The dissolve is eased even under the reduced ceiling, so
 * the depth read a moment after a press is wherever it had got to, and a whole number of percent
 * is a coin toss on how busy the machine is. Where it comes to rest is asserted frame by frame in
 * the motion spec, which is the layer that can watch it.
 */
const DOWNWARDS = /^linear-gradient\(rgb\(0, 0, 0\) 0px, rgb\(0, 0, 0\) \d+(?:\.\d+)?%, rgba\(0, 0, 0, 0\) 100%\)$/

/**
 * The board page on a phone, which every test of the stacked band starts from.
 *
 * [boards] is the association's whole history unless a test is about a board the history has no
 * example of — a photograph small enough to be drawn narrower than the page, say.
 */
async function boardOnAPhone(
  page: Page,
  options: {path?: string, light?: boolean, boards?: Array<Record<string, unknown>>} = {},
): Promise<void> {
  await page.setViewportSize(PHONE)
  await installApiMocks(page, {boards: options.boards ?? wholeHistory})
  if (options.light) await preferLightTheme(page)
  await page.goto(options.path ?? "/board")
}

/**
 * Opens a member's slice and waits for it to say that it is open.
 *
 * The control is what says so, and it says so to a reader with a screen reader too. Not the
 * description's own box: a shut slice keeps its words in the document and clips them, and a
 * clipped child still has a box, so the words are no signal at all.
 */
async function openMember(page: Page, id: number): Promise<Locator> {
  const slice = page.getByTestId(`board-member-${id}`)
  await pressSlice(slice)
  await expect(slice.getByRole("button")).toHaveAttribute("aria-expanded", "true")
  return slice
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

/**
 * A portrait as the api answers with one, at the widths one is stored at.
 *
 * Taller than it is wide, by half again, which is the shape most of the association's own
 * portraits are and the same shape the mocked file itself comes back at. The two have to agree:
 * a stacked slice draws a portrait in a box of the aspect the api reported, and the test of that
 * is that the box and the decoded photograph are the same shape — so a fixture claiming one
 * shape while the bytes are another would fail an assertion about the layout for a reason that
 * has nothing to do with it. It used to say 640 square, which is neither shape.
 *
 * The shape, not the size: every stored width answers with the same file, so no fixture could
 * make the pixel counts agree, and the ratio is the only part of this an assertion reads. The
 * master stays 640 wide because a stored width already claims that number — a master with a
 * width of its own joins the ladder as another candidate, and a browser choosing it is a
 * browser the tests below would report as having fetched the master by name.
 */
const portrait = (name: string) => ({
  path: `board-portraits/${name}.webp`,
  url: `/files/public/board-portraits/${name}.webp`,
  width: 640,
  height: 960,
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

/**
 * A board whose photograph is small and nearly square, which the real history is full of.
 *
 * Board V's is 461 by 409. A photograph of that shape is the one that shows whether the picture
 * is being drawn at the width of the page or at whatever its own proportions come to against the
 * band's height — a wide master lands close enough to the page's width to look right while being
 * wrong. Its own fixture rather than another board on the history, so the arithmetic every other
 * test does about that history stays where it is.
 */
const nearlySquarePhotograph = [board({
  id: 5, number: 5, name: "Bittersweet",
  startDate: "2021-09-01", endDate: "2022-08-31",
  photo: {
    path: "board-photos/board5.webp",
    url: "/files/public/board-photos/board5.webp",
    width: 461,
    height: 409,
    renditions: [320].map((width) => ({url: `/files/public/board-photos/board5-${width}.webp`, width})),
  },
  members: [member(51, 5, "Sanne de Wit", "Chairman")],
})]

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
    await boardOnAPhone(page)

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

  /*
   * A photograph narrower than the page was silently wrong for as long as the stacked band
   * existed, and nothing caught it: the width had to be stated *after* the rule it overrides,
   * because a media query adds no specificity and `width: auto` written later in the file beat
   * `width: 100%` written earlier inside a query. So the picture came out at whatever its own
   * proportions made of the band's height, against a strip of empty ground beside it.
   */
  test("spans a board's photograph across the page on a phone", async ({page}) => {
    await boardOnAPhone(page, {boards: nearlySquarePhotograph})

    const band = (await page.getByTestId("board-band").boundingBox())!
    const photo = (await page.getByTestId("board-photo").boundingBox())!

    // Edge to edge, whatever shape the photograph is: the band is the page's hero and the one
    // thing on it that is the board rather than a fact about the board.
    expect(photo.x).toBeCloseTo(band.x, 0)
    expect(photo.width).toBeCloseTo(band.width, 0)
    expect(photo.width).toBeCloseTo(PHONE.width, 0)
  })

  /*
   * And a board with no photograph keeps its words inside its own band.
   *
   * The words are pulled up over the foot of the photograph so the two read as one band. With no
   * photograph there is nothing above them to be pulled over, so the lift took them up out of
   * the band and into whatever the band sits under, which is the strip.
   */
  test("keeps a bare board's words out of the strip on a phone", async ({page}) => {
    await boardOnAPhone(page, {path: "/board?board=4"})
    await expect(page.getByTestId("board-band-eyebrow")).toHaveText("BOARD IV · 2020-2021")

    /*
     * How far the words have strayed: under the strip, above their own band, or out of it.
     *
     * All three read in the one pass, and polled. A band arriving is a band travelling — the
     * page eases it in — so two boxes read a round trip apart are two boxes read at different
     * offsets, which shows up as the words being a pixel or two out of a band they are exactly
     * inside. One read of all three, once the travel is over, is a read of the page rather than
     * of the way it got there.
     */
    const strayed = () => page.evaluate(() => {
      const box = (id: string) => document.querySelector(`[data-testid='${id}']`)!.getBoundingClientRect()
      const strip = box("board-timeline")
      const band = box("board-band")
      const words = box("board-band-words")
      return Math.max(strip.bottom - words.top, band.top - words.top, words.height - band.height)
    })

    await expect.poll(strayed, {timeout: 5000}).toBeLessThanOrEqual(0.5)
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

    /*
     * And the band stays a band: every slice is the same height and stands on the same line
     * whether it has art or not.
     *
     * All three read in one pass, and polled. The band eases in, so three boxes read a round
     * trip apart are three boxes read at three different moments of that, and the widest gap
     * between them comes out about a pixel — a read of the band arriving rather than of a band
     * whose slices disagree.
     */
    const ragged = () => page.evaluate((ids) => {
      const boxes = ids.map((id) => document
        .querySelector(`[data-testid='board-member-${id}']`)!.getBoundingClientRect())
      const spread = (of: (box: DOMRect) => number) =>
        Math.max(...boxes.map(of)) - Math.min(...boxes.map(of))
      return Math.max(spread((box) => box.height), spread((box) => box.top))
    }, [91, 92, 93])

    await expect.poll(ragged, {timeout: 5000}).toBeLessThanOrEqual(0.5)
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

    await pressSlice(page.getByTestId("board-member-92"))

    await expect(page.getByTestId("board-member-92")).toHaveClass(/slice--open/)
    await expect(page.getByTestId("board-member-91")).not.toHaveClass(/slice--open/)

    // One at a time, and what shuts a member is another member opening rather than a second press:
    // a band with nothing open says a reader is nowhere.
    await pressSlice(page.getByTestId("board-member-91"))
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

  /*
   * The stacked slice, which is a shape of its own rather than the row at a smaller size.
   *
   * Geometry rather than class names: what a visitor can see is where the description sits
   * relative to the portrait, how wide it is and which edge the photograph goes soft at. Every
   * assertion is of the state the slice settles in, so the suite's reduced motion is the only
   * setting these need.
   */
  test("stacks a member's portrait over their description on a phone", async ({page}) => {
    await boardOnAPhone(page)
    const member = await openMember(page, 91)

    const slice = (await member.boundingBox())!
    const face = (await member.locator("img").boundingBox())!
    const name = (await member.getByText('Emma "Emmz" Dokter').boundingBox())!
    const blurb = (await page.getByTestId("board-member-blurb-91").boundingBox())!

    // The portrait takes the whole width of the slice, worked out from the slice rather than
    // from the window: the band's own width is what its height is a multiple of.
    expect(face.x).toBeCloseTo(slice.x, 0)
    expect(face.width).toBeCloseTo(slice.width, 0)

    // And the whole of the photograph, because the box is the photograph's own shape: given a
    // box of the picture's ratio the `object-fit: cover` the slice uses has nothing to crop.
    const aspect = await decodedAspect(member.locator("img"))
    expect(face.height / face.width).toBeCloseTo(aspect, 1)
    // Taller than wide, or the line above would be satisfied by a square and would say nothing
    // about which way round the aspect had been read.
    expect(aspect).toBeGreaterThan(1.2)

    // The name is on the photograph, at its foot: inside the last stretch of it, the stretch
    // the dissolve goes soft over, rather than lifted clear above the line where it begins. A
    // name a fifth of the way up a portrait reads as neither on the photograph nor under it,
    // and what keeps it legible down there is the scrim, which is darkest exactly there.
    expect(name.y).toBeGreaterThan(face.y + face.height / 2)
    expect(name.y + name.height).toBeLessThanOrEqual(face.y + face.height + 1)
    const dissolve = await restingDissolve(page)
    expect(name.y + name.height).toBeGreaterThan(face.y + face.height * (1 - dissolve))

    // And the description reads below the picture, in the width of the slice rather than in a
    // third of it, which is the gutter this shape exists to remove.
    expect(blurb.y).toBeGreaterThanOrEqual(face.y + face.height - 1)
    expect(blurb.width).toBeGreaterThan(slice.width * 0.8)
    expect(slice.width).toBeLessThanOrEqual(PHONE.width)
    expect(blurb.x + blurb.width).toBeLessThanOrEqual(PHONE.width + 1)
  })

  test("keeps a phone's portrait the same size as the slice opens and shuts", async ({page}) => {
    await boardOnAPhone(page)
    const member = await openMember(page, 91)
    const open = (await member.locator("img").boundingBox())!

    // Another member opens, so this one shuts. What opening a slice brings is the words under
    // the picture: it never resizes the face.
    await openMember(page, 92)
    await expect(member.getByRole("button")).toHaveAttribute("aria-expanded", "false")

    const shut = (await member.locator("img").boundingBox())!
    expect(shut.width).toBeCloseTo(open.width, 0)
    expect(shut.height).toBeCloseTo(open.height, 0)
  })

  /*
   * The drawn divider leans the way the seam it is drawn on leans.
   *
   * A slice is clipped on a diagonal and two slices of the same tone meet on an invisible one,
   * so the boundary is drawn: a 1.5px sliver clipped to the same geometry. Across a row that
   * boundary is a slice's leaning left edge and the sliver is tall and thin; stacked it is the
   * leaning *top* edge, and the sliver left as it was ran down the inside of every slice,
   * crossing the words rather than dividing anything from anything.
   *
   * Read out of the clip and turned back into a box, which is the closest a test gets to what a
   * reader sees of a line 1.5px wide: it runs the width of the slice and is barely tall, rather
   * than running its height and being barely wide.
   */
  test("leans a phone slice's drawn divider along the seam it is cut on", async ({page}) => {
    await boardOnAPhone(page)

    /** The box the divider's sliver is drawn inside, in pixels, out of the clip that shapes it. */
    const sliver = (slice: Locator) => slice.evaluate((el) => {
      const clip = getComputedStyle(el, "::after").clipPath
      const box = el.getBoundingClientRect()
      const points = [...clip.matchAll(/(-?[\d.]+)(px|%)\s+(-?[\d.]+)(px|%)/g)].map((at) => ({
        x: at[2] === "%" ? (Number(at[1]) / 100) * box.width : Number(at[1]),
        y: at[4] === "%" ? (Number(at[3]) / 100) * box.height : Number(at[3]),
      }))
      const xs = points.map((point) => point.x)
      const ys = points.map((point) => point.y)
      return {
        across: Math.max(...xs) - Math.min(...xs),
        down: Math.max(...ys) - Math.min(...ys),
        slice: {width: box.width, height: box.height},
      }
    })

    // Not the first: there is nothing above it to divide it from.
    const stacked = await sliver(page.getByTestId("board-member-92"))
    expect(stacked.across).toBeCloseTo(stacked.slice.width, 0)
    expect(stacked.down).toBeLessThan(stacked.across / 4)

    // And the row still divides the other way round, which is what makes this a turn rather
    // than a correction: the same sliver, on the same cut, read from the other side.
    await page.setViewportSize({width: 1280, height: 900})
    await expect(page.getByTestId("board-member-92")).toBeVisible()
    const inARow = await sliver(page.getByTestId("board-member-92"))
    expect(inARow.down).toBeCloseTo(inARow.slice.height, 0)
    expect(inARow.across).toBeLessThan(inARow.down / 4)
  })

  test("dissolves the foot of an open portrait on a phone, and leaves a shut one whole", async ({page}) => {
    await boardOnAPhone(page)
    const member = await openMember(page, 91)

    const mask = () => member.locator("img").evaluate((img) => getComputedStyle(img).maskImage)

    // Downwards into the words, the way the board photograph in the band above already fades on
    // a narrow screen, rather than to the right across the reading direction.
    expect(await mask()).toMatch(DOWNWARDS)

    // Shut there is nothing for the picture to be joined to, so it ends on the band's own
    // diagonal rather than melting into the face after it.
    await openMember(page, 92)
    await expect(member.getByRole("button")).toHaveAttribute("aria-expanded", "false")
    expect(await mask()).toBe("none")
  })

  test("carries a phone portrait's name on ground of the portrait's own", async ({page}) => {
    await boardOnAPhone(page)
    const member = await openMember(page, 91)

    const ground = (id: number) => page.getByTestId(`board-member-${id}`).getByRole("button")
      .evaluate((body) => {
        const scrim = getComputedStyle(body, "::before")
        return {display: scrim.display, height: parseFloat(scrim.height)}
      })
    const face = (await member.locator("img").boundingBox())!

    // The scrim is the picture's band and not the foot of the slice, which after the restack is
    // below the description: left there it would draw a dark band under the prose.
    const carried = await ground(91)
    expect(carried.display).not.toBe("none")
    expect(carried.height).toBeCloseTo(face.height, 0)

    // Twenty-six of the forty-six members in the history have no portrait. A scrim with no
    // photograph under it is a dark fade up the page and nothing else, so it is not drawn.
    expect((await ground(92)).display).toBe("none")
  })

  /*
   * One line, not two.
   *
   * The name has come down into the picture's last stretch, which is the stretch the photograph
   * itself goes soft over — so the ground under the name has to go soft over exactly that
   * stretch too, or a reader is shown a dark band standing where the picture has already gone.
   * In the light half that is a smear across the page.
   *
   * Which is why the eased depth is declared on the slice and inherited: the picture and the
   * name's ground are siblings, and one depth read by both is what keeps them on one line. Held
   * on the slice with `inherits: false` the ground read the registered initial of nothing and
   * never faded at all, which is the state this asserts against.
   */
  test("fades a phone portrait and the name's ground on the one line", async ({page}) => {
    await boardOnAPhone(page)
    const member = await openMember(page, 91)

    /*
     * How far down each of the two has gone, as a percentage of the picture's band.
     *
     * Both read inside one `evaluate`, so both are the same frame's answer. The dissolve is eased
     * even under the reduced ceiling, so the depth a moment after a press is wherever it had got
     * to — which is exactly why the two have to be read together and why the pair is polled until
     * it settles rather than sampled once.
     */
    const depths = () => member.getByRole("button").evaluate((body) => {
      const depth = (style: CSSStyleDeclaration) => {
        const stop = /(\d+(?:\.\d+)?)%/.exec(style.maskImage)
        return stop ? Math.round(100 - Number(stop[1])) : 0
      }
      const picture = body.parentElement!.querySelector("img")!
      return [depth(getComputedStyle(picture)), depth(getComputedStyle(body, "::before"))]
    })

    const resting = Math.round((await restingDissolve(page)) * 100)
    await expect.poll(depths, {timeout: 5000}).toEqual([resting, resting])

    // And shut, neither of them carries a mask: there is nothing under a shut slice for either
    // to be joined to, and the picture ends on the band's own diagonal instead.
    await openMember(page, 92)
    await expect(member.getByRole("button")).toHaveAttribute("aria-expanded", "false")
    expect(await depths()).toEqual([0, 0])
  })

  test("asks for a phone portrait at the width of the slice it fills", async ({page}) => {
    await boardOnAPhone(page)

    const face = page.getByTestId("board-member-91").locator("img")
    await expect(face).toHaveAttribute("srcset", /emma-160\.webp 160w/)

    // Stacked, a portrait is the full width of the slice rather than a column in a row, and the
    // figure the browser is promised says so: a third of the screen fetches a face to be blown
    // up over the whole of it.
    await expect.poll(() => face.getAttribute("sizes")).toMatch(/^\d+px$/)
    const asked = Number((await face.getAttribute("sizes"))!.replace("px", ""))
    expect(asked).toBeGreaterThan(340)
    expect(asked).toBeLessThanOrEqual(PHONE.width)

    // And one of the stored copies still, rather than the master by name.
    const fetched = await face.evaluate((img: HTMLImageElement) => img.currentSrc)
    expect(fetched, "the copy a phone fetched").toMatch(/emma-\d+\.webp$/)
  })

  test("gives a phone band of members with no portraits the height of their names", async ({page}) => {
    await boardOnAPhone(page)
    // A slice with a portrait, so the band with none has something of its own page to be a
    // fraction of. There is no figure a portrait's band is: it is the picture's own shape, so a
    // number in here would be this test guessing at what a photograph happens to be.
    const photographed = (await page.getByTestId("board-member-91").boundingBox())!

    await page.goto("/board?board=4")
    await expect(page.getByTestId("board-member-41")).toContainText("Anne Schrader")

    // Nobody on this board has a portrait and nobody wrote anything either, so what is there is
    // a name and a role, and the band is as tall as those need rather than as tall as a
    // photograph would have been. A fraction of one face, not a multiple of it.
    const band = (await page.getByTestId("board-members").boundingBox())!
    expect(band.height).toBeLessThan(photographed.height / 2)
    await expect(page.getByTestId("board-member-blurb-41")).toHaveCount(0)
  })

  /*
   * The light half, which is the one where this fails: the name over a photograph takes
   * near-white ink whichever theme the reader is on, and a member with no portrait must not,
   * or their name is near-white on the light theme's near-white page.
   */
  test("stacks a member the same way, and keeps the names legible, on the light theme", async ({page}) => {
    await boardOnAPhone(page, {light: true})
    const member = await openMember(page, 91)

    const slice = (await member.boundingBox())!
    const face = (await member.locator("img").boundingBox())!
    const blurb = (await page.getByTestId("board-member-blurb-91").boundingBox())!

    // The shape is the theme's business in nothing at all, and it is still the photograph's own.
    expect(face.width).toBeCloseTo(slice.width, 0)
    expect(face.height / face.width).toBeCloseTo(await decodedAspect(member.locator("img")), 1)
    expect(blurb.y).toBeGreaterThanOrEqual(face.y + face.height - 1)
    expect(blurb.width).toBeGreaterThan(slice.width * 0.8)
    expect(await member.locator("img").evaluate((img) => getComputedStyle(img).maskImage)).toMatch(DOWNWARDS)

    const ink = (id: number, name: string) => page.getByTestId(`board-member-${id}`)
      .getByText(name).evaluate((node) => getComputedStyle(node).color)

    // Over a photograph, the near-white the scrim under it makes safe.
    expect(await ink(91, 'Emma "Emmz" Dokter')).toBe("rgb(242, 244, 246)")
    // With no photograph there is no scrim, so the theme's own ink: near-white here would be
    // near-white on paper.
    expect(await ink(92, "Viktor Petrov")).toBe("rgb(28, 28, 28)")
  })

  /*
   * The role and the words are read at a phone's size rather than at the row's.
   *
   * Both took the shared figure until the restack, which is a figure for a narrow panel beside a
   * portrait: the role came out at 11px on a phone, and a member's own words — this band is the
   * only place they appear at all — were set as a caption. Stacked there is no panel, so there
   * is nothing for a larger figure to run out of.
   *
   * A comparison rather than two numbers. What is being asserted is that the phone has a size of
   * its own, and the day either figure is retuned is not a day this should have an opinion.
   */
  test("sets a member's role and words larger on a phone than in the row", async ({page}) => {
    const size = (locator: Locator) => locator.evaluate(
      (node) => parseFloat(getComputedStyle(node).fontSize),
    )

    await boardOnAPhone(page)
    const member = await openMember(page, 91)
    const onAPhone = {
      role: await size(member.getByText("Chair", {exact: true})),
      words: await size(page.getByTestId("board-member-blurb-91")),
    }

    await page.setViewportSize({width: 1400, height: 1000})
    await expect(page.getByTestId("board-member-blurb-91")).toBeVisible()
    const inARow = {
      role: await size(member.getByText("Chair", {exact: true})),
      words: await size(page.getByTestId("board-member-blurb-91")),
    }

    expect(onAPhone.role).toBeGreaterThan(inARow.role)
    expect(onAPhone.words).toBeGreaterThan(inARow.words)
  })

  test("stacks the timeline, the banner and the faces on a phone", async ({page}) => {
    await boardOnAPhone(page)

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
    for (const box of await boxes()) expect(box.width).toBeLessThanOrEqual(PHONE.width)
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
    // And the pencil still works when it is pressed rather than dragged from — pressed once the
    // band has come to rest, since a press that has to scroll first sets the band moving again.
    await pressSliceEdit(page.getByTestId("board-member-edit-92"))
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
