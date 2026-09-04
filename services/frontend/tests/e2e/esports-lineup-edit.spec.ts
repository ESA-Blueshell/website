import {expect, test} from "./test"
import {installApiMocks, loginAsBoard, loginAsMember} from "./mocks"
import {heightsHeldFrom} from "./sliceBand"

/**
 * Changing who played for a team in one season, from the slice that shows them.
 *
 * Everything is held in the dialog until it is saved, so a line-up is published as one answer.
 * A season is edited on its own: the same team in another season is a different line-up.
 */
const GAME_PAGE = "/esports/valorant"

const SWIPE = "[data-testid=\"season-swipe\"]"

const openLineup = async (page: import("@playwright/test").Page) => {
  const roster = page.getByTestId("team-roster-1")
  const pencil = page.getByTestId("team-roster-edit-1")

  // Scrolled to before it is hovered, not by clicking it: a click scrolls its target into view
  // first, and that scroll takes the slice out from under the pointer the pencil needs.
  await roster.scrollIntoViewIfNeeded()
  await roster.hover()
  await expect(pencil).toBeVisible()
  await pencil.click()
  await expect(page.getByTestId("lineup-editor")).toBeVisible()
  // The editor is on screen before it knows what it is editing: it says it is reading the
  // line-up, and the rows arrive when the roster does. A test that counts rows the moment the
  // editor appears is counting a page that has not finished answering.
  await expect(page.getByTestId("lineup-loading")).toHaveCount(0)
}

test.describe("editing a line-up", () => {
  test("somebody who may not edit is offered nothing", async ({page}) => {
    await installApiMocks(page)
    await loginAsMember(page.context())
    await page.goto(GAME_PAGE)

    await expect(page.getByTestId("team-roster-1")).toBeVisible()
    await expect(page.getByTestId("team-roster-edit-1")).toHaveCount(0)
  })

  test("the line-up opens on who played, in the order they are listed", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await expect(page.getByTestId("lineup-handle-0")).toHaveValue("AriosFury")
    await expect(page.getByTestId("lineup-handle-1")).toHaveValue("Loafine")
    await expect(page.getByTestId("lineup-handle-2")).toHaveValue("Blackout")
    // What was said about somebody comes back to be edited, not just to be read.
    await expect(page.getByTestId("lineup-title-0")).toHaveValue("Captain")
    await expect(page.getByTestId("lineup-description-0")).toHaveValue("Holds the **middle** together.")
  })

  test("a caption is held to its length while it is typed", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    const note = page.getByTestId("lineup-description-1")
    await note.fill("a".repeat(300))

    // Held at the cap by the form rather than reported after it was submitted.
    await expect(note).toHaveValue("a".repeat(280))
    await expect(page.getByTestId("lineup-count-1")).toHaveText("280/280")
  })

  test("somebody added and somebody removed both land, and the slice says so", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await page.getByTestId("lineup-remove-2").click()
    // Somebody already on the roster is asked about before they come off.
    await page.getByTestId("confirm-go").click()
    await page.getByTestId("lineup-add").click()
    await page.getByTestId("lineup-handle-2").fill("Newblood")
    await page.getByTestId("lineup-role-2").selectOption("SUBSTITUTE")
    await page.getByTestId("lineup-save").click()

    await expect(page.getByTestId("lineup-editor")).toBeHidden()
    const slice = page.getByTestId("team-roster-1")
    await expect(slice).toContainText("Newblood")
    await expect(slice).not.toContainText("Blackout")
  })

  test("what somebody did and a word about them reach the page", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await page.getByTestId("lineup-title-1").fill("In-game leader")
    await page.getByTestId("lineup-description-1").fill("Calls the *rounds*.")
    await page.getByTestId("lineup-save").click()

    await expect(page.getByTestId("lineup-editor")).toBeHidden()
    const slice = page.getByTestId("team-roster-1")
    await expect(slice).toContainText("In-game leader")
    await expect(slice.locator(".slice__entry-note em")).toHaveText("rounds")
  })

  test("does not grow the band, the height being held for a travelling stop and nothing else", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await expect(page.getByTestId("team-roster-1")).toBeVisible()

    // Watched from before the editor is opened, since a height is written for the length of a
    // pass and released at the end of one: the claim is that none was ever written.
    const held = await heightsHeldFrom(page, SWIPE)
    await openLineup(page)
    await page.getByTestId("lineup-title-1").fill("In-game leader")
    await page.getByTestId("lineup-save").click()
    await expect(page.getByTestId("lineup-editor")).toBeHidden()
    await expect(page.getByTestId("team-roster-1")).toContainText("In-game leader")

    // The save re-answers the season and the band is redrawn around it, which is a change the
    // visitor made and can already see. Two things keep the height out of it: a stop is an id, so
    // a re-answer of the same season never reads as an arrival, and `carry` holds nothing where
    // nothing travels. See #854, where the band grew to fit an editor that had just opened.
    expect(await held()).toEqual([])
  })

  test("the order players are listed in can be changed, and holds", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    // Second becomes first, which is the whole of setting the order.
    await page.getByTestId("lineup-up-1").click()
    await expect(page.getByTestId("lineup-handle-0")).toHaveValue("Loafine")
    await page.getByTestId("lineup-save").click()
    await expect(page.getByTestId("lineup-editor")).toBeHidden()

    await openLineup(page)
    await expect(page.getByTestId("lineup-handle-0")).toHaveValue("Loafine")
    await expect(page.getByTestId("lineup-handle-1")).toHaveValue("AriosFury")
  })

  test("a player can be attached to a member, and detached again", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    // The first is attached already; detaching leaves the roster spot behind.
    await expect(page.getByTestId("lineup-member-0")).toBeVisible()
    await page.getByTestId("lineup-detach-0").click()
    await expect(page.getByTestId("lineup-search-0")).toBeVisible()

    // The second has no account until one is searched for and picked.
    await page.getByTestId("lineup-search-1").fill("Vik")
    await page.getByTestId("lineup-matches-1").waitFor()
    await page.getByTestId("lineup-matches-1").locator("button").first().click()
    await expect(page.getByTestId("lineup-member-1")).toBeVisible()
  })

  test("a real name is still shown only where the member allowed it", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)

    const slice = page.getByTestId("team-roster-1")
    await expect(slice.locator(".slice__entry-name")).toHaveCount(1)

    await openLineup(page)
    await page.getByTestId("lineup-title-0").fill("Still captain")
    await page.getByTestId("lineup-save").click()
    await expect(page.getByTestId("lineup-editor")).toBeHidden()

    // Editing around somebody does not publish a name that was not published before.
    await expect(slice.locator(".slice__entry-name")).toHaveCount(1)
  })

  test("editing one season leaves the same team's other seasons alone", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto(GAME_PAGE)
    await openLineup(page)

    await page.getByTestId("lineup-handle-0").fill("Renamed")
    await page.getByTestId("lineup-save").click()
    await expect(page.getByTestId("lineup-editor")).toBeHidden()

    // The season before it is a different line-up and is untouched.
    await page.getByTestId("esports-season-node-19").click()
    const earlier = page.getByTestId("team-roster-slices")
    await expect(earlier).toContainText("fetabass")
    await expect(earlier).not.toContainText("Renamed")
  })
})
