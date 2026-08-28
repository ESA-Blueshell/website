import {expect, test} from "./test"
import {installApiMocks, loginAsBoard, loginAsMember} from "./mocks"

/**
 * Changing who played for a team in one season, from the slice that shows them.
 *
 * Everything is held in the dialog until it is saved, so a line-up is published as one answer.
 * A season is edited on its own: the same team in another season is a different line-up.
 */
const GAME_PAGE = "/esports/valorant"

const openLineup = async (page: import("@playwright/test").Page) => {
  await page.getByTestId("team-roster-1").hover()
  await page.getByTestId("team-roster-edit-1").click()
  await expect(page.getByTestId("lineup-editor")).toBeVisible()
}

test.describe("editing a line-up in place", () => {
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
    await expect(slice.locator(".team-slice__member-note em")).toHaveText("rounds")
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
    await expect(slice.locator(".team-slice__member-name")).toHaveCount(1)

    await openLineup(page)
    await page.getByTestId("lineup-title-0").fill("Still captain")
    await page.getByTestId("lineup-save").click()
    await expect(page.getByTestId("lineup-editor")).toBeHidden()

    // Editing around somebody does not publish a name that was not published before.
    await expect(slice.locator(".team-slice__member-name")).toHaveCount(1)
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
