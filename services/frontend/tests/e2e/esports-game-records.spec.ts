import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

/**
 * What a game is called, the colour it carries, its mark and its banner all come from its
 * record. They used to be written into the frontend, in two files, so the pages and the
 * database could disagree about a game and nothing would say so.
 */
test.describe("a game as its record has it", () => {
  test("names the game on its page as the record names it", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/league-of-legends")

    await expect(page.getByRole("heading", {level: 1})).toHaveText("League of Legends")
  })

  test("says what the record says about the game", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/valorant")

    await expect(page.getByTestId("esports-game-intro")).toContainText("Shooters, and plenty of them.")
  })

  test("carries the record's accent across the page", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/valorant")
    await page.getByTestId("esports-island").waitFor()

    // Valorant's own red, from its record, rather than the association's blue.
    const accents = await page.evaluate(() =>
      Array.from(document.querySelectorAll<HTMLElement>("[style*='rgb(255, 70, 85)']")).length)
    expect(accents).toBeGreaterThan(0)
  })

  test("draws a game nobody has chosen art for on the island's own colour", async ({page}) => {
    await installApiMocks(page, {
      esportsGames: [
        {game: "VALORANT", name: "Valorant", slug: "valorant", accent: null, mark: null,
          banner: null, intro: "Nothing drawn for it.", sortIndex: 1, fielded: true},
      ],
    })

    await page.goto("/esports/valorant")
    await page.getByTestId("esports-island").waitFor()

    // Its name and copy still read; there is simply no mark and no colour of its own.
    await expect(page.getByRole("heading", {level: 1})).toHaveText("Valorant")
    await expect(page.getByTestId("esports-game-intro")).toContainText("Nothing drawn for it.")
    const painted = await page.evaluate(() =>
      Array.from(document.querySelectorAll<HTMLElement>("[style*='rgb(255, 70, 85)']")).length)
    expect(painted).toBe(0)
  })

  test("a game renamed in its record is renamed on its page", async ({page}) => {
    await installApiMocks(page, {
      esportsGames: [
        {game: "VALORANT", name: "Valorant Reborn", slug: "valorant", accent: "#ff4655",
          mark: "valorant.png", banner: null, intro: "Renamed.", sortIndex: 1, fielded: true},
      ],
    })

    await page.goto("/esports/valorant")

    // Nothing in the frontend holds the name any more, so the record is the whole of it.
    await expect(page.getByRole("heading", {level: 1})).toHaveText("Valorant Reborn")
  })

  test("names a game on the index as its record names it, and links to its address", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/competitive-scene")

    const slice = page.getByTestId("esports-game-VALORANT")
    await expect(slice).toBeVisible()
    await expect(slice).toContainText("Valorant")
  })
})
