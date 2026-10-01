import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

/**
 * The home reel's slow pass is the one thing on the home page that moves on its own, once the
 * reel's five-second rest is over. It runs where motion is welcome and holds still for a
 * visitor who asked for reduced motion.
 */
const openings = (page: import("@playwright/test").Page) => page.evaluate(() =>
  [...document.querySelectorAll<HTMLElement>(".flick-reel__slice")]
    .map(slice => slice.style.getPropertyValue("--open")).join(","))

const movesWithin = async (page: import("@playwright/test").Page, ms: number) => {
  const before = await openings(page)
  await page.waitForTimeout(ms)
  return (await openings(page)) !== before
}

test("the home reel passes on its own, and holds still with reduced motion", async ({page}) => {
  await installApiMocks(page)
  await page.goto("/")
  await expect(page.getByTestId("home-casual-rail-VALORANT")).toBeVisible()
  expect(await movesWithin(page, 7000)).toBe(true)

  await page.emulateMedia({reducedMotion: "reduce"})
  await page.reload()
  await expect(page.getByTestId("home-casual-rail-VALORANT")).toBeVisible()
  expect(await movesWithin(page, 7000)).toBe(false)
})
