import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

/**
 * The home reel's slow pass is the one thing on the home page that moves on its own, once the
 * reel's five-second rest is over. It runs where motion is welcome and holds still for a
 * visitor who asked for reduced motion.
 *
 * The belt stands still while it is off screen, and the casual band starts below the fold on a
 * desktop, so each half brings the reel into view first, with the pointer off it.
 */
type Page = import("@playwright/test").Page

const openings = (page: Page) => page.evaluate(() =>
  [...document.querySelectorAll<HTMLElement>(".flick-reel__slice")]
    .map(slice => slice.style.getPropertyValue("--open")).join(","))

const showReel = async (page: Page) => {
  await page.locator(".flick-reel__band").first().scrollIntoViewIfNeeded()
  await page.mouse.move(1, 1)
}

test("the home reel passes on its own, and holds still with reduced motion", async ({page}) => {
  await installApiMocks(page)
  await page.goto("/")
  await expect(page.getByTestId("home-casual-rail-VALORANT")).toBeVisible()
  await showReel(page)
  const resting = await openings(page)
  await expect.poll(() => openings(page), {timeout: 12_000}).not.toBe(resting)

  await page.emulateMedia({reducedMotion: "reduce"})
  await page.reload()
  await expect(page.getByTestId("home-casual-rail-VALORANT")).toBeVisible()
  await showReel(page)
  const held = await openings(page)
  await page.waitForTimeout(7_000)
  expect(await openings(page)).toBe(held)
})
