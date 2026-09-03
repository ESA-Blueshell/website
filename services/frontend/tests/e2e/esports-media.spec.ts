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
  // The editor is on screen before it knows what it is editing: it says it is reading the
  // line-up, and the rows arrive when the roster does. A test that counts rows the moment the
  // editor appears is counting a page that has not finished answering.
  await expect(page.getByTestId("lineup-loading")).toHaveCount(0)
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
    await expect(behind).toHaveAttribute("width", "1280")

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

  /**
   * A team carries an icon only once somebody uploads one, and none ships, so the game's own
   * logo is the only one this page has. Without it the page a slice leads to shows nothing of
   * the game the slice named — which is what removing it from the header did.
   */
  test("the game's own page is identified by the game's logo", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/esports")

    await page.getByTestId("esports-game-VALORANT").hover()
    await page.getByTestId("esports-game-edit-VALORANT").click()
    await choose(page, "game-dialog-icon")
    await page.getByTestId("game-dialog-save").click()
    await expect(page.getByTestId("game-dialog")).toHaveCount(0)

    await page.goto(GAME_PAGE)

    const logo = page.getByTestId("esports-game-icon")
    await expect.poll(() => decoded(logo)).toBe(true)
    await expect(logo).toHaveAttribute("src", /\/files\/public\/game-icons\/[^/]+\.webp/)
    await expect(logo).toHaveAttribute("srcset", /128w/)
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

/**
 * How large a copy of a banner is fetched, and when.
 *
 * A slice is a tall narrow strip and its banner covers it, so the picture is drawn far wider
 * than the strip is: promising the browser the strip's width fetches something blurred to two
 * and a half times its size. Promising it the honest figure up front fetches the top of the
 * ladder before anything is on the screen.
 *
 * So it is asked twice. The first promise understates, deliberately, and the picture that
 * lands is dimmed almost to a silhouette anyway; the second is measured off the band once it
 * has been laid out, and the browser fetches a larger copy over the one already showing.
 */
test.describe("how large a banner is fetched", () => {


  /**
   * Nothing the mocks seed carries a banner, so they are put on the way a board would put
   * them and the page is then read as a visitor reads it.
   */
  const giveABanner = async (page: import("@playwright/test").Page, team: number) => {
    await page.getByTestId(`team-roster-${team}`).hover()
    await page.getByTestId(`team-roster-edit-${team}`).click()
    await expect(page.getByTestId("lineup-editor")).toBeVisible()
    await expect(page.getByTestId("lineup-loading")).toHaveCount(0)
    await choose(page, "lineup-team-banner")
    await expect(page.getByTestId("lineup-team-banner-preview")).toBeVisible()
    await page.getByTestId("lineup-save").click()
    await expect(page.getByTestId("lineup-editor")).toBeHidden()
  }

  /**
   * What each banner in the band is promised, and which slice is being read.
   *
   * Read together in one pass, because the comparison is between them: the figure is worked
   * out from the share of the row a slice has and how tall the row is, so the only honest check
   * is the open one against the shut ones at the same moment. Slices with no picture are not in
   * the list.
   */
  const band = (page: import("@playwright/test").Page) => page.evaluate(() =>
    [...document.querySelectorAll(".slice")]
      .map(slice => ({
        open: slice.classList.contains("slice--open"),
        sizes: slice.querySelector(".slice__banner")?.getAttribute("sizes") ?? "",
      }))
      .filter(one => one.sizes !== ""))

  test("a banner is asked for enough to cover its slice, and the slice being read for no less", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await giveABanner(page, 1)
    await giveABanner(page, 2)

    // Reloaded, and then left alone. Reaching a slice's edit button means hovering it, which
    // opens it, and a slice that has been open keeps the wider figure it had — so after the
    // uploads there is no slice left in this band that has only ever been a strip. A fresh
    // load is a band where exactly one slice has been open: the one it opens itself.
    //
    // Which one that is, is not this test's business. It is found rather than named, because
    // naming it made the test read whichever slice happened to lead as the open one and the
    // two figures came back the other way round.
    await page.reload()

    await expect.poll(
      async () => {
        const drawn = await band(page)
        // Both conditions together: nothing is open for the first frame, by design, so a band
        // whose figures have all settled may still have no slice open yet.
        return drawn.length > 1 &&
          drawn.every(one => /^\d+px$/.test(one.sizes)) &&
          drawn.filter(one => one.open).length === 1
      },
      {timeout: 15_000},
    ).toBe(true)

    const slices = await band(page)
    expect(slices.filter(one => one.open)).toHaveLength(1)
    const asked = (one: {sizes: string}) => Number(one.sizes.replace("px", ""))
    const openly = asked(slices.find(one => one.open)!)
    const others = slices.filter(one => !one.open).map(asked)
    expect(others.length).toBeGreaterThan(0)
    const window = page.viewportSize()!.width

    // No slice is promised less than the window, or less than the one being read: a banner
    // covers its slice, so a slice narrower than it is tall is asked for the width its height
    // demands rather than for its share of the row. Where that height decides it, the slice
    // being read and the strips beside it land on the same figure — which is why this is a
    // floor rather than a difference, and why no ceiling is asserted at all.
    if (await page.evaluate(() => matchMedia("(max-width: 767px)").matches)) {
      // Stacked, a slice is at least the width of the window.
      expect(openly).toBeGreaterThanOrEqual(window)
      others.forEach(one => expect(one).toBeGreaterThanOrEqual(window))
    } else {
      expect(openly).toBeGreaterThanOrEqual(Math.max(...others))
      others.forEach(one => expect(one).toBeGreaterThan(0))
    }
  })

  /**
   * The whole point: something on the screen first, the right thing shortly after.
   *
   * Watched as requests rather than as an attribute, because what matters is which file the
   * browser actually went and got.
   *
   * Which widths those are depends on the screen's density — the promise is in css pixels and
   * the browser multiplies — so this asserts that the first copy is narrower than one that
   * follows it, rather than naming the two files.
   */
  test("a small copy is fetched first and a larger one over it", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await giveABanner(page, 1)

    // Left for a blank page before anything is counted, rather than reloaded over the top.
    // Saving the banner re-renders the band, which asks for a wider copy of it, and that
    // request is still in flight while this test sets up — recorded, it reads as the first
    // thing the new page asked for. Leaving the page cancels it instead. Emptying the list on
    // the new document's `domcontentloaded` does not work: the event is delivered
    // asynchronously and the clear can land after the requests it was meant to precede.
    await page.goto("about:blank")

    const fetched: string[] = []
    // Only the copies, not the master: a master is `mock-<id>.webp`, which a looser pattern
    // reads as a copy `<id>` pixels wide.
    page.on("request", request => {
      const match = /team-banners\/mock-\d+-(\d+)\.webp/.exec(request.url())
      if (match) fetched.push(match[1])
    })

    // A fresh load, so both passes happen from nothing.
    await page.goto(GAME_PAGE)

    // The first copy arrives on its own, off the understated promise.
    await expect.poll(() => fetched.length, {timeout: 15_000}).toBeGreaterThan(0)
    const first = Number(fetched[0])

    // Then the slice is read, which is when a wider copy is worth having: a shut slice's share
    // of the row is narrow enough that the worked-out figure lands on the same rung the guess
    // did, so nothing is fetched twice and nothing should be — that is the saving, not a
    // failure. It is the slice being looked at whose picture is fetched again.
    await page.getByTestId("team-roster-1").hover()

    // Waited for rather than slept through: a fixed wait for this is too short on a loaded
    // machine, which is what three workers made of it.
    await expect.poll(() => Math.max(...fetched.map(Number)), {timeout: 15_000})
      .toBeGreaterThan(first)

    // And the narrow one really did go first.
    expect(first).toBe(Math.min(...fetched.map(Number)))
  })
})
