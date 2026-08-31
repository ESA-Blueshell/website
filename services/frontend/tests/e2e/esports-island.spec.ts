import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin, loginAsBoard, preferLightTheme} from "./mocks"

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

  test("shows the games fielded in the shown season, each linking to its own page", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")

    const slices = page.getByTestId("esports-game-slices")
    await slices.waitFor()
    // Every game the mock reports a team for, and a way into each one's own history.
    await expect(slices.locator('[data-testid^="esports-game-"]')).not.toHaveCount(0)
    // On the season being read here, so following it lands on what was just being looked at.
    await expect(slices.locator('a[href="/esports/valorant?season=20"]')).toHaveCount(1)
  })

  test("a game followed from the index opens on the season that was being read", async ({page}, info) => {
    test.skip(info.project.name === "mobile-chrome", "There is no pointer to open a slice with.")
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-slices").waitFor()

    // An earlier season, chosen here because of what was fielded in it.
    await page.getByTestId("esports-season-node-19").click()
    await expect(page.getByTestId("esports-game-VALORANT")).toContainText("BS Tempra")

    // Opening a slice and going to it are the same gesture, one after the other.
    const valorant = page.getByTestId("esports-game-VALORANT")
    await valorant.hover()
    await valorant.click()

    // The same season, and its roster rather than the newest one's.
    await expect(page).toHaveURL(/\/esports\/valorant\?season=19$/)
    await expect(page.getByTestId("team-roster-3")).toContainText("BS Tempra")
    await expect(page.getByTestId("team-roster-1")).toHaveCount(0)
  })

  test("the link into a game names the season it goes to", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-slices").waitFor()
    await page.getByTestId("esports-season-node-19").click()

    const link = page.locator('a[href="/esports/valorant?season=19"]')
    await expect(link).toHaveCount(1)
    // It goes to one season, so it says which rather than promising all of them.
    await expect(link).toContainText("Valorant in Spring 2024/25")
  })

  test("the way back is the index on the season that was chosen", async ({page}, info) => {
    test.skip(info.project.name === "mobile-chrome", "There is no pointer to open a slice with.")
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-slices").waitFor()
    await page.getByTestId("esports-season-node-19").click()

    const valorant = page.getByTestId("esports-game-VALORANT")
    await valorant.hover()
    await valorant.click()
    await expect(page.getByTestId("team-roster-3")).toBeVisible()

    await page.goBack()

    await expect(page).toHaveURL(/\/esports\/competitive-scene\?season=19$/)
    await expect(page.getByTestId("esports-game-VALORANT")).toContainText("BS Tempra")
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

  test("the island's ground follows the viewer's theme", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")

    const ground = () => page.getByTestId("esports-island")
      .evaluate(el => getComputedStyle(el).backgroundColor)

    // The suite reads as dark.
    expect(await ground()).toBe("rgb(28, 28, 28)")

    await preferLightTheme(page)
    await page.goto("/esports/competitive-scene")
    expect(await ground()).toBe("rgb(220, 229, 238)")
  })

  test("the shell tile is behind the island, and differs by theme", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/competitive-scene")

    const tile = () => page.getByTestId("esports-island")
      .evaluate(el => getComputedStyle(el).backgroundImage)

    const dark = await tile()
    expect(dark).toContain("url(")

    await preferLightTheme(page)
    await page.goto("/esports/competitive-scene")
    const light = await tile()
    expect(light).toContain("url(")
    // A broken path resolves to none, and one tile serving both themes is the other
    // way this silently stops working.
    expect(light).not.toBe(dark)
  })

  test("the band of games reads the same in either theme", async ({page}) => {
    await installApiMocks(page)
    await preferLightTheme(page)
    await page.goto("/esports/competitive-scene")

    const slices = page.getByTestId("esports-game-slices")
    await slices.waitFor()

    // Light ink, under a light theme: the band carries one treatment, not the viewer's.
    const ink = await slices.locator('[data-testid^="esports-game-"]').first()
      .evaluate(el => getComputedStyle(el).color)
    expect(ink).toBe("rgb(242, 244, 246)")
  })

  test("a board dialog follows the theme", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)
    await preferLightTheme(page)
    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-slices").waitFor()

    await page.getByTestId("esports-game-add").click()

    // Guards the token substitutions: each of these was a hardcoded dark hex.
    const panel = await page.getByTestId("game-dialog")
      .evaluate(el => getComputedStyle(el).backgroundColor)
    expect(panel).toBe("rgb(251, 253, 255)")
  })
})
