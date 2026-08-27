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

  test("shows the games fielded in the season on show, each linking to its own page", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")

    const slices = page.getByTestId("esports-game-slices")
    await slices.waitFor()
    // Every game the mock reports a team for, and a way into each one's own history.
    await expect(slices.locator('[data-testid^="esports-game-"]')).not.toHaveCount(0)
    await expect(slices.locator('a[href="/esports/valorant"]')).toHaveCount(1)
  })

  test("offers the three ways in, and points each of them somewhere real", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")

    const band = page.getByTestId("esports-join")
    await expect(band).toBeVisible()

    // Joining is the one that puts somebody on a roster, so it leads.
    await expect(page.getByTestId("esports-join-member")).toHaveAttribute("href", "/membership")
    // Asking first is the alternative, in both the places the association answers.
    await expect(page.getByTestId("esports-join-discord"))
      .toHaveAttribute("href", /^https:\/\/discord\.gg\//)
    await expect(page.getByTestId("esports-join-mail"))
      .toHaveAttribute("href", "mailto:esports-affairs@blueshell.utwente.nl")
  })

  test("opens the Discord in its own tab, and safely", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")

    const discord = page.getByTestId("esports-join-discord")
    await expect(discord).toHaveAttribute("target", "_blank")
    // A tab opened from here must not be handed a reference back to this one.
    await expect(discord).toHaveAttribute("rel", /noopener/)
  })

  test("the island's reset stops at its own root", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")

    // Inside, the island's reset applies: a heading carries none of the margin the rest of
    // the site gives it.
    const insideHeading = await page.locator('[data-testid="esports-island"] h1').first()
      .evaluate(el => getComputedStyle(el).marginBlockEnd)
    expect(insideHeading).toBe("0px")

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
