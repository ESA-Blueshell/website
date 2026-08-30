import {Buffer} from "node:buffer"
import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

/**
 * Putting pictures on the pages without a deploy.
 *
 * The point of the upload work is that a banner and an icon reach the public page from a file
 * chooser rather than from the frontend's assets directory, so each test here ends by reading
 * the page a visitor would see rather than the form that changed it.
 *
 * Pictures are drawn in the slices and nowhere else: a game's in its slice on the index, a
 * team's in its slice on the game's page. Choosing one stores it; the dialog's Save is what
 * puts it on the record. That is why these tests save before they look, and why there is a
 * test that cancels instead.
 */
const GAME_PAGE = "/esports/valorant"

/** A one-pixel PNG, which is the smallest thing that is genuinely the type it claims. */
const PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
  "base64",
)

const choose = async (page: import("@playwright/test").Page, testid: string) => {
  await page.getByTestId(`${testid}-file`).setInputFiles({
    name: "banner.png",
    mimeType: "image/png",
    buffer: PNG,
  })
}

/**
 * An image that decoded, which a url pointing at the wrong origin never does.
 *
 * Asked of the browser rather than measured from `naturalWidth`: once an image is picked out
 * of a `srcset`, that width is divided by the density the chosen candidate implies, so a
 * one-pixel stand-in chosen at 320w reports zero and a decoded image reads as a broken one.
 */
const decoded = async (image: import("@playwright/test").Locator) => {
  await expect(image).toBeVisible()
  return image.evaluate((img: HTMLImageElement) => img.decode().then(() => true).catch(() => false))
}

const loaded = async (page: import("@playwright/test").Page, testid: string) =>
  decoded(page.getByTestId(testid))

const openLineup = async (page: import("@playwright/test").Page) => {
  await page.getByTestId("team-roster-1").hover()
  await page.getByTestId("team-roster-edit-1").click()
  await expect(page.getByTestId("lineup-editor")).toBeVisible()
}

