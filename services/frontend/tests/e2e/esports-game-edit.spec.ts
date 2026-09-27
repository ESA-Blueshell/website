import {expect, test} from "./test"
import {installApiMocks, loginAsBoard, writeMarkdown} from "./mocks"
import type {Page} from "@playwright/test"

/**
 * The header's pencil waits for a pointer, the way the slices' do.
 *
 * Scrolled to before it is hovered, not by clicking it. A click scrolls its target into view
 * first, that scroll takes the header out from under the pointer, and a pencil is only visible
 * while what it belongs to is hovered: the click loses the hover it needs. Whether the scroll
 * is needed at all depends on where the page happens to be sitting.
 */
const openGameEditor = async (page: Page) => {
  // The island's header, not the app bar's, which is also a <header>.
  const header = page.getByTestId("esports-island").locator("header").first()
  const pencil = page.getByTestId("esports-game-edit")

  await header.scrollIntoViewIfNeeded()
  await header.hover()
  await expect(pencil).toBeVisible()
  await pencil.click()
}

/**
 * Correcting a game on its own edit page, reached by the same affordance the seasons and the
 * teams already carry, with the competition head it will have drawn beside the form.
 */
test.describe("changing a game", () => {
  test("a visitor is offered none of it", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/competition/valorant")
    await page.getByTestId("esports-island").waitFor()

    await expect(page.getByTestId("esports-game-edit")).toHaveCount(0)

    await page.goto("/competition")
    await page.getByTestId("esports-game-slices").waitFor()
    await expect(page.getByTestId("esports-game-edit-VALORANT")).toHaveCount(0)
  })

  test("a game's code is shown but is not editable", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/competition/valorant")
    await openGameEditor(page)

    // It is what a team, a roster and a member's handle already point at.
    await expect(page).toHaveURL(/\/competition\/valorant\/edit$/)
    await expect(page.getByTestId("game-edit")).toContainText("VALORANT")
    await expect(page.getByTestId("game-edit").locator("input[value='VALORANT']")).toHaveCount(0)
  })

  test("renames a game on its own page, without a reload", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/competition/valorant")
    await openGameEditor(page)
    await page.getByTestId("game-edit-name").locator("input").fill("Valorant Reborn")
    // The head the page will have, drawn as it is typed.
    await expect(page.getByTestId("game-edit-preview")).toContainText("Valorant Reborn")
    await page.getByTestId("game-edit-save").click()

    await expect(page).toHaveURL(/\/competition\/valorant$/)
    await expect(page.getByRole("heading", {level: 1})).toHaveText("Valorant Reborn")
  })

  test("changes what the page says about the game", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/competition/valorant")
    await openGameEditor(page)
    await writeMarkdown(page, page.getByTestId("game-edit-intro").getByRole("textbox"), "Aim, plus everything else.")
    await page.getByTestId("game-edit-save").click()

    await expect(page.getByTestId("esports-game-intro")).toContainText("Aim, plus everything else.")
  })

  test("takes a game's art away, leaving the island's own colour", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/competition/valorant")
    await openGameEditor(page)
    await page.locator("[data-testid='game-edit-accent'] input:not([type=color])").fill("")
    await page.getByTestId("game-edit-save").click()

    await expect(page.getByTestId("game-edit")).toHaveCount(0)
    const painted = await page.evaluate(() =>
      Array.from(document.querySelectorAll<HTMLElement>("[style*='rgb(255, 70, 85)']")).length)
    expect(painted).toBe(0)
  })

  test("a highlight colour is picked or written, tints both previews, and a non-colour is not saved", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)
    await page.goto("/competition/valorant")
    await openGameEditor(page)

    const hex = page.locator("[data-testid='game-edit-accent'] input:not([type=color])")
    await expect(hex).toHaveValue("#ff4655")
    await page.getByTestId("game-edit-accent-field-swatch").fill("#1183d6")
    await expect(hex).toHaveValue("#1183d6")
    const tinted = () => page.evaluate(() =>
      Array.from(document.querySelectorAll<HTMLElement>("[data-testid^='game-edit-preview-'] [style*='rgb(17, 131, 214)'], [data-testid^='game-edit-preview-'] [style*='#1183d6']")).length)
    await expect.poll(tinted).toBeGreaterThan(1)

    await hex.fill("blue")
    await expect(page.getByTestId("game-edit-accent")).toContainText("Write a colour as # and six hex digits.")
    await expect(page.getByTestId("game-edit-save")).toBeDisabled()
  })

  test("the competition pages say their own intro and name the esports channels; the casual pages keep theirs", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)
    await page.goto("/competition/valorant")
    await openGameEditor(page)

    const competition = page.getByTestId("game-edit-competition")
    await writeMarkdown(page, page.getByTestId("game-edit-competition-intro").getByRole("textbox"), "Two teams in the national league.")
    await competition.getByTestId("game-edit-esports-channels-picker-search").click()
    await page.getByTestId("game-edit-esports-channels-picker-7322").click()
    await expect(page.getByTestId("game-edit-esports-channels-7322")).toContainText("#valorant-esports")
    await expect(page.getByTestId("game-edit-preview-competition").getByTestId("esports-game-intro")).toContainText("Two teams")
    await page.getByTestId("game-edit-save").click()

    await expect(page).toHaveURL(/\/competition\/valorant$/)
    await expect(page.getByTestId("esports-game-intro")).toContainText("Two teams in the national league.")
    await expect(page.getByTestId("esports-game-channel-7322")).toHaveAttribute("href", "https://discord.com/channels/324/7322")

    await page.goto("/casual/valorant")
    await expect(page.getByTestId("casual-game-head")).toContainText("Five-stacks, customs and clips.")
    await expect(page.getByTestId("casual-game-head")).not.toContainText("Two teams")
    await expect(page.getByTestId("casual-game-channel-6322")).toBeVisible()
    await expect(page.getByTestId("casual-game-channel-7322")).toHaveCount(0)
  })

  test("an address another game claims is refused, and what was typed stays", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/competition/valorant")
    await openGameEditor(page)
    await page.getByTestId("game-edit-slug").locator("input").fill("geoguessr")
    await page.getByTestId("game-edit-save").click()

    await expect(page.getByTestId("game-edit-failure"))
      .toContainText("The address 'geoguessr' is already used by GeoGuessr.")
    await expect(page.getByTestId("game-edit-slug").locator("input")).toHaveValue("geoguessr")
  })

  test("Cancel goes back to the page without writing", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/competition/valorant")
    await openGameEditor(page)
    await page.getByTestId("game-edit-name").locator("input").fill("Not saved")
    await page.getByTestId("game-edit-cancel").click()

    await expect(page).toHaveURL(/\/competition\/valorant$/)
    await expect(page.getByRole("heading", {level: 1})).toHaveText("Valorant")
  })

  test("a game is corrected from the band as well as from its own page", async ({page, context}) => {
    await installApiMocks(page)
    await loginAsBoard(context)

    await page.goto("/competition")
    const slice = page.getByTestId("esports-game-VALORANT")
    const pencil = page.getByTestId("esports-game-edit-VALORANT")

    // Scrolled to, hovered, and only then pressed: see `openGameEditor` above.
    await slice.scrollIntoViewIfNeeded()
    await slice.hover()
    await expect(pencil).toBeVisible()
    await pencil.click()

    await expect(page.getByTestId("game-edit-name").locator("input")).toHaveValue("Valorant")
  })
})
