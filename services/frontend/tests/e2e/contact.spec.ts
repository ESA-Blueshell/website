import {expect, test} from "./test"
import {installApiMocks, preferLightTheme} from "./mocks"

test.describe("the contact page", () => {
  // The map is the reason this page once called Google: now nothing of its own leaves the site.
  // Every page still loads its fonts from Google Fonts, until #2295 takes that away too.
  test("asks nobody but the site for anything", async ({page}) => {
    const elsewhere: string[] = []
    page.on("request", request => {
      const {hostname} = new URL(request.url())
      if (["127.0.0.1", "localhost", "fonts.googleapis.com", "fonts.gstatic.com"].includes(hostname)) return
      if (!request.url().startsWith("data:")) elsewhere.push(request.url())
    })
    await installApiMocks(page)

    await page.goto("/contact")
    const art = page.locator("img.campus-map__art--dark")
    await art.scrollIntoViewIfNeeded()
    await expect(art).toBeVisible()
    await expect.poll(() => art.evaluate(img => (img as HTMLImageElement).naturalWidth)).toBeGreaterThan(0)

    expect(elsewhere).toEqual([])
  })

  test("draws the light campus in the light theme", async ({page}) => {
    await installApiMocks(page)
    await preferLightTheme(page)

    await page.goto("/contact")

    await expect(page.locator("img.campus-map__art--light")).toBeVisible()
    await expect(page.locator("img.campus-map__art--dark")).toBeHidden()
  })

  test("zooms with its buttons and a scroll, and pans by dragging", async ({page}) => {
    await installApiMocks(page)
    await page.goto("/contact")
    const plate = page.getByTestId("campus-map")
    const stage = page.locator(".campus-map__stage")
    await plate.scrollIntoViewIfNeeded()

    await page.getByRole("button", {name: "Zoom in"}).click()
    await expect(stage).toHaveAttribute("style", /scale\(1\.4\)/)

    const box = (await plate.boundingBox())!
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
    await page.mouse.down()
    await page.mouse.move(box.x + box.width / 2 + 60, box.y + box.height / 2 + 30, {steps: 4})
    await page.mouse.up()
    await expect(stage).toHaveAttribute("style", /translate\(60px, 30px\)/)

    await page.getByRole("button", {name: "Back to the Lounge"}).click()
    await expect(stage).toHaveAttribute("style", /translate\(0px, 0px\) scale\(1\)/)

    // A scroll over the map zooms it, not the page.
    const scrolled = await page.evaluate(() => window.scrollY)
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
    await page.mouse.wheel(0, -100)
    await expect(stage).toHaveAttribute("style", /scale\(1\.4\)/)
    expect(await page.evaluate(() => window.scrollY)).toBe(scrolled)
  })
})
