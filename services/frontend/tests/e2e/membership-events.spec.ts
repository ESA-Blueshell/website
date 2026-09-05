import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

/**
 * The band of recent events on the membership page, read as a logged-out visitor reads it.
 *
 * Everything is found by the test id it is drawn with rather than by its position in the row,
 * so a band moving on the page, or an event changing places in it, is not a failing spec.
 */
test.describe("membership events band", () => {
  test("shows six recent events with their real titles", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/membership")

    await expect(page.getByTestId("membership-events")).toBeVisible()
    await expect(page.getByTestId("membership-events-slices").locator("> section")).toHaveCount(6)
    await expect(page.getByTestId("membership-events-551")).toContainText("Sampled Event 1")
    await expect(page.getByTestId("membership-events-556")).toContainText("Sampled Event 6")
  })

  test("draws the art from the embedded url and its rendition ladder", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/membership")

    const art = page.getByTestId("membership-events-551").locator("img")
    await expect(art).toHaveAttribute("src", "/files/public/event-banners/sampled-1-1280.webp")
    // The ladder goes to the browser, and the band, which owns the layout, states the widths.
    await expect(art).toHaveAttribute("srcset", /sampled-1-320\.webp 320w/)
    await expect(art).toHaveAttribute("sizes", /px|vw/)
    await expect(art).toHaveAttribute("width", "1280")
    await expect(art).toHaveAttribute("height", "720")
  })

  // That an event was members-only is the argument the page is making with it.
  test("says on the slice that an event was members-only", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/membership")

    await expect(page.getByTestId("membership-events-551")).toContainText("Members only")
    await expect(page.getByTestId("membership-events-552")).not.toContainText("Members only")
  })

  test("asks the api only for recent events that have art", async ({page}) => {
    await installApiMocks(page)
    const asked = page.waitForRequest(request => request.url().includes("hasBanner=true"))
    await page.goto("/membership")

    const request = await asked
    const query = new URL(request.url()).searchParams
    expect(query.get("sort")).toBe("startTime,desc")
    expect(query.get("size")).toBe("6")
    expect(query.get("to")).toBeTruthy()
  })

  // The whole point of the public banner and the public events read: a visitor who has not
  // logged in gets the same page.
  test("makes no authenticated request for any of it", async ({page}) => {
    await installApiMocks(page)
    const authorized: string[] = []
    page.on("request", (request) => {
      if (request.headers()["authorization"]) authorized.push(request.url())
    })

    await page.goto("/membership")
    await expect(page.getByTestId("membership-events")).toBeVisible()

    expect(authorized).toEqual([])
  })

  /**
   * The band is absent rather than short.
   *
   * A row of three is two wide slabs beside an open one, where the band's fixed diagonal reads
   * as a rendering fault, and three events is not a sample of a busy calendar but the whole of
   * one. The rest of the page still makes its case.
   */
  test("takes the whole band away when too few events qualify", async ({page}) => {
    await installApiMocks(page, {bannerEvents: threeWithArt()})
    await page.goto("/membership")

    await expect(page.getByTestId("membership-perks")).toBeVisible()
    await expect(page.getByTestId("membership-events")).toHaveCount(0)
    await expect(page.getByTestId("membership-events-slices")).toHaveCount(0)
  })

  test("takes the band away when the api will not answer at all", async ({page}) => {
    await installApiMocks(page, {bannerEvents: []})
    await page.goto("/membership")

    await expect(page.getByTestId("membership-fees-year")).toBeVisible()
    await expect(page.getByTestId("membership-events")).toHaveCount(0)
  })

  // An event the api counts as having a banner whose file will not serve is a slice with a
  // hole in it, so it is not one of the events the band draws.
  test("leaves out an event whose art the api cannot serve", async ({page}) => {
    await installApiMocks(page, {bannerEvents: fiveWithArtAndOneWithout()})
    await page.goto("/membership")

    await expect(page.getByTestId("membership-events-slices").locator("> section")).toHaveCount(5)
    await expect(page.getByTestId("membership-events-556")).toHaveCount(0)
  })

  // A file the browser refuses is not a broken row: the names and the dates are the band.
  test("still reads as a band when the art itself fails to load", async ({page}) => {
    await page.route("**/files/public/**", route => route.abort())
    await installApiMocks(page)
    await page.goto("/membership")

    await expect(page.getByTestId("membership-events-slices").locator("> section")).toHaveCount(6)
    await expect(page.getByTestId("membership-events-551")).toContainText("Sampled Event 1")
    const heights = await page.getByTestId("membership-events-slices").locator("> section")
      .evaluateAll(nodes => nodes.map(node => Math.round(node.getBoundingClientRect().height)))
    expect(heights.every(height => height > 0)).toBe(true)
  })

  /**
   * A visitor who asked for less movement gets the band at rest, opened already.
   *
   * The preference is emulated here rather than left to the project: `use.reducedMotion` does
   * not reach the page on Playwright 1.60 (#852), so a spec that assumes it is asserting the
   * unreduced path under a reduced name.
   */
  test("stands still for a visitor who asked for less motion", async ({page}) => {
    await page.emulateMedia({reducedMotion: "reduce"})
    await installApiMocks(page)
    await page.goto("/membership")

    const band = page.getByTestId("membership-events-slices")
    await expect(band).toHaveAttribute("style", /--slice-ease: 0\.12s/)
    // Opened as it is drawn rather than animated open, which is what the ceiling is for.
    await expect(page.getByTestId("membership-events-551").getByRole("button").first())
      .toHaveAttribute("aria-expanded", "true")
  })
})

/** Enough art to be tempting and not enough to be a band. */
function threeWithArt() {
  return [1, 2, 3].map(nth => withArt(nth))
}

/** Six the api counts, one of which has no servable file behind its banner. */
function fiveWithArtAndOneWithout() {
  return [...[1, 2, 3, 4, 5].map(nth => withArt(nth)), {...withArt(6), banner: {
    eventId: 556, fileId: 556, version: 0,
    createdAt: "2026-01-01T00:00:00.000Z", updatedAt: "2026-01-01T00:00:00.000Z",
    image: null,
  }}]
}

function withArt(nth: number) {
  return {
    id: 550 + nth,
    title: `Sampled Event ${nth}`,
    location: "Predator Esports Lounge",
    startTime: `2026-0${nth}-0${nth}T19:00:00.000Z`,
    endTime: `2026-0${nth}-0${nth}T23:00:00.000Z`,
    approved: true,
    signUp: false,
    signUpCount: 0,
    membersOnly: false,
    committeeId: 900,
    banner: {
      eventId: 550 + nth,
      fileId: 550 + nth,
      version: 0,
      createdAt: "2026-01-01T00:00:00.000Z",
      updatedAt: "2026-01-01T00:00:00.000Z",
      image: {
        path: `event-banners/sampled-${nth}.webp`,
        url: `/files/public/event-banners/sampled-${nth}.webp`,
        width: 1280,
        height: 720,
        renditions: [320, 640, 960, 1280].map(width => ({
          url: `/files/public/event-banners/sampled-${nth}-${width}.webp`,
          width,
        })),
      },
    },
  }
}
