import {Buffer} from "node:buffer"
import type {Page} from "@playwright/test"
import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

/** A portrait as the api answers with one, at the widths one is stored at. */
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

/**
 * The history a spec writes to, made fresh for each one.
 *
 * A function rather than a constant: a member written down lands on the board it belongs to, so a
 * fixture shared between tests would carry one test's member into the next.
 */
const history = () => [
  {
    id: 9, number: 9, name: "Eeveelutions", candidate: "Eeveelutions",
    cheer: "RNG, Be With Me!", accent: null, description: null,
    startDate: "2025-09-01", endDate: null, image: null, photo: null, version: 0,
    createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
    members: [
      {
        id: 91, boardId: 9, userId: 1, role: "Chair", name: "Emma Dokter", nickname: "Emmz",
        description: "Chairing the ninth board.", image: null, portrait: portrait("emma"),
        startDate: "2025-09-01", endDate: "2026-08-31", version: 0,
        createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
      },
      {
        id: 92, boardId: 9, userId: null, role: "Treasurer", name: "Viktor Petrov",
        nickname: null, description: null, image: null, portrait: null,
        startDate: "2025-09-01", endDate: "2026-08-31", version: 0,
        createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
      },
    ],
  },
  {
    id: 4, number: 4, name: null, candidate: "Board 4", cheer: null, accent: null,
    description: null, startDate: "2020-09-01", endDate: "2021-08-31",
    image: null, photo: null, version: 0,
    createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
    // A board nobody has been recorded on, which is where a member is added from nothing.
    members: [],
  },
]

/**
 * What a person does: bring the pointer to the slice, then take up the pencil it reveals. On a
 * touch screen the hover is a no-op and the pencil is already standing.
 *
 * The slice is scrolled to before it is hovered, not by clicking the pencil inside it. A click
 * scrolls its target into view first, that scroll moves the slice out from under the pointer,
 * and the pencil is only visible while the slice is hovered: the click loses the very hover it
 * needs, and reports the pencil as not visible. It went either way depending on how loaded the
 * machine was, and a slice that is already in view has nothing to scroll.
 */
const openMember = async (page: Page, id: number) => {
  const slice = page.getByTestId(`board-member-${id}`)
  const pencil = page.getByTestId(`board-member-edit-${id}`)

  await slice.scrollIntoViewIfNeeded()
  await slice.hover()
  await expect(pencil).toBeVisible()
  await pencil.click()
}

/** The page as a board member reads it, opened on the board in office. */
const asBoard = async (page: Page) => {
  await loginAsBoard(page.context())
  await installApiMocks(page, {boards: history()})
  await page.goto("/board")
  await expect(page.getByTestId("board-band-name")).toHaveText("Eeveelutions")
}

