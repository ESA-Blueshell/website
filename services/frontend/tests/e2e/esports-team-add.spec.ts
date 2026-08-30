import {expect, test} from "./test"
import {installApiMocks, loginAsBoard, loginAsMember} from "./mocks"

/**
 * Putting a team into the season on show, from the band that shows the teams.
 *
 * A team that played before usually brings the same people, so its last line-up is offered —
 * shown in full and droppable one name at a time, because a roster is published under the
 * names of real people and last season's departure should not quietly reappear.
 */
const GAME_PAGE = "/esports/valorant"
const INDEX = "/esports/competitive-scene"

test.describe("adding a team to the season on show", () => {
  test("the band ends in a plus for somebody who may edit", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await expect(page.getByTestId("team-roster-add-team")).toBeVisible()
  })

  test("somebody who may not edit is offered no plus", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())
    await page.goto(GAME_PAGE)

    await expect(page.getByTestId("team-roster-slices")).toBeVisible()
    await expect(page.getByTestId("team-roster-add-team")).toHaveCount(0)
  })

  test("a new team is created and fielded in the season on show", async ({page}, testInfo) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add-team").click()
    await expect(page.getByTestId("add-team-dialog")).toContainText("Autumn 2025/26")
    await page.getByTestId("add-team-name").fill("BS Newcomers")
    await page.getByTestId("add-team-save").click()

    await expect(page.getByTestId("add-team-dialog")).toBeHidden()
    // In the band without the page being fetched again by hand.
    const added = page.getByTestId("team-roster-71")
    await expect(added).toBeVisible()
    await expect(added).toContainText("BS Newcomers")

    // Side by side, the team just added is the one showing its roster. Stacked, which slice
    // is open is decided by what the reader has scrolled to — that is the whole of how the
    // band works on a phone, and a slice held open against the scroll would be undone by
    // the next one anyway.
    if (testInfo.project.name !== "mobile-chrome") {
      await expect(added.locator("[aria-expanded]")).toHaveAttribute("aria-expanded", "true")
    }
  })

  test("a team already playing this season is not offered again", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add-team").click()
    await page.getByTestId("add-team-source-existing").click()

    const matches = page.getByTestId("add-team-matches").locator("button")
    // BS Waterboarders and BS SpicyWater already play this season; only the third is left.
    await expect(matches.filter({hasText: "BS Old Guard"})).toHaveCount(1)
    await expect(matches.filter({hasText: "BS Waterboarders"})).toHaveCount(0)
    await expect(matches.filter({hasText: "BS SpicyWater"})).toHaveCount(0)
  })

  test("the team picker is typed at rather than scrolled through", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add-team").click()
    await page.getByTestId("add-team-source-existing").click()

    // Typing narrows to what was asked for.
    await page.getByTestId("add-team-existing").fill("old")
    await expect(page.getByTestId("add-team-match-3")).toBeVisible()

    await page.getByTestId("add-team-existing").fill("nothing answers to this")
    await expect(page.getByTestId("add-team-matches")).toHaveCount(0)
    await expect(page.getByTestId("add-team-no-matches")).toBeVisible()

    // Picking one puts the search away and says what was chosen.
    await page.getByTestId("add-team-existing").fill("guard")
    await page.getByTestId("add-team-match-3").click()
    await expect(page.getByTestId("add-team-chosen")).toContainText("BS Old Guard")
    await expect(page.getByTestId("add-team-existing")).toHaveCount(0)

    // And it can be handed back.
    await page.getByTestId("add-team-unpick").click()
    await expect(page.getByTestId("add-team-existing")).toBeVisible()
  })

  test("picking a team that played before shows exactly who would come with it", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add-team").click()
    await page.getByTestId("add-team-source-existing").click()
    await page.getByTestId("add-team-match-3").click()

    const lineup = page.getByTestId("add-team-lineup")
    await expect(lineup).toBeVisible()
    // Named, so "its last line-up" can be told apart from any other season's.
    await expect(lineup).toContainText("Spring 2024/25")
    await expect(lineup).toContainText("AriosFury")
    await expect(lineup).toContainText("Blackout")
  })

  test("anybody offered can be dropped before it is saved", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    const written: string[] = []
    page.on("request", (request) => {
      if (request.method() === "POST" && /\/esports\/teams\/\d+\/roster$/.test(new URL(request.url()).pathname)) {
        written.push(String(JSON.parse(request.postData() ?? "{}").handle))
      }
    })

    await page.getByTestId("team-roster-add-team").click()
    await page.getByTestId("add-team-source-existing").click()
    await page.getByTestId("add-team-match-3").click()
    await page.getByTestId("add-team-player-12").locator("input").uncheck()
    await page.getByTestId("add-team-save").click()

    await expect(page.getByTestId("add-team-dialog")).toBeHidden()
    // The one kept is written down; the one dropped is never written and then removed.
    expect(written).toEqual(["AriosFury"])
  })

  test("a team can be fielded with nobody on it", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    const written: string[] = []
    page.on("request", (request) => {
      if (request.method() === "POST" && /\/esports\/teams\/\d+\/roster$/.test(new URL(request.url()).pathname)) {
        written.push(String(JSON.parse(request.postData() ?? "{}").handle))
      }
    })

    await page.getByTestId("team-roster-add-team").click()
    await page.getByTestId("add-team-source-existing").click()
    await page.getByTestId("add-team-match-3").click()
    await page.getByTestId("add-team-player-11").locator("input").uncheck()
    await page.getByTestId("add-team-player-12").locator("input").uncheck()
    await page.getByTestId("add-team-save").click()

    await expect(page.getByTestId("add-team-dialog")).toBeHidden()
    expect(written).toEqual([])
    await expect(page.getByTestId("team-roster-3")).toBeVisible()
  })

  test("a game's own page still offers a team", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await expect(page.getByTestId("team-roster-add-team")).toContainText("Add a team")
    await page.getByTestId("team-roster-add-team").click()

    // The game is settled by the page, so it is not asked for.
    await expect(page.getByTestId("add-team-dialog")).toContainText("Add a team")
    await expect(page.getByTestId("add-team-game")).toHaveCount(0)
  })

})
