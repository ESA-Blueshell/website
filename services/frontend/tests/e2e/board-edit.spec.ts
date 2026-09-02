import {Buffer} from "node:buffer"
import type {Locator, Page} from "@playwright/test"
import {expect, test} from "./test"
import {installApiMocks, loginAsBoard, loginAsMember, preferLightTheme} from "./mocks"

/**
 * A board written down and corrected on the page it is read on.
 *
 * The affordances are offered by the same rule the api enforces, so what is asserted here is
 * that they appear for somebody who may take them up, that a save carries what was typed, that
 * a cancelled dialog changes nothing at all, and that a refusal arrives in words. None of it is
 * a guard: the api refuses what it refuses whether or not a pencil was drawn.
 */

/** A photograph as the api answers with one, at the widths a board photo is stored at. */
const photo = (name: string) => ({
  path: `board-photos/${name}.webp`,
  url: `/files/public/board-photos/${name}.webp`,
  width: 2560,
  height: 1440,
  renditions: [320, 640, 960, 1280, 1920, 2560].map((width) => ({
    url: `/files/public/board-photos/${name}-${width}.webp`,
    width,
  })),
})

const seat = (id: number, boardId: number, name: string, role: string) => ({
  id, boardId, userId: null, role, name, nickname: null,
  description: null, image: null, portrait: null,
  startDate: "2025-09-01", endDate: "2026-08-31", version: 0,
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
})

const board = (over: Record<string, unknown>) => ({
  id: 1, number: 1, name: null, candidate: "Board", cheer: null, accent: null, description: null,
  startDate: "2017-09-01", endDate: "2018-08-31", image: null, photo: null, version: 0,
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z", members: [],
  ...over,
})

/**
 * The line as the association has it, with the colours the boards actually chose.
 *
 * Two of them are pale and one is deep, which is what makes the ink rule testable: a fill is
 * painted as it was chosen and what adapts is whatever is drawn on top of it. Board X is
 * elected and not yet sitting, has no photograph and nobody seated, so it is the board an
 * empty removal can be tried on; board IX is in office and holds three seats, so it is the
 * one a removal is refused for.
 */
const history = [
  board({
    id: 10, number: 10, name: "Rainbow road", accent: "#65c6cd",
    startDate: "2099-09-01", endDate: "2100-08-31", members: [],
  }),
  board({
    id: 9, number: 9, name: "Eeveelutions", cheer: "RNG, Be With Me!",
    startDate: "2025-09-01", endDate: null, photo: photo("board9"),
    members: [
      seat(91, 9, "Emma Dokter", "Chair"),
      seat(92, 9, "Viktor Petrov", "Treasurer"),
      seat(93, 9, "Roos Kruk", "Commissioner of Internal Affairs"),
    ],
  }),
  board({
    id: 7, number: 7, name: "Overcooked", cheer: "Krijg de tering!", accent: "#b00b69",
    startDate: "2023-09-01", endDate: "2024-08-31", photo: photo("board7"),
    members: [seat(71, 7, "Thijs Lieverse", "Chairman")],
  }),
  board({
    id: 6, number: 6, name: "Don't starve together", cheer: "Never alone!", accent: "#eaa4b6",
    startDate: "2022-09-01", endDate: "2023-08-31",
    members: [seat(61, 6, "Anne Schrader", "Chairman")],
  }),
  board({
    id: 4, number: 4, name: null, startDate: "2020-09-01", endDate: "2021-08-31",
    members: [seat(41, 4, "Bram Bakker", "Chairman")],
  }),
]

/** A one-pixel PNG, which is the smallest thing that is genuinely the type it claims. */
const PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
  "base64",
)

/** What a person does: bring the pointer to the board, then take up what it reveals. */
const openEditor = async (page: Page, number: number) => {
  await page.getByTestId(`board-node-${number}`).hover()
  await page.getByTestId(`board-edit-${number}`).click()
  await expect(page.getByTestId("board-dialog")).toBeVisible()
}

const choosePhoto = (page: Page) =>
  page.getByTestId("board-dialog-photo-file").setInputFiles({
    name: "board.png",
    mimeType: "image/png",
    buffer: PNG,
  })

const styleOf = (target: Locator, property: string) =>
  target.evaluate(
    (node, name) => getComputedStyle(node).getPropertyValue(name),
    property,
  )

/**
 * A colour as it is really painted, in sRGB channels.
 *
 * Asked of a canvas rather than parsed out of the computed value: a colour mixed in oklab is
 * serialised as `oklab(...)`, so reading the numbers out of the string reads three components
 * of another space as if they were channels — which is a contrast measurement of nothing.
 * Painting one pixel and reading it back is the browser's own conversion.
 */
