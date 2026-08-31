import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

/**
 * One page serves every game, found by the address its record names. A game used to gain a page
 * by somebody writing a component and a route for it.
 */
test.describe("a game's page, by its address", () => {
  test("every address that worked before lands on the same game", async ({page}) => {
    await installApiMocks(page)

    for (const [slug, name] of [
      ["league-of-legends", "League of Legends"],
      ["counter-strike-2", "Counter-Strike 2"],
      ["valorant", "Valorant"],
      ["rocketleague", "Rocket League"],
      ["geoguessr", "GeoGuessr"],
    ]) {
      await page.goto(`/esports/${slug}`)
      await expect(page.getByRole("heading", {level: 1})).toHaveText(name!)
    }
  })

  test("trackmania is reachable", async ({page}) => {
    await installApiMocks(page)

    // It has had a page written for it and no way to get to it.
    await page.goto("/esports/trackmania")

    await expect(page.getByRole("heading", {level: 1})).toHaveText("Trackmania")
    await expect(page.getByTestId("esports-game-intro")).toContainText("Driving, fast.")
  })

  test("an address no game claims is a not-found, not an empty page", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/tiddlywinks")

    await expect(page.getByTestId("not-found")).toBeVisible()
    await expect(page.getByTestId("esports-island")).toHaveCount(0)
  })

  test("a game gains a page by having a record, with no route written for it", async ({page}) => {
    await installApiMocks(page, {
      esportsGames: [
        {code: "PONG", name: "Pong", slug: "pong", accent: null, banner: null, icon: null,
          intro: "Two paddles and a ball.", sortIndex: 1, current: true},
      ],
    })

    await page.goto("/esports/pong")

    await expect(page.getByRole("heading", {level: 1})).toHaveText("Pong")
    await expect(page.getByTestId("esports-game-intro")).toContainText("Two paddles and a ball.")
  })

  test("the navigation lists the games the records report as fielded", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/")

    // CS:GO is history and is not offered; Trackmania is fielded and now is.
    await expect(page.locator("a[href='/esports/trackmania']").first()).toBeAttached()
    await expect(page.locator("a[href='/esports/counter-strike-global-offensive']")).toHaveCount(0)
  })
})
