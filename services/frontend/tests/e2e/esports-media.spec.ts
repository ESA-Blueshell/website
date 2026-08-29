import {Buffer} from "node:buffer"
import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

/**
 * Putting pictures on the pages without a deploy.
 *
 * The point of the upload work is that a poster, an icon and a banner reach the public page
 * from a file chooser rather than from the frontend's assets directory, so each test here
 * ends by reading the page a visitor would see rather than the form that changed it.
 *
 * Choosing a picture stores it; the dialog's Save is what puts it on the team or the person.
 * That is why the poster and icon tests save before they look, and why there is a test that
 * cancels instead.
 */
const GAME_PAGE = "/esports/valorant"

/** A one-pixel PNG, which is the smallest thing that is genuinely the type it claims. */
const PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
  "base64",
)

const choose = async (page: import("@playwright/test").Page, testid: string) => {
  await page.getByTestId(`${testid}-file`).setInputFiles({
    name: "poster.png",
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

test.describe("posters, icons and banners", () => {
  test("a team's poster is chosen, saved and shows without a deploy", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await expect(page.getByTestId("lineup-team-poster-empty")).toBeVisible()
    await choose(page, "lineup-team-poster")

    const preview = page.getByTestId("lineup-team-poster-preview")
    await expect(preview).toHaveAttribute("src", /\/files\/public\/team-posters\/[^/]+\.webp/)
    await expect.poll(() => loaded(page, "lineup-team-poster-preview")).toBe(true)

    await page.getByTestId("lineup-save").click()
    await expect(page.getByTestId("lineup-editor")).toBeHidden()

    // The page a visitor reads, rather than the form that changed it. The slice behind the
    // team draws the poster, and is offered the widths it is stored at. Reached through the
    // slice rather than by a testid of its own: the slices are found by a prefix selector, and
    // an image named from the same prefix would be caught by it.
    const behind = page.getByTestId("team-roster-1").locator("img")
    await expect.poll(() => decoded(behind)).toBe(true)
    await expect(behind).toHaveAttribute("srcset", /320w/)
    await expect(behind).toHaveAttribute("width", "640")

    await openLineup(page)
    await expect.poll(() => loaded(page, "lineup-team-poster-preview")).toBe(true)
  })

  /**
   * The whole point of holding the picture until the save: Cancel discards it along with
   * everything else, rather than leaving a picture applied and the rest of the form thrown
   * away.
   */
  test("a chosen poster is discarded along with the rest when the dialog is cancelled", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await page.getByTestId("lineup-team-name").fill("A name nobody keeps")
    await choose(page, "lineup-team-poster")
    await expect(page.getByTestId("lineup-team-poster-preview")).toBeVisible()

    await page.getByTestId("lineup-cancel").click()
    await expect(page.getByTestId("lineup-editor")).toBeHidden()

    await openLineup(page)
    await expect(page.getByTestId("lineup-team-poster-empty")).toBeVisible()
    await expect(page.getByTestId("lineup-team-name")).toHaveValue("BS Waterboarders")
  })

  test("a chosen poster is taken away again before it is ever saved", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await choose(page, "lineup-team-poster")
    await expect(page.getByTestId("lineup-team-poster-preview")).toBeVisible()

    await page.getByTestId("lineup-team-poster-clear").click()

    await expect(page.getByTestId("lineup-team-poster-empty")).toBeVisible()
    await expect(page.getByTestId("lineup-team-poster-preview")).toBeHidden()
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

  test("a banner set for the game reaches the page behind the header", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await expect(page.getByTestId("esports-page-banner")).toBeHidden()

    await page.getByTestId("esports-banners-open").click()
    await expect(page.getByTestId("banner-dialog")).toBeVisible()
    await choose(page, "banner-game")

    await expect(page.getByTestId("banner-game-preview")).toBeVisible()
    await page.keyboard.press("Escape")

    // Drawn, not merely present: the api answers on another origin than the page, so a url
    // the frontend failed to resolve still sets an `src` and still renders nothing.
    await expect.poll(() => loaded(page, "esports-page-banner")).toBe(true)

    // The widths it is stored at reach the markup, and every one of them is resolved against
    // the api rather than the page — a `srcset` entry pointing at the frontend draws nothing.
    const srcset = await page.getByTestId("esports-page-banner").getAttribute("srcset")
    expect(srcset).toContain("320w")
    expect(srcset).toContain("640w")
    for (const entry of (srcset ?? "").split(",")) {
      expect(entry.trim()).toMatch(/^http.*\/files\/public\/esports-banners\/.* \d+w$/)
    }
  })

  /** The page does not shift under a reader's finger while the picture arrives. */
  test("a banner reserves its space before its bytes arrive", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("esports-banners-open").click()
    await choose(page, "banner-game")
    await expect(page.getByTestId("banner-game-preview")).toBeVisible()
    await page.keyboard.press("Escape")

    const banner = page.getByTestId("esports-page-banner")
    await expect(banner).toHaveAttribute("width", "640")
    await expect(banner).toHaveAttribute("height", "360")
  })

  test("the banner fades out rather than stopping in a line above the strip", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("esports-banners-open").click()
    await expect(page.getByTestId("banner-dialog")).toBeVisible()
    await choose(page, "banner-game")
    await expect(page.getByTestId("banner-game-preview")).toBeVisible()
    await page.keyboard.press("Escape")

    const banner = page.getByTestId("esports-page-banner")
    await expect(banner).toBeVisible()

    // Carried to nothing before the header's own edge, so the header and the strip below it
    // meet without a line between them.
    const mask = await banner.evaluate(el => getComputedStyle(el).maskImage)
    expect(mask).toContain("gradient")
    expect(mask).toContain("rgba(0, 0, 0, 0)")
  })

  test("the banner levels read least specific first, so which one wins is legible", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await page.getByTestId("esports-banners-open").click()
    await expect(page.getByTestId("banner-dialog")).toBeVisible()

    const levels = await page.locator(".banners__level > .picker").evaluateAll(
      nodes => nodes.map(node => node.getAttribute("data-testid")),
    )

    // The game first, then the season, then each team and that team within the season:
    // the order the api resolves them in, so the list explains which one wins.
    expect(levels).toEqual([
      "banner-game",
      "banner-season-20",
      "banner-team-1",
      "banner-team-1-season-20",
      "banner-team-2",
      "banner-team-2-season-20",
    ])
  })

  test("a visitor is offered none of this", async ({page}) => {
    await installApiMocks(page)
    await page.goto(GAME_PAGE)

    await expect(page.getByTestId("esports-banners-open")).toBeHidden()
  })
})
