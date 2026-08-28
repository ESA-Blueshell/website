import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

/**
 * What a roster entry says beyond the part somebody played.
 *
 * The enum is the shape of the squad; these are the shape of the person in it. Both are read
 * by anybody, so what is asserted is that they reach the public page and that the rules about
 * a real name are untouched by their arrival.
 */
test.describe("what a roster says about a player", () => {
  test("the words a team used for somebody sit beside the part they played", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/valorant")

    const roster = page.getByTestId("team-roster-1")
    await expect(roster).toContainText("Captain")
    // The fixed part is still what the roster is grouped by.
    await expect(roster).toContainText("Players")
  })

  test("a caption is rendered as markdown rather than shown as its source", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/valorant")

    const note = page.getByTestId("team-roster-1").locator(".team-slice__member-note")
    await expect(note).toContainText("Holds the middle together.")
    // Emphasis is rendered, so the asterisks are not on the page.
    await expect(note.locator("strong")).toHaveText("middle")
    await expect(note).not.toContainText("**")
  })

  test("saying nothing about somebody adds nothing to the page", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/valorant")

    // Loafine has no words and no caption, and gains no empty lines because of it.
    const roster = page.getByTestId("team-roster-1")
    await expect(roster).toContainText("Loafine")
    await expect(roster.locator(".team-slice__member-note")).toHaveCount(1)
    await expect(roster.locator(".team-slice__member-role")).toHaveCount(1)
  })

  test("a real name still appears only for somebody who allowed it", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/esports/valorant")

    const roster = page.getByTestId("team-roster-1")
    await expect(roster).toContainText("Viktor Petrov")
    // Nobody else on the roster gained a name from the new fields arriving.
    await expect(roster.locator(".team-slice__member-name")).toHaveCount(1)
  })
})
