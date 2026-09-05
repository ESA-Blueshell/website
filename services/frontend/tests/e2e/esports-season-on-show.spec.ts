import {expect, test} from "./test"
import {installApiMocks} from "./mocks"
import {landing} from "./sliceBand"

/**
 * The season a page opens on, and that it is the same season everything on it describes.
 *
 * Runs in the deterministic projects — none of this is about the movement between seasons, only
 * about which season is arrived at. The movement itself is in
 * `esports-season-swipe.motion.spec.ts`.
 *
 * Those projects are meant to emulate reduced motion and do not: see #852. So nothing here may
 * assume the choreography is switched off, and the one test below that is about the preference
 * sets it for itself.
 *
 * CS:GO is the game that makes these assertions mean something: it played the older of the two
 * seasons and nothing since, so a page that lets the api pick a season per game shows it, and
 * a page that names the season it means does not.
 */
const SWIPE = "[data-testid=\"season-swipe\"]"

test.describe("the season a page opens on", () => {
  test("shows only the games fielded in the association's newest season", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-slices").waitFor()

    await expect(page.getByTestId("esports-game-VALORANT")).toBeAttached()
    await expect(page.getByTestId("esports-game-CS2")).toBeAttached()
    // Fielded in the older season only. Left to answer for itself it would be here, dressed
    // as something the association plays now.
    await expect(page.getByTestId("esports-game-CSGO")).toHaveCount(0)
  })

  test("says the season the band belongs to, on the band", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-slices").waitFor()

    // Every slice is described by the one season, because every slice is that season's.
    const labels = page.getByTestId("esports-game-slices").locator(".slice__group-label")
    const named = await labels.allTextContents()
    expect(named.length).toBeGreaterThan(0)
    named.forEach(name => expect(name.trim()).toBe("Autumn 2025"))
  })

  test("opens a game's own page on the newest season, not the game's own newest", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/counter-strike-global-offensive")
    const empty = page.getByTestId("esports-empty")
    await empty.waitFor()

    // The association's newest season, which this game sat out — said by name, so the page is
    // an answer about a season rather than a blank.
    await expect(empty).toContainText("Autumn 2025")
  })

  test("offers the season a retired game last played, from the empty page", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/counter-strike-global-offensive")
    const back = page.getByTestId("esports-empty-last-played")
    await back.waitFor()

    await expect(back).toContainText("Spring 2025")
    await back.click()

    // And it leads somewhere: the season it names, with what it played in it.
    await expect(page.getByTestId("team-roster-slices")).toBeAttached()
    expect(new URL(page.url()).searchParams.get("season")).toBe("19")
  })

  test("puts the season being read on the strip, even where the game never played it", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/counter-strike-global-offensive")
    await page.getByTestId("esports-empty").waitFor()

    // Season 20 is not one this game played; standing on it, it still has a node to stand on.
    await expect(page.getByTestId("esports-season-node-20")).toBeAttached()
    await expect(page.getByTestId("esports-season-node-19")).toBeAttached()
  })

  test("keeps the game that was being read open across a season change", async ({page}, info) => {
    test.skip(info.project.name === "mobile-chrome", "There is no pointer to open a slice with.")
    await installApiMocks(page)

    await page.goto("/esports/competitive-scene")
    await page.getByTestId("esports-game-slices").waitFor()

    // Not the first slice — the one this visitor chose to read.
    await page.getByTestId("esports-game-CS2").hover()
    await expect(page.getByTestId("esports-game-CS2")).toHaveClass(/slice--open/)

    await page.getByTestId("esports-season-node-19").click()
    await expect(page.getByTestId("season-swipe")).toHaveAttribute("data-swipe", "past")

    // Open in the frame the arriving season is first drawn in, while the pass is still on,
    // rather than growing again once it is over. CS:GO is what says the band is the older
    // season's, the slice being read having stood on the season before it as well.
    const drawn = await landing(page, SWIPE, "esports-game-CS2", "esports-game-CSGO")
    expect(drawn).toEqual({open: true, panels: 2})

    // The season travelled. The subject did not change under them on the way.
    await expect(page.getByTestId("esports-game-CS2")).toHaveClass(/slice--open/)
  })

  test("keeps a season named in the url, whatever the newest one is", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/valorant?season=19")
    await page.getByTestId("team-roster-slices").waitFor()

    await expect(page.getByTestId("team-roster-3")).toBeAttached()
  })

  test("crosses the seasons over rather than travelling, for a visitor who asked for less motion", async ({page}) => {
    // The preference comes from the project, which every project but the motion one sets.
    await installApiMocks(page)

    await page.goto("/esports/competitive-scene")
    const swipe = page.getByTestId("season-swipe")
    await swipe.waitFor()
    await expect(swipe).toHaveAttribute("data-swipe-mode", "fade")

    await page.getByTestId("esports-season-node-19").click()

    // The change is still explained — the seasons cross over — but nothing travels the width
    // of the window, which is the whole of what the preference asks for.
    await expect(swipe).toHaveAttribute("data-swipe", "past")
    const travelled = await swipe.evaluate(el => [...el.children].map(
      child => new DOMMatrix(getComputedStyle(child).transform).m41,
    ))
    travelled.forEach(x => expect(x).toBe(0))
  })
})
