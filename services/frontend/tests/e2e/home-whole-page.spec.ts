import {expect, test} from "./test"
import {installApiMocks} from "./mocks"

/**
 * The home page as a whole (#1358): the poster scroller, the slice bands and the Discord widget
 * are each reached by keyboard and show a ring, and the reel's rail stays legible in light mode,
 * where the image bands keep their dark palette on a light page.
 */
test("the poster scroller, the slice bands and the Discord widget are reached by keyboard, each with a ring", async ({page}) => {
  await installApiMocks(page)
  await page.goto("/")
  await expect(page.getByTestId("home-discord-room-1")).toBeVisible()

  const ringed = new Set<string>()
  for (let press = 0; press < 150 && !ringed.has("home-call-discord"); press++) {
    await page.keyboard.press("Tab")
    const stop = await page.evaluate(() => {
      const el = document.activeElement as HTMLElement | null
      if (!el || el === document.body) return null
      const style = getComputedStyle(el)
      const ring = (style.outlineStyle !== "none" && parseFloat(style.outlineWidth) > 0) || style.boxShadow !== "none"
      return {at: el.closest("[data-testid]")?.getAttribute("data-testid") ?? "", ring}
    })
    if (stop?.ring) ringed.add(stop.at)
  }

  for (const stop of ["home-upcoming-strip-500", "home-casual-rail-MINECRAFT", "home-esports-link-VALORANT", "home-discord-join", "home-discord-room-1"]) {
    expect(ringed, stop).toContain(stop)
  }
})

test("the reel's rail labels meet AA in light mode, lit and unlit", async ({page}) => {
  await page.addInitScript(() => localStorage.setItem("esa-blueshell.nl:darkMode", "false"))
  await installApiMocks(page)
  await page.goto("/")
  await expect(page.getByTestId("home-casual-rail-VALORANT")).toBeVisible()

  // Colours are resolved by painting them, so color-mix and oklab come back as plain sRGB.
  const ratios = await page.evaluate(() => {
    const canvas = document.createElement("canvas")
    canvas.width = canvas.height = 1
    const paint = canvas.getContext("2d", {willReadFrequently: true}) as CanvasRenderingContext2D
    const rgb = (...layers: string[]) => {
      paint.clearRect(0, 0, 1, 1)
      paint.fillStyle = "#fff"
      paint.fillRect(0, 0, 1, 1)
      for (const layer of layers) {
        paint.fillStyle = layer
        paint.fillRect(0, 0, 1, 1)
      }
      return [...paint.getImageData(0, 0, 1, 1).data.slice(0, 3)]
    }
    const luminance = ([r, g, b]: number[]) => {
      const [lr, lg, lb] = [r, g, b].map(v => {
        const c = v / 255
        return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
      })
      return 0.2126 * lr + 0.7152 * lg + 0.0722 * lb
    }
    const contrast = (a: number[], b: number[]) => {
      const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
      return (hi + 0.05) / (lo + 0.05)
    }
    const rail = getComputedStyle(document.querySelector("[data-testid=home-casual-rail]") as Element).backgroundColor
    const cell = (testid: string) => {
      const button = document.querySelector(`[data-testid=${testid}]`) as Element
      const ground = rgb(rail, getComputedStyle(button, "::before").backgroundColor)
      return contrast(rgb(getComputedStyle(button.querySelector("span") as Element).color), ground)
    }
    return {lit: cell("home-casual-rail-VALORANT"), unlit: cell("home-casual-rail-MINECRAFT")}
  })

  expect(ratios.lit).toBeGreaterThanOrEqual(4.5)
  expect(ratios.unlit).toBeGreaterThanOrEqual(4.5)
})
