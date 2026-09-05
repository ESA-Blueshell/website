import {expect, type Page, test} from "./test"
import {installApiMocks} from "./mocks"

function escapeRegExp(input: string) {
  return input.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
}

async function assertPathRenders(page: Page, path: string, sentinel: RegExp) {
  await page.goto(path)
  await expect(page).toHaveURL(new RegExp(`${escapeRegExp(path)}$`))
  await expect(page.getByText(/Uh oh, we made a fucky wucky!/i)).toHaveCount(0)
  await expect(page.getByText(sentinel).first()).toBeVisible()
}

async function openDrawer(page: Page) {
  const menuButton = page.locator(".v-app-bar .v-btn").filter({
    has: page.locator(".mdi-menu"),
  }).first()

  await expect(menuButton).toBeVisible()
  await menuButton.click()
  await expect(page.locator(".v-navigation-drawer")).toBeVisible()
}

test.describe("navbar route integrity", () => {
  test("association and core navbar destinations render", async ({page}) => {
    await installApiMocks(page)

    await assertPathRenders(page, "/membership", /JOIN BLUESHELL/i)
    await assertPathRenders(page, "/aboutus", /ASSOCIATION/i)
    await assertPathRenders(page, "/board", /BOARD/i)
    await assertPathRenders(page, "/committees", /COMMITTEES/i)
    await assertPathRenders(page, "/documents", /DOCUMENTS/i)
    await assertPathRenders(page, "/blogs", /NEWSLETTERS/i)
    await assertPathRenders(page, "/contact", /CONTACT/i)
  })

  test("esports navbar destinations render, including the game nothing used to link to", async ({page}) => {
    await installApiMocks(page)

    await page.goto("/")
    // The menu lists what the records report as fielded, so a game no longer fielded is not on it.
    await expect(page.locator("a[href='/esports/counter-strike-global-offensive']")).toHaveCount(0)

    await assertPathRenders(page, "/esports/competitive-scene", /ESPORTS/i)
    await assertPathRenders(page, "/esports/trackmania", /TRACKMANIA/i)
    await assertPathRenders(page, "/esports/league-of-legends", /LEAGUE OF LEGENDS/i)
    await assertPathRenders(page, "/esports/counter-strike-2", /COUNTER-STRIKE 2/i)
    await assertPathRenders(page, "/esports/valorant", /VALORANT/i)
    await assertPathRenders(page, "/esports/rocketleague", /ROCKET LEAGUE/i)
    await assertPathRenders(page, "/esports/geoguessr", /GEOGUESSR/i)
  })

  test("mobile navbar drawer exposes partner and newsletter links", async ({page}) => {
    await installApiMocks(page)
    await page.setViewportSize({width: 390, height: 844})
    await page.goto("/")

    await openDrawer(page)

    const expectedDrawerLinks = [
      "/blogs",
      "/partners/become-a-partner",
      "/partners/el-nino",
      "/partners/marketing-maatwerk",
      "/esports/geoguessr",
      "/esports/trackmania",
      "/events/circuitShowdown",
    ]

    for (const path of expectedDrawerLinks) {
      await expect(page.locator(`a[href='${path}']`).first()).toBeAttached()
    }

    await expect(page.locator("a[href='/esports/counter-strike-global-offensive']")).toHaveCount(0)
  })
})
