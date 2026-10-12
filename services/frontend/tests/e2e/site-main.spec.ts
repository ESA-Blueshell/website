import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

const PAGES = ["/", "/documents", "/membership", "/membership/signup", "/events", "/blogs", "/contact", "/login", "/not-a-page"]

test.describe("a page's main area", () => {
  for (const path of PAGES) {
    test(`${path} has exactly one main landmark`, async ({page}) => {
      await installApiMocks(page)
      await page.goto(path)

      await expect(page.locator("main")).toHaveCount(1)
      await expect(page.locator("main")).toBeVisible()
    })
  }

  test("fills the window on a page without the bar and the footer", async ({page}) => {
    await page.setViewportSize({width: 1280, height: 800})
    await installApiMocks(page)
    await page.goto("/unauthorized")

    await expect(page.locator("header.site-bar")).toHaveCount(0)
    const main = (await page.locator("main").boundingBox())!
    expect(main.y).toBe(0)
    expect(main.height).toBeGreaterThanOrEqual(800)
  })

  test("reaches from under the bar down to the footer on a short page", async ({page}) => {
    await page.setViewportSize({width: 1280, height: 1600})
    await installApiMocks(page)
    await page.goto("/documents")

    const main = (await page.locator("main").boundingBox())!
    const footer = (await page.locator("footer").first().boundingBox())!
    expect(Math.abs(main.y + main.height - footer.y)).toBeLessThanOrEqual(1)
    expect(footer.y + footer.height).toBeGreaterThanOrEqual(1599)
  })
})
