import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin} from "./mocks"

/**
 * The island is a second styling system inside a Vuetify app. What these assert
 * is mostly containment: that it exists where it should and reaches nothing
 * else. Every project but the motion one runs with reduced motion emulated, so
 * nothing here waits on an animation.
 */
test.describe("the esports island", () => {
  test("the esports index is inside the island", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")

    await expect(page.getByTestId("esports-island")).toBeVisible()
    // Nothing Vuetify is left in the page's own markup.
    await expect(page.locator('[data-testid="esports-island"] .v-btn, [data-testid="esports-island"] .v-card'))
      .toHaveCount(0)
  })

  test("every game is offered and each one links to its page", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")

    const grid = page.getByTestId("esports-game-grid")
    await expect(grid.locator("a")).toHaveCount(5)
    await expect(page.getByTestId("esports-game-league-of-legends"))
      .toHaveAttribute("href", "/esports/league-of-legends")
  })

  test("the island's reset stops at its own root", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")

    // Inside, the island's reset applies: its lists carry no markers.
    const insideList = await page.getByTestId("esports-game-grid")
      .evaluate(el => getComputedStyle(el).listStyleType)
    expect(insideList).toBe("none")

    // Outside, a bare paragraph keeps the margin the browser gives it. This is
    // the assertion that Preflight is not imported: Tailwind's reset would have
    // zeroed this on every page in the app at once.
    await page.goto("/membership")
    const outsideParagraph = await page.locator("p").first()
      .evaluate(el => getComputedStyle(el).marginBlockStart)
    expect(outsideParagraph).not.toBe("0px")
  })

  test("a management page still renders its Vuetify furniture", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto("/management/cohorts")

    // The island must not have disturbed the rest of the app: no Tailwind
    // Preflight is imported, so Vuetify's own components look as they did.
    await expect(page.locator(".v-application").first()).toBeVisible()
    await expect(page.getByTestId("esports-island")).toHaveCount(0)
  })

  test("the island looks the same to a visitor who prefers light", async ({page}) => {
    await installApiMocks(page)
    await page.emulateMedia({colorScheme: "light"})
    await page.goto("/esports/competitive-scene")

    // These pages commit to one treatment; there is no light variant to keep.
    // The value is the island's ground token, which sits a step below the
    // navigation's own grey rather than being a near-black of its own.
    const background = await page.getByTestId("esports-island")
      .evaluate(el => getComputedStyle(el).backgroundColor)
    expect(background).toBe("rgb(28, 28, 28)")
  })
})