test.describe("banners and icons", () => {
  test("a team's banner is chosen, saved and shows without a deploy", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await expect(page.getByTestId("lineup-team-banner-empty")).toBeVisible()
    await choose(page, "lineup-team-banner")

    const preview = page.getByTestId("lineup-team-banner-preview")
    await expect(preview).toHaveAttribute("src", /\/files\/public\/team-banners\/[^/]+\.webp/)
    await expect.poll(() => loaded(page, "lineup-team-banner-preview")).toBe(true)

    await page.getByTestId("lineup-save").click()
    await expect(page.getByTestId("lineup-editor")).toBeHidden()

    // The page a visitor reads, rather than the form that changed it. The slice behind the
    // team draws the banner, and is offered the widths it is stored at. Reached through the
    // slice rather than by a testid of its own: the slices are found by a prefix selector, and
    // an image named from the same prefix would be caught by it.
    const behind = page.getByTestId("team-roster-1").locator("img")
    await expect.poll(() => decoded(behind)).toBe(true)
    await expect(behind).toHaveAttribute("srcset", /320w/)
    await expect(behind).toHaveAttribute("width", "640")

    await openLineup(page)
    await expect.poll(() => loaded(page, "lineup-team-banner-preview")).toBe(true)
  })

  /**
   * The whole point of holding the picture until the save: Cancel discards it along with
   * everything else, rather than leaving a picture applied and the rest of the form thrown
   * away.
   */
  test("a chosen banner is discarded along with the rest when the dialog is cancelled", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await page.getByTestId("lineup-team-name").fill("A name nobody keeps")
    await choose(page, "lineup-team-banner")
    await expect(page.getByTestId("lineup-team-banner-preview")).toBeVisible()

    await page.getByTestId("lineup-cancel").click()
    await expect(page.getByTestId("lineup-editor")).toBeHidden()

    await openLineup(page)
    await expect(page.getByTestId("lineup-team-banner-empty")).toBeVisible()
    await expect(page.getByTestId("lineup-team-name")).toHaveValue("BS Waterboarders")
  })

  test("a chosen banner is taken away again before it is ever saved", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await choose(page, "lineup-team-banner")
    await expect(page.getByTestId("lineup-team-banner-preview")).toBeVisible()

    await page.getByTestId("lineup-team-banner-clear").click()

    await expect(page.getByTestId("lineup-team-banner-empty")).toBeVisible()
    await expect(page.getByTestId("lineup-team-banner-preview")).toBeHidden()
  })

  test("a roster entry carries its own picture", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await expect(page.getByTestId("lineup-icon-0-empty")).toBeVisible()
    await choose(page, "lineup-icon-0")

    await expect.poll(() => loaded(page, "lineup-icon-0-preview")).toBe(true)
    // The entry beside it is untouched: an icon belongs to one place on one roster.
    await expect(page.getByTestId("lineup-icon-1-empty")).toBeVisible()

    await page.getByTestId("lineup-save").click()
    await expect(page.getByTestId("lineup-editor")).toBeHidden()

    await openLineup(page)
    await expect.poll(() => loaded(page, "lineup-icon-0-preview")).toBe(true)
    await expect(page.getByTestId("lineup-icon-1-empty")).toBeVisible()
  })

  /**
   * A row nobody has saved yet can carry one now: the picture is named by the write that
   * creates the entry, so there is nothing to wait for.
   */
  test("a row added in the dialog can carry a picture before it is saved", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    const before = await page.getByTestId(/^lineup-icon-\d+$/).count()
    await page.getByTestId("lineup-add").click()

    await expect(page.getByTestId(/^lineup-icon-\d+$/)).toHaveCount(before + 1)
    await choose(page, `lineup-icon-${before}`)
    await expect.poll(() => loaded(page, `lineup-icon-${before}-preview`)).toBe(true)
  })

  test("a game's banner is chosen on the index and reaches the slice it is drawn in", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/esports")

    await page.getByTestId("esports-game-VALORANT").hover()
    await page.getByTestId("esports-game-edit-VALORANT").click()
    await expect(page.getByTestId("game-dialog")).toBeVisible()

    await expect(page.getByTestId("game-dialog-banner-empty")).toBeVisible()
    await choose(page, "game-dialog-banner")
    await expect(page.getByTestId("game-dialog-banner-preview")).toBeVisible()
    await page.getByTestId("game-dialog-save").click()

    // Drawn, not merely present: the api answers on another origin than the page, so a url
    // the frontend failed to resolve still sets an `src` and still renders nothing.
    const slice = page.getByTestId("esports-game-VALORANT").locator("img").first()
    await expect.poll(() => decoded(slice)).toBe(true)

    // Every width is resolved against the api rather than the page — a `srcset` entry
    // pointing at the frontend draws nothing.
    const srcset = await slice.getAttribute("srcset")
    expect(srcset).toContain("320w")
    for (const entry of (srcset ?? "").split(",")) {
      expect(entry.trim()).toMatch(/^http.*\/files\/public\/game-banners\/.* \d+w$/)
    }
  })

  test("a game's icon is chosen on the index and is drawn beside its name", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/esports")

    await page.getByTestId("esports-game-VALORANT").hover()
    await page.getByTestId("esports-game-edit-VALORANT").click()
    await expect(page.getByTestId("game-dialog")).toBeVisible()

    await expect(page.getByTestId("game-dialog-icon-empty")).toBeVisible()
    await choose(page, "game-dialog-icon")
    await expect(page.getByTestId("game-dialog-icon-preview")).toBeVisible()
    await page.getByTestId("game-dialog-save").click()
    await expect(page.getByTestId("game-dialog")).toHaveCount(0)

    // Drawn, not merely present. The icon is the only picture this game has, so it is the one
    // image in the slice; a url the frontend failed to resolve would still set an `src`.
    const icon = page.getByTestId("esports-game-VALORANT").locator("img").first()
    await expect.poll(() => decoded(icon)).toBe(true)
    await expect(icon).toHaveAttribute("src", /\/files\/public\/game-icons\/[^/]+\.webp/)

    // The widths an icon is stored at, which are not the widths a banner is stored at: 128
    // and 256 rather than 320 and 640. Naming a banner's here would pass against a stub that
    // hands every kind the same ladder and fail against the api.
    const srcset = await icon.getAttribute("srcset")
    expect(srcset).toContain("128w")
    expect(srcset).toContain("256w")
    expect(srcset).not.toContain("320w")
    for (const entry of (srcset ?? "").split(",")) {
      expect(entry.trim()).toMatch(/^http.*\/files\/public\/game-icons\/.* \d+w$/)
    }
  })

  test("a team's icon is chosen in the line-up and is drawn beside its name", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await expect(page.getByTestId("lineup-team-icon-empty")).toBeVisible()
    await choose(page, "lineup-team-icon")
    await expect(page.getByTestId("lineup-team-icon-preview")).toBeVisible()

    await page.getByTestId("lineup-save").click()
    await expect(page.getByTestId("lineup-editor")).toBeHidden()

    const icon = page.getByTestId("team-roster-1").locator("img").first()
    await expect.poll(() => decoded(icon)).toBe(true)
    await expect(icon).toHaveAttribute("src", /\/files\/public\/team-icons\/[^/]+\.webp/)
    await expect(icon).toHaveAttribute("srcset", /128w/)
    await expect(icon).toHaveAttribute("srcset", /256w/)
  })

  test("a game's chosen icon is discarded when the dialog is cancelled", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/esports")

    await page.getByTestId("esports-game-VALORANT").hover()
    await page.getByTestId("esports-game-edit-VALORANT").click()
    await choose(page, "game-dialog-icon")
    await expect(page.getByTestId("game-dialog-icon-preview")).toBeVisible()

    await page.getByTestId("game-dialog-cancel").click()
    await expect(page.getByTestId("game-dialog")).toHaveCount(0)

    await page.getByTestId("esports-game-VALORANT").hover()
    await page.getByTestId("esports-game-edit-VALORANT").click()
    await expect(page.getByTestId("game-dialog-icon-empty")).toBeVisible()
  })

  /**
   * A team's icon is optional and nothing ships one, so the ordinary case is a slice with a
   * name and no logo beside it. A broken image here would be the failure this replaces.
   */
  test("a team nobody has given an icon draws none", async ({page}) => {
    await installApiMocks(page)
    await page.goto(GAME_PAGE)

    await expect(page.getByTestId("team-roster-1")).toBeVisible()
    await expect(page.getByTestId("team-roster-1").locator("img")).toHaveCount(0)
  })

  test("a chosen icon is discarded along with the rest when the dialog is cancelled", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await choose(page, "lineup-team-icon")
    await expect(page.getByTestId("lineup-team-icon-preview")).toBeVisible()

    await page.getByTestId("lineup-cancel").click()
    await expect(page.getByTestId("lineup-editor")).toBeHidden()

    await openLineup(page)
    await expect(page.getByTestId("lineup-team-icon-empty")).toBeVisible()
  })

  test("the game's own page draws no picture behind its header", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    // The header is the accent and nothing else; pictures belong to the slices.
    await expect(page.getByTestId("esports-page-banner")).toHaveCount(0)
    await expect(page.getByTestId("esports-banners-open")).toHaveCount(0)
  })

  test("a visitor is offered none of this", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports")

    await expect(page.getByTestId("esports-game-edit-VALORANT")).toBeHidden()
  })
})
