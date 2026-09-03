import type {Locator} from "@playwright/test"
import type {Page} from "./test"
import {expect, test} from "./test"
import {installApiMocks, loginAsBoard, preferLightTheme} from "./mocks"
import {pressSlice} from "./sliceBand"

/** The phone the stacked band is read on. */
const PHONE = {width: 390, height: 900}

/**
 * How much wider than tall a stacked portrait is drawn.
 *
 * A second statement of the figure the stylesheet crops to, and deliberately so. A test that
 * read the crop out of the component would agree with it whatever it said; this one says what a
 * reader is owed and fails the day the crop moves without anybody meaning it to.
 */
const CROP = 4 / 3

/**
 * A mask the browser computed that fades downwards, which is what a stacked portrait's does.
 *
 * A gradient running downwards is the default, so the browser leaves the direction out of what
 * it computes. A named direction in here at all is a picture fading the wrong way — to the
 * right, across the reading direction, which is the shape this one replaced.
 *
 * The depth is left loose on purpose. This suite runs with full motion whatever the config says
 * (#852), so the depth read a moment after a press is wherever the eased dissolve had got to,
 * and a whole number of percent is a coin toss on how busy the machine is. Where it comes to
 * rest is asserted frame by frame in the motion spec, which is the layer that can watch it.
 */
const DOWNWARDS = /^linear-gradient\(rgb\(0, 0, 0\) 0px, rgb\(0, 0, 0\) \d+(?:\.\d+)?%, rgba\(0, 0, 0, 0\) 100%\)$/

/** The board page on a phone, which every test of the stacked band starts from. */
async function boardOnAPhone(page: Page, options: {path?: string, light?: boolean} = {}): Promise<void> {
  await page.setViewportSize(PHONE)
  await installApiMocks(page, {boards: wholeHistory})
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

    // The portrait takes the whole width of the slice, at four by three, worked out from the
    // slice rather than from the window: the band's own width is what the crop is of.
    expect(face.x).toBeCloseTo(slice.x, 0)
    expect(face.width).toBeCloseTo(slice.width, 0)
    expect(face.height).toBeCloseTo(face.width / CROP, 0)

    // The name is on the photograph, at its foot, and never below it.
    expect(name.y).toBeGreaterThan(face.y + face.height / 2)
    expect(name.y + name.height).toBeLessThanOrEqual(face.y + face.height + 1)

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
    await boardOnAPhone(page, {path: "/board?board=4"})
    await expect(page.getByTestId("board-member-41")).toContainText("Anne Schrader")

    // A portrait's band alone is the width of the screen at the crop. Nobody on this board has
    // one, and nobody wrote anything either, so what is there is a name and a role and the band
    // is as tall as they need rather than as tall as a photograph would have been.
    const band = (await page.getByTestId("board-members").boundingBox())!
    expect(band.height).toBeLessThan(PHONE.width / CROP)
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

    // The shape is the theme's business in nothing at all.
    expect(face.width).toBeCloseTo(slice.width, 0)
    expect(face.height).toBeCloseTo(face.width / CROP, 0)
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

  test("stacks the timeline, the banner and the faces on a phone", async ({page}) => {
    await boardOnAPhone(page)

    const strip = (await page.getByTestId("board-timeline").boundingBox())!
    const band = (await page.getByTestId("board-band").boundingBox())!
    const members = (await page.getByTestId("board-members").boundingBox())!

    // One above the next, none of them wider than the phone, and nothing scrolling sideways.
    // The board's own words are inside the banner now, which is why there are three boxes here
    // and not four.
    expect(strip.y + strip.height).toBeLessThanOrEqual(band.y + 1)
    expect(band.y + band.height).toBeLessThanOrEqual(members.y + 1)
    for (const box of [strip, band, members]) expect(box.width).toBeLessThanOrEqual(390)
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
