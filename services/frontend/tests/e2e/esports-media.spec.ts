import {Buffer} from "node:buffer"
import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

/**
 * Putting pictures on the pages without a deploy.
 *
 * The point of the upload work is that a poster, an icon and a banner reach the public page
 * from a file chooser rather than from the frontend's assets directory, so each test here
 * ends by reading the page a visitor would see rather than the form that changed it.
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

/** An image that decoded, which a url pointing at the wrong origin never does. */
const loaded = async (page: import("@playwright/test").Page, testid: string) => {
  await expect(page.getByTestId(testid)).toBeVisible()
  return page.getByTestId(testid).evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0)
}

const openLineup = async (page: import("@playwright/test").Page) => {
  await page.getByTestId("team-roster-1").hover()
  await page.getByTestId("team-roster-edit-1").click()
  await expect(page.getByTestId("lineup-editor")).toBeVisible()
}

test.describe("posters, icons and banners", () => {
  test("a team's poster is uploaded and shows without a deploy", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await expect(page.getByTestId("lineup-team-poster-empty")).toBeVisible()
    await choose(page, "lineup-team-poster")

    const preview = page.getByTestId("lineup-team-poster-preview")
    await expect(preview).toHaveAttribute("src", /\/files\/public\/team-posters\/[^/]+\.webp/)
    await expect.poll(() => loaded(page, "lineup-team-poster-preview")).toBe(true)
  })

  test("an uploaded poster can be taken away again", async ({page}) => {
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
  })

  test("a row nobody has saved yet has nowhere to hang a picture", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    const before = await page.getByTestId(/^lineup-icon-\d+$/).count()
    await page.getByTestId("lineup-add").click()

    await expect(page.getByTestId(/^lineup-icon-\d+$/)).toHaveCount(before)
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
