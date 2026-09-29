import type {Locator} from "@playwright/test"
import type {Page} from "./test"

export type Rgb = [number, number, number]

/** What a screenshot of an element shows, one css pixel per point. */
export interface Pixels {
  width: number
  height: number
  at(x: number, y: number): Rgb
}

/**
 * Screenshots [target] and reads its colours back.
 *
 * Decoded in the page through a canvas, because the suite carries no image library. Taken at css
 * scale, so a point here is a point in the element's own box whatever the device's density. Cut
 * from the page rather than taken of the element, whose screenshot waits for a box that holds
 * still, and a slice next to one easing open does not.
 */
export async function pixelsOf(page: Page, target: Locator): Promise<Pixels> {
  await target.scrollIntoViewIfNeeded()
  const clip = (await target.boundingBox())!
  const png = (await page.screenshot({clip, animations: "disabled", scale: "css"})).toString("base64")
  const {width, height, data} = await page.evaluate(async (encoded) => {
    const image = new Image()
    image.src = `data:image/png;base64,${encoded}`
    await image.decode()
    const canvas = document.createElement("canvas")
    canvas.width = image.width
    canvas.height = image.height
    const context = canvas.getContext("2d")!
    context.drawImage(image, 0, 0)
    return {width: image.width, height: image.height, data: [...context.getImageData(0, 0, image.width, image.height).data]}
  }, png)
  return {
    width,
    height,
    at(x, y) {
      const i = (Math.round(y) * width + Math.round(x)) * 4
      return [data[i], data[i + 1], data[i + 2]]
    },
  }
}

/** The largest difference in any one channel between two colours. */
export function distance(a: Rgb, b: Rgb): number {
  return Math.max(...a.map((channel, i) => Math.abs(channel - b[i])))
}

/** The smallest box around every point [matches], or null when none does. */
export function boxWhere(pixels: Pixels, matches: (colour: Rgb) => boolean): {x: number, y: number, width: number, height: number} | null {
  let left = Infinity
  let top = Infinity
  let right = -Infinity
  let bottom = -Infinity
  for (let y = 0; y < pixels.height; y++) {
    for (let x = 0; x < pixels.width; x++) {
      if (!matches(pixels.at(x, y))) continue
      left = Math.min(left, x)
      top = Math.min(top, y)
      right = Math.max(right, x)
      bottom = Math.max(bottom, y)
    }
  }
  return left === Infinity ? null : {x: left, y: top, width: right - left + 1, height: bottom - top + 1}
}
