import {expect, test} from "./test"
import {installApiMocks, loginAsAdmin} from "./mocks"

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

test.describe("esports manager", () => {
  test("lists the seasons and the teams of the chosen game", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto("/management/esports")

    await expect(page.getByTestId("esports-season-row-20")).toContainText("Autumn 2025/26")
    await expect(page.getByTestId("esports-team-row-1")).toContainText("BS Waterboarders")
  })

  test("shows a roster with its real names, which the public page does not", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto("/management/esports")
    await page.getByTestId("esports-team-row-1").click()

    const roster = page.getByTestId("esports-roster-table")
    await expect(roster).toContainText("AriosFury")
    await expect(roster).toContainText("Viktor Petrov")
    // An entry nobody could be attributed to is marked rather than hidden.
    await expect(roster).toContainText("Unlinked")
  })

  test("puts somebody new on a roster", async ({page}) => {
    await installApiMocks(page)
    await loginAsAdmin(page.context())

    await page.goto("/management/esports")
    await page.getByTestId("esports-team-row-1").click()
    await page.getByTestId("esports-add-entry").click()

    await page.getByTestId("esports-entry-handle").locator("input").fill("newcomer")
    const created = page.waitForRequest(
      (request) => request.method() === "POST" && /\/esports\/teams\/\d+\/roster$/.test(new URL(request.url()).pathname),
    )
    await page.getByTestId("esports-entry-save").click()

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
