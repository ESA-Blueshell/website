import {expect, test} from "./test"
import {installApiMocks, preferLightTheme} from "./mocks"

const PHONE = 390

// One width inside each of the four ranges the banner sizes itself for, 840px being the
// first of its range.
const SIZES = [
  {width: PHONE, band: "200px", title: "40px"},
  {width: 700, band: "200px", title: "50px"},
  {width: 840, band: "200px", title: "70px"},
  {width: 1440, band: "300px", title: "90px"},
]

test.describe("the page banner", () => {
  for (const {width, band, title} of SIZES) {
    for (const theme of ["dark", "light"]) {
      test(`is ${band} tall with a ${title} title at ${width}px, ${theme}`, async ({page}) => {
        await page.setViewportSize({width, height: 900})
        await installApiMocks(page)
        if (theme === "light") await preferLightTheme(page)
        await page.goto("/documents")

        const banner = page.getByTestId("page-banner")
        await expect(banner).toHaveCSS("height", band)
        const heading = banner.getByTestId("page-banner-title")
        await expect(heading).toHaveText("DOCUMENTS")
        await expect(heading).toHaveCSS("font-size", title)
        await expect(heading).toHaveCSS("font-family", /^"Shellhouse One"/)
        await expect(heading).toHaveCSS("font-weight", "700")
        await expect(heading).toHaveCSS("letter-spacing", "1.5px")
        await expect(heading).toHaveCSS("line-height", "normal")
        await expect(heading).toHaveCSS("text-align", "center")
        await expect(heading).toHaveCSS("color", "rgb(255, 255, 255)")
      })
    }
  }

  test("wraps a long title on a phone and never runs past the screen", async ({page}) => {
    await page.setViewportSize({width: PHONE, height: 900})
    await installApiMocks(page)
    await page.goto("/events/circuitShowdown")

    const banner = page.getByTestId("page-banner")
    const heading = banner.getByTestId("page-banner-title")
    await expect(heading).toHaveText("CIRCUIT SHOWDOWN")
    const box = (await heading.boundingBox())!
    expect(box.x).toBeGreaterThanOrEqual(0)
    expect(box.x + box.width).toBeLessThanOrEqual(PHONE)
    // One line of this face at 40px is about 59px tall, two about 117px.
    expect(box.height).toBeGreaterThan(100)
    // The banner's own width: this page's standings table is wider than a phone.
    expect(await banner.evaluate(element => element.scrollWidth)).toBeLessThanOrEqual(PHONE)
  })
})
