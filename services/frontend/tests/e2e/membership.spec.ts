import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

/**
 * The page that sells membership, read as a visitor reads it.
 *
 * Everything is found by the test id it is drawn with rather than by its position on the page,
 * so a band moving, or another one arriving between two of them, is not a failing spec.
 */
test.describe("membership page", () => {
  test("stands on the island with its bands meeting", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/membership")

    await expect(page.getByTestId("membership-island")).toBeVisible()
    await expect(page.getByTestId("membership-hero-photo")).toBeVisible()

    // Full-bleed, and the hero hands straight over to the numbers: no gap between the two.
    const gap = await page.evaluate(() => {
      const hero = document.querySelector('[data-testid="membership-hero"]')!.getBoundingClientRect()
      const numbers = document.querySelector('[data-testid="membership-numbers"]')!.getBoundingClientRect()
      return {between: Math.round(numbers.top - hero.bottom), left: Math.round(hero.left), width: Math.round(hero.width)}
    })
    expect(gap.between).toBe(0)
    expect(gap.left).toBe(0)
    expect(gap.width).toBe(await page.evaluate(() => document.documentElement.clientWidth))
  })

  // The pitch is what a visitor is here for, so it may not be below the fold on a phone.
  test("shows the pitch and the way in without scrolling", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/membership")

    await expect(page.getByTestId("membership-hero-pitch")).toBeInViewport()
    await expect(page.getByTestId("membership-hero-join")).toBeInViewport()
  })

  test("upgrades the floors to the association's counted numbers", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/membership")

    await expect(page.getByTestId("membership-numbers-teams-value")).toHaveText("13")
    await expect(page.getByTestId("membership-numbers-committees-value")).toHaveText("15")
    await expect(page.getByTestId("membership-numbers-events-value")).toHaveText("63")
    // Permission-gated and unreadable while logged out, so it stays the association's claim.
    await expect(page.getByTestId("membership-numbers-members-value")).toHaveText("200+")
  })

  test("still states something true when the numbers are refused", async ({page}) => {
    await installApiMocks(page, {associationStatistics: null})
    await page.goto("/membership")

    await expect(page.getByTestId("membership-numbers-members-value")).toHaveText("200+")
    await expect(page.getByTestId("membership-numbers-teams-value")).toHaveText("10+")
    await expect(page.getByTestId("membership-numbers-committees-value")).toHaveText("10+")
    await expect(page.getByTestId("membership-numbers-events-value")).toHaveText("20+")
  })

  test("quotes this year's fees and says they are subject to change", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/membership")

    await expect(page.getByTestId("membership-fees-full-year-amount")).toContainText("25,00")
    await expect(page.getByTestId("membership-fees-half-year-amount")).toContainText("15,00")
    await expect(page.getByTestId("membership-fees-alumni-amount")).toContainText("12,50")
    await expect(page.getByTestId("membership-fees-year")).toHaveText(/\d{4}\/\d{4}/)
    await expect(page.getByTestId("membership-fees-note")).toContainText("subject to change")
    await expect(page.getByTestId("membership-fees-note")).toContainText("General Members Meeting")
  })

  test("says the fees are not listed rather than showing a price it does not have", async ({page}) => {
    await installApiMocks(page, {currentContributionPeriod: null})
    await page.goto("/membership")

    await expect(page.getByTestId("membership-fees-unlisted")).toBeVisible()
    await expect(page.getByTestId("membership-fees-full-year-amount")).toHaveCount(0)
  })

  test("reaches the signup step from the hero", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/membership")

    await page.getByTestId("membership-hero-join").click()

    await expect(page).toHaveURL(/\/membership\/signup$/)
    await expect(page.getByTestId("membership-signup-stepper")).toBeVisible()
  })

  test("reaches the signup step from the band at the foot", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/membership")

    await page.getByTestId("membership-join-signup").click()

    await expect(page).toHaveURL(/\/membership\/signup$/)
  })

  test("opens the Discord in its own tab, and safely", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/membership")

    const discord = page.getByTestId("membership-join-discord")
    await expect(discord).toHaveAttribute("target", "_blank")
    await expect(discord).toHaveAttribute("rel", /noopener/)
  })

  /**
   * The one moving thing on the page, for a visitor who asked for less of it: the words are
   * simply there, at rest, rather than arriving.
   *
   * The preference comes from the project, which every project but the motion one sets.
   */
  test("does not animate the pitch in for a visitor who asked for less motion", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/membership")

    const words = page.getByTestId("membership-hero-pitch")
    await expect(words).toBeVisible()
    const style = await words.evaluate(el => {
      const box = el.parentElement!
      return {opacity: getComputedStyle(box).opacity, transform: getComputedStyle(box).transform}
    })
    expect(style.opacity).toBe("1")
    expect(["none", "matrix(1, 0, 0, 1, 0, 0)"]).toContain(style.transform)
  })
})
