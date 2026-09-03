import type {Locator, Page} from "@playwright/test"

/**
 * Dragging a band sideways, as a finger does it.
 *
 * Driven with the pointer rather than with a synthesized touch, because the band binds the
 * gesture where the pointer is *coarse* rather than where an event says "touch": on a phone the
 * two are the same thing, and in a suite the first is a device the harness already emulates while
 * the second is a stream of events assembled by hand. So a spec in a phone-shaped project drags
 * exactly what a phone drags.
 *
 * Every move is a frame apart. A drag delivered as fast as the harness can send it is a flick
 * whatever its length — the last two samples land a couple of milliseconds apart, which is a
 * pace no thumb reaches — and a spec meaning to prove that a short drag springs back would then
 * prove the opposite. A pace is not a thing to assert through a browser; that is what the unit
 * tests on the axis are for. Here the distance does the deciding.
 */
export interface Drag {
  /**
   * How far to drag, in pixels, positive rightwards.
   *
   * Several of them are the legs of one journey made without lifting the finger, which is how a
   * gesture that changed its mind halfway is expressed.
   */
  by: number | number[]
  /** Where to begin, as a share of the window's width. Defaults to the side the drag comes from. */
  at?: number
  /** What the drag must begin on, where that is the point of it. Defaults to the band itself. */
  on?: Locator
  /** How many moves it is delivered in, and how long apart. */
  steps?: number
  pause?: number
  /** Whether to let go at the end, or leave the finger where it is. */
  release?: boolean
}

export async function dragBand(page: Page, band: Locator, drag: Drag): Promise<void> {
  const {by, on, steps = 12, pause = 16, release = true} = drag
  const legs = Array.isArray(by) ? by : [by]
  const at = drag.at ?? ((legs[0] ?? 0) > 0 ? 0.12 : 0.88)
  const view = page.viewportSize()!

  await band.scrollIntoViewIfNeeded()
  const box = (await (on ?? band).boundingBox())!
  // Somewhere on what is being dragged and inside the window, since the band is taller than a
  // phone and a pointer outside the window is a pointer the browser never reports.
  const y = Math.min(Math.max(box.y + box.height / 2, 8), view.height - 8)
  const x = Math.round(view.width * at)

  await page.mouse.move(x, y)
  await page.mouse.down()
  let from = x
  for (const leg of legs) {
    for (let step = 1; step <= steps; step += 1) {
      await page.mouse.move(Math.round(from + (leg * step) / steps), y)
      await page.waitForTimeout(pause)
    }
    from += leg
  }
  if (release) await page.mouse.up()
}

/** Where each panel of a band stands along the axis it is dragged on, in pixels from home. */
export async function standing(page: Page, selector: string): Promise<number[]> {
  return page.locator(selector).evaluateAll(panels => panels.map(
    panel => new DOMMatrix(getComputedStyle(panel).transform).m41,
  ))
}
