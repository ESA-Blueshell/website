import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

test.describe("esports pages", () => {
  test("shows the teams of the season on offer, with their handles", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/valorant")

    await expect(page.getByTestId("team-roster-1")).toBeVisible()
    await expect(page.getByTestId("team-roster-1")).toContainText("BS Waterboarders")
    await expect(page.getByTestId("team-roster-1")).toContainText("AriosFury")
    await expect(page.getByTestId("team-roster-2")).toContainText("BS SpicyWater")
  })

  test("names each group of a roster for what it holds", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/valorant")

    const team = page.getByTestId("team-roster-1")
    // Two players and one substitute: one label plural, the other singular.
    await expect(team).toContainText("Players")
    await expect(team).toContainText("Substitute")
    await expect(team).not.toContainText("Coach")
  })

  test("switches to an earlier season, and says so in the url", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/valorant")
    await expect(page.getByTestId("team-roster-1")).toBeVisible()

    await page.getByTestId("esports-season-node-19").click()

    await expect(page.getByTestId("team-roster-3")).toContainText("BS Tempra")
    await expect(page.getByTestId("team-roster-1")).toHaveCount(0)
    await expect(page).toHaveURL(/season=19/)
  })

  test("opens straight into the season the url names", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/valorant?season=19")

    await expect(page.getByTestId("team-roster-3")).toContainText("fetabass")
  })

  test("shows no name for a member who has not allowed one", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/valorant")

    // Loafine and Blackout are linked members whose names the admin surface shows; neither
    // has said their name may be published, so the page knows them by their handle alone.
    const team = page.getByTestId("team-roster-1")
    await expect(team).toContainText("Loafine")
    await expect(team.locator(".team-slice__member-name")).toHaveCount(1)
  })
})

/**
 * What the esports manager used to guarantee, asserted where each of those things now
 * happens. The manager is gone; none of what it covered went with it.
 */
test.describe("what the manager used to do, where it happens now", () => {
  test("its address lands on the pages that replaced it", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/management/esports")

    await expect(page).toHaveURL(/\/esports\/competitive-scene$/)
    await expect(page.getByTestId("esports-island")).toBeVisible()
  })

  test("the seasons and the teams of a game are both on the game's own page", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())

    await page.goto("/esports/valorant")

    await expect(page.getByTestId("esports-season-node-20")).toContainText("Autumn 2025/26")
    await expect(page.getByTestId("team-roster-1")).toContainText("BS Waterboarders")
  })

  test("a roster is read with its real names where it is edited, which the public page does not do", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/esports/valorant")

    await page.getByTestId("team-roster-1").hover()
    await page.getByTestId("team-roster-edit-1").click()

    const lineup = page.getByTestId("lineup-editor")
    await expect(lineup).toBeVisible()
    await expect(page.getByTestId("lineup-handle-0")).toHaveValue("AriosFury")
    await expect(page.getByTestId("lineup-name-0")).toHaveValue("Viktor Petrov")
    // Somebody nobody could be attributed to is offered a member to attach rather than hidden.
    await expect(page.getByTestId("lineup-search-1")).toBeVisible()
  })

  test("somebody new goes onto a roster from the slice that shows it", async ({page}) => {
    await installApiMocks(page)
    await loginAsBoard(page.context())
    await page.goto("/esports/valorant")

    await page.getByTestId("team-roster-1").hover()
    await page.getByTestId("team-roster-edit-1").click()
    await page.getByTestId("lineup-add").click()
    await page.getByTestId("lineup-handle-3").fill("newcomer")

    const created = page.waitForRequest(
      (request) => request.method() === "POST" && /\/esports\/teams\/\d+\/roster$/.test(new URL(request.url()).pathname),
    )
    await page.getByTestId("lineup-save").click()

    const request = await created
    expect(JSON.parse(request.postData() ?? "{}")).toMatchObject({handle: "newcomer", role: "PLAYER"})
  })
})

test.describe("names on the team pages", () => {
  test("names a member who allows it, and nobody else", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/esports/valorant")

    const team = page.getByTestId("team-roster-1")
    // One member has said their name may be shown; the other two have not.
    await expect(team).toContainText("AriosFury")
    await expect(team).toContainText("Viktor Petrov")
    await expect(team).toContainText("Loafine")
    await expect(team).not.toContainText("Blackout Petrov")

    const named = team.locator(".team-slice__member-name")
    await expect(named).toHaveCount(1)
    await expect(named).toHaveText("Viktor Petrov")
  })
})