test.describe("a member filled in on the page", () => {
  test("offers a visitor no pencil and no way to add a member", async ({page}) => {
    await installApiMocks(page, {boards: history()})

    await page.goto("/board")
    await expect(page.getByTestId("board-member-91")).toBeVisible()

    // Absent rather than hidden: the page a visitor reads is not covered in pencils, and the
    // history is what it shows.
    await expect(page.getByTestId("board-member-edit-91")).toHaveCount(0)
    await expect(page.getByTestId("board-member-add")).toHaveCount(0)
    await expect(page.getByTestId("board-member-dialog")).toHaveCount(0)
  })

  test("adds a member to a board, in the board's own words for the role", async ({page}) => {
    await asBoard(page)

    await page.getByTestId("board-member-add").click()
    await expect(page.getByTestId("board-member-dialog")).toBeVisible()

    await page.getByTestId("board-member-dialog-name").fill("Roos Kruk")
    await page.getByTestId("board-member-dialog-nickname").fill("SkyeWolf")
    // Not from a fixed list: nine years of boards have renamed and combined their offices.
    await page.getByTestId("board-member-dialog-role")
      .fill("Secretary and Commissioner of the Esports Lounge")
    await page.getByTestId("board-member-dialog-description").fill("Ran the lounge.")

    // The save navigates nothing, so the request is what is awaited and the row is the proof.
    const written = page.waitForRequest(
      (request) => request.method() === "POST"
        && /\/boards\/9\/members$/.test(new URL(request.url()).pathname),
    )
    await page.getByTestId("board-member-dialog-save").click()
    const body = JSON.parse((await written).postData() ?? "{}") as Record<string, unknown>

    expect(body.displayName).toBe("Roos Kruk")
    // Recorded apart from the name rather than typed into the middle of it.
    expect(body.nickname).toBe("SkyeWolf")
    expect(body.role).toBe("Secretary and Commissioner of the Esports Lounge")
    expect(body.description).toBe("Ran the lounge.")
    // Nobody was picked, so no account is sent: the member stands under the name it was given.
    expect(body.userId).toBeUndefined()

    await expect(page.getByTestId("board-member-dialog")).toHaveCount(0)
    // And the page reads again, so the member is on it: the name with the nickname back inside.
    await expect(page.getByTestId("board-member-901")).toContainText('Roos "SkyeWolf" Kruk')
    await expect(page.getByTestId("board-member-901"))
      .toContainText("Secretary and Commissioner of the Esports Lounge")
    await expect(page.getByTestId("board-member-blurb-901")).toContainText("Ran the lounge.")
  })

  test("records somebody on a board that had nobody at all", async ({page}) => {
    await asBoard(page)

    // The fourth board has no members, and the way in is still at the end of the stack. Reached
    // by its own address rather than off the strip: a phone's strip pans rather than scrolls.
    await page.goto("/board?board=4")
    // A reader who may add somebody is shown the band with the way in at the end of it, so the
    // emptiness is a slice in the row rather than the paragraph a visitor gets instead of one.
    await expect(page.getByTestId("board-member-empty-slice"))
      .toContainText("No members are recorded on this board yet")

    await page.getByTestId("board-member-add").click()
    await page.getByTestId("board-member-dialog-name").fill("Anne Schrader")
    await page.getByTestId("board-member-dialog-role").fill("Chairman")
    await page.getByTestId("board-member-dialog-save").click()

    await expect(page.getByTestId("board-member-901")).toContainText("Anne Schrader")
    await expect(page.getByTestId("board-no-members")).toHaveCount(0)
  })

  test("opens a member on what it says, and corrects it", async ({page}) => {
    await asBoard(page)

    await openMember(page, 91)

    // Everything the page read, back in the fields it was written in.
    await expect(page.getByTestId("board-member-dialog-name")).toHaveValue("Emma Dokter")
    await expect(page.getByTestId("board-member-dialog-nickname")).toHaveValue("Emmz")
    await expect(page.getByTestId("board-member-dialog-role")).toHaveValue("Chair")
    await expect(page.getByTestId("board-member-dialog-description"))
      .toHaveValue("Chairing the ninth board.")
    // The nickname sits beside the name rather than inside it, and the dialog says how the
    // page will publish the two together.
    await expect(page.getByTestId("board-member-dialog-published"))
      .toHaveText('Reads as Emma "Emmz" Dokter')

    await page.getByTestId("board-member-dialog-nickname").fill("LyndisLuna")
    await page.getByTestId("board-member-dialog-description").fill("Chaired the year of the rebuild.")

    const written = page.waitForRequest(
      (request) => request.method() === "PUT"
        && /\/boards\/9\/members\/91$/.test(new URL(request.url()).pathname),
    )
    await page.getByTestId("board-member-dialog-save").click()
    const body = JSON.parse((await written).postData() ?? "{}") as Record<string, unknown>

    expect(body.nickname).toBe("LyndisLuna")
    expect(body.displayName).toBe("Emma Dokter")
    expect(body.description).toBe("Chaired the year of the rebuild.")

    await expect(page.getByTestId("board-member-91")).toContainText('Emma "LyndisLuna" Dokter')
    await expect(page.getByTestId("board-member-blurb-91"))
      .toContainText("Chaired the year of the rebuild.")
  })

  test("uploads a portrait, shows it before the save, and puts it on the member", async ({page}) => {
    await asBoard(page)

    // The member with no portrait: its slice draws no picture at all until one is uploaded.
    await expect(page.getByTestId("board-member-92").locator("img")).toHaveCount(0)
    await openMember(page, 92)

    await expect(page.getByTestId("board-member-dialog-portrait-empty")).toBeAttached()
    await page.getByTestId("board-member-dialog-portrait-file").setInputFiles({
      name: "viktor.png",
      mimeType: "image/png",
      buffer: Buffer.from(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==",
        "base64",
      ),
    })

    // Shown before it is saved, because a picture nobody can see is one nobody can tell is wrong.
    const preview = page.getByTestId("board-member-dialog-portrait-preview")
    await expect(preview).toBeVisible()
    await expect(preview).toHaveAttribute("src", /mock-\d+\.webp$/)

    const written = page.waitForRequest(
      (request) => request.method() === "PUT"
        && /\/boards\/9\/members\/92$/.test(new URL(request.url()).pathname),
    )
    await page.getByTestId("board-member-dialog-save").click()
    const body = JSON.parse((await written).postData() ?? "{}") as Record<string, unknown>

    // The save names where the bytes are stored rather than carrying them.
    expect(String(body.portrait)).toMatch(/mock-\d+\.webp$/)

    // And the slice draws the face now, where it drew nothing before.
    await expect(page.getByTestId("board-member-92").locator("img")).toHaveCount(1)
  })

  test("links a member to an account with the island's own picker", async ({page}) => {
    await asBoard(page)

    await openMember(page, 92)
    // Nothing attached: most people who have sat on a board never had an account here.
    await expect(page.getByTestId("board-member-dialog-attached")).toHaveCount(0)

    // The island's picker rather than Vuetify's, so the search is a plain field and the list
    // is drawn at the end of the document.
    await page.getByTestId("board-member-dialog-account-search").fill("Viktor")
    await page.getByTestId("board-member-dialog-account-2").click()
    await expect(page.getByTestId("board-member-dialog-attached")).toContainText("Viktor Petrov")

    const linked = page.waitForRequest(
      (request) => request.method() === "PUT"
        && /\/boards\/9\/members\/92\/member$/.test(new URL(request.url()).pathname),
    )
    await page.getByTestId("board-member-dialog-save").click()
    const body = JSON.parse((await linked).postData() ?? "{}") as Record<string, unknown>

    expect(body.userId).toBe(2)
  })

  test("detaches an account and leaves the member standing under its own name", async ({page}) => {
    await asBoard(page)

    await openMember(page, 91)
    await expect(page.getByTestId("board-member-dialog-attached")).toContainText("Emma Dokter")

    await page.getByTestId("board-member-dialog-detach").click()
    await expect(page.getByTestId("board-member-dialog-attached")).toHaveCount(0)
    // The name is the member's own, so detaching leaves it in the field it was in.
    await expect(page.getByTestId("board-member-dialog-name")).toHaveValue("Emma Dokter")

    const detached = page.waitForRequest(
      (request) => request.method() === "PUT"
        && /\/boards\/9\/members\/91\/member$/.test(new URL(request.url()).pathname),
    )
    await page.getByTestId("board-member-dialog-save").click()
    const body = JSON.parse((await detached).postData() ?? "{}") as Record<string, unknown>

    // A null member detaches, and the member is still on the page under its own name.
    expect(body.userId).toBeUndefined()
    await expect(page.getByTestId("board-member-91")).toContainText('Emma "Emmz" Dokter')
  })

  test("pre-fills a new member from the board's term, and records a handover part-way", async ({page}) => {
    await asBoard(page)

    await page.getByTestId("board-member-add").click()
    // The common case needs no typing: the board took office in the autumn of 2025 and is
    // still in office, so the member opens on the same stretch.
    await expect(page.getByTestId("board-member-dialog-start")).toHaveValue("2025-09-01")
    await expect(page.getByTestId("board-member-dialog-end")).toHaveValue("")

    await page.getByTestId("board-member-dialog-name").fill("Sylwia Nowak")
    await page.getByTestId("board-member-dialog-role").fill("Treasurer")
    // A handover part-way through the year, recorded truthfully rather than as a full one.
    await page.getByTestId("board-member-dialog-start").fill("2026-02-01")

    const written = page.waitForRequest(
      (request) => request.method() === "POST"
        && /\/boards\/9\/members$/.test(new URL(request.url()).pathname),
    )
    await page.getByTestId("board-member-dialog-save").click()
    const body = JSON.parse((await written).postData() ?? "{}") as Record<string, unknown>

    // What the cohort module reads to answer "was on the board that year".
    expect(body.startDate).toBe("2026-02-01")
  })

  test("carries a member's own dates into the dialog rather than the board's", async ({page}) => {
    await asBoard(page)

    await openMember(page, 91)

    await expect(page.getByTestId("board-member-dialog-start")).toHaveValue("2025-09-01")
    await expect(page.getByTestId("board-member-dialog-end")).toHaveValue("2026-08-31")
  })

  test("asks before a member is removed, and names what will go", async ({page}) => {
    await asBoard(page)

    await openMember(page, 91)
    await page.getByTestId("board-member-dialog-remove").click()

    // Named, so the question can be answered without remembering what was clicked. A blurb is
    // somebody's own words about themselves, so the question says it goes.
    const question = page.getByTestId("board-member-remove-dialog").getByTestId("confirm-question")
    await expect(question).toContainText('Emma "Emmz" Dokter')
    await expect(question).toContainText("Chair")
    await expect(question).toContainText("What they wrote about themselves goes with it.")

    const dropped = page.waitForRequest(
      (request) => request.method() === "DELETE"
        && /\/boards\/9\/members\/91$/.test(new URL(request.url()).pathname),
    )
    await page.getByTestId("board-member-remove-dialog").getByTestId("confirm-go").click()
    await dropped

    await expect(page.getByTestId("board-member-91")).toHaveCount(0)
    // The member beside it is untouched.
    await expect(page.getByTestId("board-member-92")).toContainText("Viktor Petrov")
  })

  test("keeps the member where a removal is declined", async ({page}) => {
    await asBoard(page)

    await openMember(page, 91)
    await page.getByTestId("board-member-dialog-remove").click()
    await page.getByTestId("board-member-remove-dialog").getByTestId("confirm-cancel").click()

    await expect(page.getByTestId("board-member-91")).toContainText('Emma "Emmz" Dokter')
  })

  test("leaves a member exactly as it was when the dialog is cancelled, picture and all", async ({page}) => {
    await asBoard(page)

    const face = page.getByTestId("board-member-91").locator("img")
    const before = await face.getAttribute("src")

    await openMember(page, 91)
    await page.getByTestId("board-member-dialog-name").fill("Somebody Else")
    await page.getByTestId("board-member-dialog-nickname").fill("Wrong")
    await page.getByTestId("board-member-dialog-role").fill("Nobody")
    // A picture is stored on choosing and reaches the member only on the save, so cancelling
    // leaves the member on the portrait it had.
    await page.getByTestId("board-member-dialog-portrait-file").setInputFiles({
      name: "wrong.png",
      mimeType: "image/png",
      buffer: Buffer.from(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==",
        "base64",
      ),
    })
    await expect(page.getByTestId("board-member-dialog-portrait-preview")).toHaveAttribute("src", /mock-/)

    await page.getByTestId("board-member-dialog-cancel").click()
    await expect(page.getByTestId("board-member-dialog")).toHaveCount(0)

    await expect(page.getByTestId("board-member-91")).toContainText('Emma "Emmz" Dokter')
    await expect(page.getByTestId("board-member-91")).toContainText("Chair")
    await expect(face).toHaveAttribute("src", before ?? "")

    // And reopening it shows the member as it stands rather than what was typed and abandoned.
    await openMember(page, 91)
    await expect(page.getByTestId("board-member-dialog-name")).toHaveValue("Emma Dokter")
    await expect(page.getByTestId("board-member-dialog-role")).toHaveValue("Chair")
  })

  test("says why a save was refused, and keeps what was typed", async ({page}) => {
    await asBoard(page)

    // The sdk hands a refusal back as a body rather than throwing, so a dialog that only read
    // `data` would close on a save that never happened.
    await page.route("**/boards/9/members/91", async (route) => {
      if (route.request().method() !== "PUT") return route.fallback()
      await route.fulfill({
        status: 400,
        contentType: "application/json",
        body: JSON.stringify({detail: "A member cannot end before it began."}),
      })
    })

    await openMember(page, 91)
    await page.getByTestId("board-member-dialog-end").fill("2024-01-01")
    await page.getByTestId("board-member-dialog-save").click()

    await expect(page.getByTestId("board-member-dialog-failure"))
      .toHaveText("A member cannot end before it began.")
    // Still open, and still holding what was typed: the objection is something to act on.
    await expect(page.getByTestId("board-member-dialog-end")).toHaveValue("2024-01-01")
    await expect(page.getByTestId("board-member-dialog-name")).toHaveValue("Emma Dokter")
  })
})