const painted = (page: Page, colour: string) =>
  page.evaluate((value) => {
    const canvas = document.createElement("canvas")
    canvas.width = 1
    canvas.height = 1
    const ctx = canvas.getContext("2d")!
    ctx.fillStyle = value
    ctx.fillRect(0, 0, 1, 1)
    const [r, g, b] = ctx.getImageData(0, 0, 1, 1).data
    return [r, g, b] as [number, number, number]
  }, colour)

/** How readable one colour is on another, as WCAG counts it. */
const contrast = (ink: [number, number, number], ground: [number, number, number]) => {
  const luminance = ([r, g, b]: [number, number, number]) => {
    const linear = (channel: number) => {
      const scaled = channel / 255
      return scaled <= 0.04045 ? scaled / 12.92 : ((scaled + 0.055) / 1.055) ** 2.4
    }
    return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
  }
  const [high, low] = [luminance(ink), luminance(ground)].sort((a, b) => b - a)
  return (high! + 0.05) / (low! + 0.05)
}

test.describe("a board is corrected on the page it is read on", () => {
  test("a visitor is offered none of it", async ({page}) => {
    await installApiMocks(page, {boards: history})

    await page.goto("/board")
    await expect(page.getByTestId("board-eyebrow")).toHaveText("BOARD IX · 2025-2026")

    // Not hidden and reachable: not there at all. The api still refuses a visitor's write.
    await expect(page.locator('[data-testid^="board-edit-"]')).toHaveCount(0)
    await expect(page.getByTestId("board-add")).toHaveCount(0)
    await expect(page.getByTestId("board-dialog")).toHaveCount(0)

    await page.goto("/board?board=4")
    // The board with no photograph is the one where an editor is offered a way to add one.
    await expect(page.getByTestId("board-numeral")).toHaveText("IV")
    await expect(page.getByTestId("board-band-add-photo")).toHaveCount(0)
  })

  test("a member who is not on the board is offered none of it either", async ({page}) => {
    await installApiMocks(page, {boards: history})
    await loginAsMember(page.context())

    await page.goto("/board")
    await expect(page.getByTestId("board-eyebrow")).toHaveText("BOARD IX · 2025-2026")

    await expect(page.locator('[data-testid^="board-edit-"]')).toHaveCount(0)
    await expect(page.getByTestId("board-add")).toHaveCount(0)
  })

  test("every board is reachable, and every one of them can be corrected", async ({page}) => {
    await installApiMocks(page, {boards: history})
    await loginAsBoard(page.context())

    await page.goto("/board")

    // Complete or not, sitting or elected: a board has to be reachable before it can be
    // worked on, so the strip carries all five and offers each of them a pencil.
    for (const number of [4, 6, 7, 9, 10]) {
      await expect(page.getByTestId(`board-node-${number}`)).toHaveCount(1)
      await expect(page.getByTestId(`board-edit-${number}`)).toHaveCount(1)
    }
    await expect(page.getByTestId("board-add")).toBeVisible()
  })

  test("the number of a new board is suggested rather than remembered", async ({page}) => {
    await installApiMocks(page, {boards: history})
    await loginAsBoard(page.context())

    await page.goto("/board")
    await page.getByTestId("board-add").click()

    await expect(page.getByTestId("board-dialog")).toBeVisible()
    // Ten boards have not all been recorded here, but the highest is X, so the next is XI.
    await expect(page.getByTestId("board-dialog-number")).toHaveValue("11")
    await expect(page.getByTestId("board-dialog-suggested")).toContainText("Board XI")
  })

  test("writes a board down from the end of the timeline", async ({page}) => {
    await installApiMocks(page, {boards: history})
    await loginAsBoard(page.context())

    await page.goto("/board")
    await page.getByTestId("board-add").click()

    await page.getByTestId("board-dialog-name").fill("Rocket surgery")
    await page.getByTestId("board-dialog-cheer").fill("To the moon!")
    await page.getByTestId("board-dialog-accent").fill("#65c6cd")
    await page.getByTestId("board-dialog-description").fill("The year the lounge opened.")
    await page.getByTestId("board-dialog-start").fill("2100-09-01")
    await page.getByTestId("board-dialog-end").fill("2101-08-31")

    const written = page.waitForRequest(
      (request) => request.method() === "POST" && /\/boards$/.test(new URL(request.url()).pathname),
    )
    await page.getByTestId("board-dialog-save").click()

    expect(JSON.parse((await written).postData() ?? "{}")).toMatchObject({
      number: 11,
      name: "Rocket surgery",
      cheer: "To the moon!",
      accent: "#65c6cd",
      description: "The year the lounge opened.",
      startDate: "2100-09-01",
      endDate: "2101-08-31",
    })

    // Somebody who has just described a board is shown it, and its stop joins the line.
    await expect(page).toHaveURL(/\?board=11$/)
    await expect(page.getByTestId("board-eyebrow")).toHaveText("BOARD XI · 2100-2101")
    await expect(page.getByTestId("board-name")).toHaveText("Rocket surgery")
    await expect(page.getByTestId("board-node-11")).toContainText("Rocket surgery")
  })

  test("corrects a board's number, name, cheer, colour, description and dates", async ({page}) => {
    await installApiMocks(page, {boards: history})
    await loginAsBoard(page.context())

    await page.goto("/board?board=7")
    await openEditor(page, 7)

    // Opening it fills the form from the board as it stands, colour included.
    await expect(page.getByTestId("board-dialog-name")).toHaveValue("Overcooked")
    await expect(page.getByTestId("board-dialog-cheer")).toHaveValue("Krijg de tering!")
    await expect(page.getByTestId("board-dialog-accent")).toHaveValue("#b00b69")
    await expect(page.getByTestId("board-dialog-start")).toHaveValue("2023-09-01")
    await expect(page.getByTestId("board-dialog-end")).toHaveValue("2024-08-31")

    await page.getByTestId("board-dialog-number").fill("8")
    await page.getByTestId("board-dialog-name").fill("Overcooked 2")
    await page.getByTestId("board-dialog-cheer").fill("Krijg de tering, opnieuw!")
    await page.getByTestId("board-dialog-accent").fill("#9100d0")
    await page.getByTestId("board-dialog-description").fill("The year the kitchen burned.")
    await page.getByTestId("board-dialog-start").fill("2023-09-15")
    await page.getByTestId("board-dialog-end").fill("2024-09-14")

    const saved = page.waitForRequest(
      (request) => request.method() === "PUT" && /\/boards\/7$/.test(new URL(request.url()).pathname),
    )
    await page.getByTestId("board-dialog-save").click()

    expect(JSON.parse((await saved).postData() ?? "{}")).toMatchObject({
      number: 8,
      name: "Overcooked 2",
      cheer: "Krijg de tering, opnieuw!",
      accent: "#9100d0",
      description: "The year the kitchen burned.",
      startDate: "2023-09-15",
      endDate: "2024-09-14",
    })

    // The correction is shown where it was made: the board's number changed, so the page
    // follows it rather than falling back to the board in office.
    await expect(page).toHaveURL(/\?board=8$/)
    await expect(page.getByTestId("board-eyebrow")).toHaveText("BOARD VIII · 2023-2024")
    await expect(page.getByTestId("board-name")).toHaveText("Overcooked 2")
    await expect(page.getByTestId("board-cheer")).toHaveText("Krijg de tering, opnieuw!")
    await expect(page.getByTestId("board-description")).toHaveText("The year the kitchen burned.")
  })

  test("the colour field shows a live swatch, in the ink that reads on it", async ({page}) => {
    await installApiMocks(page, {boards: history})
    await loginAsBoard(page.context())

    await page.goto("/board?board=6")
    await openEditor(page, 6)

    const swatch = page.getByTestId("board-dialog-swatch")
    // The colour the board already carries, painted as it was chosen. It is pale, so what is
    // drawn on it is the near-black ink.
    await expect(page.getByTestId("board-dialog-accent")).toHaveValue("#eaa4b6")
    expect(await styleOf(swatch, "background-color")).toBe("rgb(234, 164, 182)")
    expect(await styleOf(swatch, "color")).toBe("rgb(28, 28, 28)")

    // A deep fill takes the other ink, and the swatch follows the field as it is typed.
    await page.getByTestId("board-dialog-accent").fill("#b00b69")
    await expect.poll(() => styleOf(swatch, "background-color")).toBe("rgb(176, 11, 105)")
    expect(await styleOf(swatch, "color")).toBe("rgb(244, 246, 248)")

    // Left blank, the board is drawn in the association's blue — which the swatch says.
    await page.getByTestId("board-dialog-accent").fill("")
    await expect.poll(() => styleOf(swatch, "background-color")).toBe("rgb(51, 135, 250)")
    expect(await styleOf(swatch, "color")).toBe("rgb(28, 28, 28)")
  })

  test("a pale colour still reads in light mode, where it is the ink that has to move", async ({page}) => {
    await installApiMocks(page, {boards: history})
    await preferLightTheme(page)

    await page.goto("/board?board=6")

    const cheer = page.getByTestId("board-cheer")
    await expect(cheer).toHaveText("Never alone!")
    const ink = await painted(page, await styleOf(cheer, "color"))
    const ground = await painted(page, await styleOf(page.getByTestId("board-island"), "background-color"))

    // #eaa4b6 is 1.57:1 against the light ground: drawn raw the cheer would be invisible, so
    // the ink is mixed towards the near-black the light half reads as.
    expect(ink).not.toEqual([234, 164, 182])
    expect(contrast(ink, ground)).toBeGreaterThanOrEqual(3)
  })

  test("a deep colour still reads in dark mode, where the ink moves the other way", async ({page}) => {
    await installApiMocks(page, {boards: history})

    await page.goto("/board?board=7")

    const cheer = page.getByTestId("board-cheer")
    await expect(cheer).toHaveText("Krijg de tering!")
    const ink = await painted(page, await styleOf(cheer, "color"))
    const ground = await painted(page, await styleOf(page.getByTestId("board-island"), "background-color"))

    // #b00b69 is 2.51:1 against the dark ground, so here the mix goes the other way: towards
    // the near-white the dark half reads as. One formula, two directions.
    expect(ink).not.toEqual([176, 11, 105])
    expect(contrast(ink, ground)).toBeGreaterThanOrEqual(3)
  })

  test("uploads a photograph from the dialog, and shows it before it is saved", async ({page}) => {
    await installApiMocks(page, {boards: history})
    await loginAsBoard(page.context())

    await page.goto("/board?board=4")
    await openEditor(page, 4)

    await expect(page.getByTestId("board-dialog-photo-empty")).toBeVisible()
    await choosePhoto(page)

    // Shown before it is committed, so a wrong crop is visible while it can still be changed.
    const preview = page.getByTestId("board-dialog-photo-preview")
    await expect(preview).toHaveAttribute("src", /\/files\/public\/board-photos\/[^/]+\.webp/)
    const chosen = (await preview.getAttribute("src"))!

    const saved = page.waitForRequest(
      (request) => request.method() === "PUT" && /\/boards\/4$/.test(new URL(request.url()).pathname),
    )
    await page.getByTestId("board-dialog-save").click()

    const body = JSON.parse((await saved).postData() ?? "{}") as {photo?: string}
    expect(chosen).toContain(body.photo!)

    // The band a visitor reads draws it, at the widths it is stored at.
    const band = page.getByTestId("board-photo")
    await expect(band).toBeVisible()
    await expect(band).toHaveAttribute("srcset", /320w/)
  })

  test("replaces a photograph that is wrong", async ({page}) => {
    await installApiMocks(page, {boards: history})
    await loginAsBoard(page.context())

    await page.goto("/board?board=7")
    await openEditor(page, 7)

    const preview = page.getByTestId("board-dialog-photo-preview")
    await expect(preview).toHaveAttribute("src", /board7\.webp$/)
    await expect(page.getByTestId("board-dialog-photo-replace")).toHaveCount(1)

    await choosePhoto(page)
    await expect(preview).not.toHaveAttribute("src", /board7\.webp$/)

    const saved = page.waitForRequest(
      (request) => request.method() === "PUT" && /\/boards\/7$/.test(new URL(request.url()).pathname),
    )
    await page.getByTestId("board-dialog-save").click()

    const body = JSON.parse((await saved).postData() ?? "{}") as {photo?: string}
    expect(body.photo).not.toBe("board-photos/board7.webp")
    await expect(page.getByTestId("board-photo")).not.toHaveAttribute("src", /board7\.webp$/)
  })

  test("offers a photograph in the band of a board that has none", async ({page}) => {
    await installApiMocks(page, {boards: history})
    await loginAsBoard(page.context())

    await page.goto("/board?board=4")

    // Half the history has no photograph, so the way to one is in the band standing in for it.
    const add = page.getByTestId("board-band-add-photo")
    await expect(add).toBeVisible()
    await add.click()

    await expect(page.getByTestId("board-dialog")).toBeVisible()
    await expect(page.getByTestId("board-dialog-number")).toHaveValue("4")
    await expect(page.getByTestId("board-dialog-photo-empty")).toBeVisible()

    // A board that has one is corrected in the dialog, beside the crop, rather than here.
    await page.goto("/board?board=7")
    await expect(page.getByTestId("board-photo")).toBeVisible()
    await expect(page.getByTestId("board-band-add-photo")).toHaveCount(0)
  })

  test("cancelling leaves the board exactly as it was, picture and all", async ({page}) => {
    await installApiMocks(page, {boards: history})
    await loginAsBoard(page.context())

    let writes = 0
    await page.route("**/boards/**", async (route) => {
      if (route.request().method() !== "GET") writes += 1
      await route.fallback()
    })

    await page.goto("/board?board=7")
    await openEditor(page, 7)

    await page.getByTestId("board-dialog-name").fill("A name nobody keeps")
    await page.getByTestId("board-dialog-accent").fill("#000000")
    await choosePhoto(page)
    await expect(page.getByTestId("board-dialog-photo-preview")).not.toHaveAttribute("src", /board7/)

    await page.getByTestId("board-dialog-cancel").click()
    await expect(page.getByTestId("board-dialog")).toBeHidden()

    // Nothing was written: the bytes are in storage and the board is untouched, which is what
    // holding a picture until the save is for.
    expect(writes).toBe(0)
    await expect(page.getByTestId("board-name")).toHaveText("Overcooked")
    await expect(page.getByTestId("board-photo")).toHaveAttribute("src", /board7\.webp$/)

    // And reopening it shows the board rather than what was abandoned.
    await openEditor(page, 7)
    await expect(page.getByTestId("board-dialog-name")).toHaveValue("Overcooked")
    await expect(page.getByTestId("board-dialog-accent")).toHaveValue("#b00b69")
    await expect(page.getByTestId("board-dialog-photo-preview")).toHaveAttribute("src", /board7\.webp$/)
  })

  test("refuses to remove a board that still has seats, and says how many are in the way", async ({page}) => {
    await installApiMocks(page, {boards: history})
    await loginAsBoard(page.context())

    await page.goto("/board")
    await openEditor(page, 9)

    await page.getByTestId("board-dialog-remove").click()
    const asking = page.getByTestId("board-remove-dialog")
    await expect(asking).toBeVisible()
    // Asked first, because a board's seats are nine people's place in the history.
    await expect(asking.getByTestId("confirm-question")).toContainText("holds 3 seats")

    await asking.getByTestId("confirm-go").click()

    // The api refused it and the refusal is what a reader is shown, in the api's own count.
    await expect(asking.getByTestId("confirm-failure")).toContainText("still has 3 seats on it")
    await expect(asking.getByTestId("confirm-failure")).toContainText("Remove the seats first")
    await expect(page.getByTestId("board-node-9")).toHaveCount(1)
  })

  test("removes a board nobody is seated on", async ({page}) => {
    await installApiMocks(page, {boards: history})
    await loginAsBoard(page.context())

    await page.goto("/board?board=10")
    await expect(page.getByTestId("board-eyebrow")).toHaveText("BOARD X · 2099-2100")
    await openEditor(page, 10)

    await page.getByTestId("board-dialog-remove").click()
    const asking = page.getByTestId("board-remove-dialog")
    await expect(asking.getByTestId("confirm-question")).toContainText("holds no seats")

    const removed = page.waitForRequest(
      (request) => request.method() === "DELETE" && /\/boards\/10$/.test(new URL(request.url()).pathname),
    )
    await asking.getByTestId("confirm-go").click()
    await removed

    // Its stop goes with it, the url stops naming it, and the page falls back to the board in
    // office rather than to a blank page.
    await expect(page.getByTestId("board-node-10")).toHaveCount(0)
    await expect(page).toHaveURL(/\/board$/)
    await expect(page.getByTestId("board-eyebrow")).toHaveText("BOARD IX · 2025-2026")
  })

  test("a failed save says why", async ({page}) => {
    await installApiMocks(page, {boards: history})
    await loginAsBoard(page.context())

    await page.goto("/board?board=4")
    await openEditor(page, 4)

    // A board's number is its identity, and board IX holds this one.
    await page.getByTestId("board-dialog-number").fill("9")
    await page.getByTestId("board-dialog-save").click()

    await expect(page.getByTestId("board-dialog-failure")).toContainText("Board 9 already exists")
    // The dialog stands with what was typed, so the number can be corrected without retyping
    // the rest of the board.
    await expect(page.getByTestId("board-dialog")).toBeVisible()
    await expect(page.getByTestId("board-dialog-number")).toHaveValue("9")
  })
})
