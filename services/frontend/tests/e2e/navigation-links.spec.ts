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
  const menuButton = page.getByTestId("nav-menu-toggle")

  await expect(menuButton).toBeVisible()
  await menuButton.click()
  await expect(page.getByTestId("nav-drawer")).toBeVisible()
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
    await expect(page.locator("a[href='/competition/counter-strike-global-offensive']")).toHaveCount(0)

    await assertPathRenders(page, "/competition", /ESPORTS/i)
    await assertPathRenders(page, "/competition/trackmania", /TRACKMANIA/i)
    await assertPathRenders(page, "/competition/league-of-legends", /LEAGUE OF LEGENDS/i)
    await assertPathRenders(page, "/competition/counter-strike-2", /COUNTER-STRIKE 2/i)
    await assertPathRenders(page, "/competition/valorant", /VALORANT/i)
    await assertPathRenders(page, "/competition/rocketleague", /ROCKET LEAGUE/i)
    await assertPathRenders(page, "/competition/geoguessr", /GEOGUESSR/i)
  })

  test("committees have a tab of their own, listing the ones running now", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/committees/lancie")

    const drawerToggle = page.getByTestId("nav-menu-toggle")
    if (await drawerToggle.isVisible()) {
      await drawerToggle.click()
      await page.getByTestId("nav-drawer-committees-more").click()
    } else {
      await expect(page.getByTestId("nav-committees")).toHaveClass(/bar-button--here/)
      await expect(page.getByTestId("nav-association")).not.toHaveClass(/bar-button--here/)
      await page.getByTestId("nav-committees-more").hover()
    }

    await expect(page.locator("a[href='/committees/events-committee']").first()).toBeAttached()
    await expect(page.locator("a[href='/committees/lancie']").first()).toBeAttached()
    // Unlisted and archived committees keep their pages but are not offered here.
    await expect(page.locator("a[href='/committees/board']")).toHaveCount(0)
    await expect(page.locator("a[href='/committees/oldcie']")).toHaveCount(0)
  })

  test("mobile navbar drawer exposes partner and newsletter links", async ({page}) => {
    await installApiMocks(page)
    await page.setViewportSize({width: 390, height: 844})
    await page.goto("/")

    await openDrawer(page)
    // A section's pages are drawn once it is unfolded, which is what a reader does to reach them.
    for (const section of ["association", "events", "competition", "partners"]) {
      await page.getByTestId(`nav-drawer-${section}-more`).click()
    }

    const expectedDrawerLinks = [
      "/blogs",
      "/partners/become-a-partner",
      "/partners/el-nino",
      "/partners/marketing-maatwerk",
      "/competition/geoguessr",
      "/competition/trackmania",
      "/events/circuitShowdown",
    ]

    for (const path of expectedDrawerLinks) {
      await expect(page.locator(`a[href='${path}']`).first()).toBeAttached()
    }

    await expect(page.locator("a[href='/competition/counter-strike-global-offensive']")).toHaveCount(0)
  })
})
