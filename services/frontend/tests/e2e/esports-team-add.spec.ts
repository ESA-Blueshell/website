import {expect, test} from "./test"
import {installApiMocks, loginAsBoard, loginAsMember} from "./mocks"

const GAME_PAGE = "/competition/valorant"
const BACK_ON_GAME_PAGE = /\/competition\/valorant(\?season=\d+)?$/

/**
 * Putting a team into the shown season, from the band that shows the teams, on the team's own
 * page with the band drawn beside the form.
 *
 * Two ways in rather than one, matching the index. A team that played before is picked out of
 * the association's whole pool and brings the line-up it last had in this game; a team that does
 * not exist yet is described in full — name, logo, this season's art, the squad — and nothing is
 * written until Create.
 */
test.describe("adding a team to the shown season", () => {
  test("the band ends in one way in, and it asks which kind", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await expect(page.getByTestId("team-roster-add")).toContainText("Add a team")
    await page.getByTestId("team-roster-add").click()

    await expect(page).toHaveURL(/\/competition\/valorant\/teams\/new(\?season=\d+)?$/)
    await expect(page.getByRole("heading", {level: 1})).toHaveText("Add a team")
    // One page, and the choice made on it.
    await expect(page.getByTestId("lineup-kind-played-before")).toContainText("An existing team")
    await expect(page.getByTestId("lineup-kind-new-team")).toContainText("A new team")
  })

  test("somebody who may not edit is offered none of it", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())
    await page.goto(GAME_PAGE)

    await expect(page.getByTestId("team-roster-slices")).toBeVisible()
    await expect(page.getByTestId("team-roster-add")).toHaveCount(0)
  })

  test("the pool is the association's, not this game's", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add").click()
    await page.getByTestId("field-team-search").click()

    // Every team the association has, including ones that have only played something else.
    // That is what makes fielding a team in a game it has never played reachable at all.
    await expect(page.getByTestId("field-team-3")).toBeVisible()
  })

  test("a team already playing this season is not offered again", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add").click()
    await page.getByTestId("field-team-search").click()

    // Team 1 is already fielded in Valorant this season, so there is nothing to add of it.
    await expect(page.getByTestId("field-team-1")).toHaveCount(0)
  })

  test("the picker is typed at rather than scrolled through", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add").click()
    await page.getByTestId("field-team-search").fill("Old Guard")

    await expect(page.getByTestId("field-team-3")).toBeVisible()
    await expect(page.getByTestId("field-team-2")).toHaveCount(0)
  })

  test("a team that played before is fielded, and brings its line-up", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add").click()
    await page.getByTestId("field-team-search").click()
    await page.getByTestId("field-team-3").click()

    // Picked, then who comes with them: the line-up it last had is offered, and everybody on
    // it is ticked until somebody is not. The list closes, and the field says who is chosen.
    // The chosen row is shown in the box rather than behind it, so it reads as the answer.
    await expect(page.getByTestId("field-team-search")).toHaveValue("BS Old Guard")
    await expect(page.getByTestId("lineup-source-people")).toContainText("AriosFury")

    await page.getByTestId("field-team-confirm").click()

    await expect(page).toHaveURL(BACK_ON_GAME_PAGE)
    const added = page.getByTestId("team-roster-3")
    await expect(added).toBeVisible()
    await expect(added).toContainText("AriosFury")
  })

  test("the line-up offered is named by its game and its season", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add").click()
    await page.getByTestId("field-team-search").click()
    await page.getByTestId("field-team-3").click()

    // "Its last line-up" is only useful if the reader can tell which squad that was, and a
    // team that spans games has more than one answer.
    await expect(page.getByTestId("lineup-source-fielding")).toContainText("VALORANT")
    await expect(page.getByTestId("lineup-source-fielding")).toContainText("Spring 2025")
  })

  test("anybody offered can be dropped before it is fielded", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add").click()
    await page.getByTestId("field-team-search").click()
    await page.getByTestId("field-team-3").click()
    await page.getByTestId("lineup-source-person-11").locator("input").uncheck()

    await page.getByTestId("field-team-confirm").click()

    // A roster is published under the names of real people, so last season's departure does
    // not quietly reappear.
    await expect(page).toHaveURL(BACK_ON_GAME_PAGE)
    await expect(page.getByTestId("team-roster-3")).not.toContainText("AriosFury")
  })

  test("a new team starts from nobody until a line-up is chosen", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add").click()
    await page.getByTestId("lineup-kind-new-team").click()

    // A team being made has no history of its own; the point is to start from people who have
    // played together somewhere else, so nothing is chosen for you.
    await expect(page.getByTestId("lineup-source")).toBeVisible()
    await expect(page.getByTestId("lineup-source-team-search")).toBeVisible()
  })

  test("a new team can be built from another team's line-up", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add").click()
    await page.getByTestId("lineup-kind-new-team").click()
    await page.getByTestId("lineup-source-team-search").click()
    await page.getByTestId("lineup-source-team-search").fill("Old Guard")
    await page.getByTestId("lineup-source-team-3").click()

    // The people arrive as rows of the form, still held until Create.
    await expect(page.getByTestId("lineup-editor")).toContainText("AriosFury")
  })

  test("a new team is described in full and created", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add").click()
    await page.getByTestId("lineup-kind-new-team").click()

    await page.getByTestId("lineup-team-name").fill("BS Newcomers")
    // The band the team joins, drawn as it is typed.
    await expect(page.getByTestId("team-edit-preview")).toContainText("BS Newcomers")
    await page.getByTestId("lineup-save").click()

    await expect(page).toHaveURL(BACK_ON_GAME_PAGE)
    await expect(page.getByTestId("team-roster-slices")).toContainText("BS Newcomers")
  })

  test("a team can be created with nobody on it", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add").click()
    await page.getByTestId("lineup-kind-new-team").click()
    await page.getByTestId("lineup-team-name").fill("BS Announced")

    // Fielding a team and settling its squad are the two decisions this whole thing separates,
    // so an empty row is not an unfinished one.
    await expect(page.getByTestId("lineup-save")).toBeEnabled()
  })

  test("nothing is written when a new team is abandoned", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add").click()
    await page.getByTestId("lineup-kind-new-team").click()
    await page.getByTestId("lineup-team-name").fill("BS Abandoned")
    await page.getByTestId("lineup-cancel").click()

    await expect(page).toHaveURL(BACK_ON_GAME_PAGE)
    // Held until Create, so leaving does not leave an empty team behind.
    await expect(page.getByTestId("team-roster-slices")).not.toContainText("BS Abandoned")
  })

  test("a team being made is offered no way to remove itself", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    await page.getByTestId("team-roster-add").click()
    await page.getByTestId("lineup-kind-new-team").click()

    // There is nothing yet to drop from a season or to remove; Cancel is what leaves.
    await expect(page.getByTestId("lineup-remove-team")).toHaveCount(0)
    await expect(page.getByTestId("lineup-drop-from-season")).toHaveCount(0)
  })

  test("a season this game sat out still offers the way to put a team into it", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    // CS:GO played the older season and nothing since, so the newer one is empty for it.
    await page.goto("/competition/counter-strike-global-offensive?season=20")

    // Said in the band, in a slice, with the way in beside it rather than under it — and the
    // way on kept, because a season this game sat out is not a dead end for a board either.
    await expect(page.getByTestId("team-roster-empty-slice")).toContainText("No teams played")
    await expect(page.getByTestId("esports-empty-last-played")).toBeVisible()
    await expect(page.getByTestId("team-roster-add")).toBeVisible()
  })

  test("a visitor reading a season it sat out is offered no way in", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/competition/counter-strike-global-offensive?season=20")
    await expect(page.getByTestId("esports-empty")).toBeVisible()

    await expect(page.getByTestId("team-roster-add")).toHaveCount(0)
  })
})
