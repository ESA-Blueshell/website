import type {Locator, Page} from "@playwright/test"
import {expect} from "./test"

/**
 * Presses a slice, having first put it where pressing it will not scroll the page.
 *
 * Stacked, the scroll decides which slice is open, and a scroll releases a tap by design: the
 * choice stands until the visitor scrolls, at which point the scroll is their intent again.
 * Playwright scrolls an element into view as part of clicking it, and that scroll event is
 * delivered asynchronously, so a press can be undone by its own scroll arriving after it.
 *
 * Scrolling first and pressing second is the order a finger makes, and it is deterministic.
 *
 * Shared, because the band is shared: the deterministic spec and the motion spec press the same
 * way for the same reason, and one of the two drifting is one of the two going flaky.
 */
export async function pressSlice(slice: Locator): Promise<void> {
  await slice.scrollIntoViewIfNeeded()
  await expect(slice).toBeInViewport()
  await slice.getByRole("button").click()
}

/**
 * Presses a slice's pencil, once the band under it has stopped moving.
 *
 * The same trap as [pressSlice] and a worse one. Playwright scrolls before it clicks, and that
 * scroll drives the stacked band's observer, so the slice opens and shuts and the pencil is
 * never still long enough to be pressed; the scroll is also the smallest one that works, which
 * parks a pencil sitting ten pixels off the top of its slice under the fixed site bar. Put in
 * the middle of the window and left to settle, the click has nothing left to scroll.
 */
export async function pressSliceEdit(pencil: Locator): Promise<void> {
  await pencil.evaluate((el) => el.scrollIntoView({block: "center"}))
  await settled(pencil)
  await expect(pencil).toBeInViewport()
  await pencil.click()
}

/**
 * Resolves once [element] has stood at one box for [frames] frames running.
 *
 * Two things move the band after a gesture — the spring back home, and the slice whichever
 * scroll came last opened — and either of them under the pointer is a press that lands
 * somewhere else. Counted on the page rather than polled from the runner, since a poll reads
 * whichever frames the round trip happens to fall on.
 */
export async function settled(element: Locator, frames = 5): Promise<void> {
  await element.evaluate((el, want) => new Promise<void>((resolve) => {
    let last = ""
    let same = 0
    const tick = () => {
      const {x, y, width, height} = el.getBoundingClientRect()
      const box = [x, y, width, height].map(Math.round).join()
      same = box === last ? same + 1 : 0
      last = box
      if (same >= want) resolve()
      else requestAnimationFrame(tick)
    }
    requestAnimationFrame(tick)
  }), frames)
}

/**
 * Every frame of a slice from the frame it is first on the page, for [frames] of them.
 *
 * Read off the page rather than polled from the runner: what is claimed is how the band was
 * drawn as it landed, and a poll reads whichever frames a round trip happens to fall on. Begun
 * before whatever the arrival is waiting on, because an arrival is a round trip or two away and
 * a window that may not contain the movement it exists to rule out is not a test of anything.
 *
 * Shared, because both bands make the same claim: a slice a gesture carried in is open in the
 * frame it is first drawn in and at one height thereafter.
 */
export function framesOf(page: Page, testid: string, frames = 42): Promise<{open: boolean, height: number}[]> {
  return page.evaluate(([id, count]) => new Promise<{open: boolean, height: number}[]>((resolve) => {
    const taken: {open: boolean, height: number}[] = []
    const tick = () => {
      const slice = document.querySelector(`[data-testid="${id}"]`)
      if (slice) {
        taken.push({
          open: slice.querySelector("[aria-expanded]")?.getAttribute("aria-expanded") === "true",
          height: Math.round(slice.getBoundingClientRect().height),
        })
      }
      if (taken.length < count) requestAnimationFrame(tick)
      else resolve(taken)
    }
    requestAnimationFrame(tick)
  }), [testid, frames] as const)
}

/**
 * The claim both arrivals make about [seen]: open in the frame the slice is first drawn in and
 * in every frame after, at one height the whole way.
 *
 * Shared, because a swipe and a late answer make it about the same slice: the gesture was the
 * animation, so nothing grows once the finger has left the glass.
 */
export function arrivedOpen(seen: {open: boolean, height: number}[]): void {
  expect(seen.length).toBeGreaterThan(8)
  expect(seen.filter(frame => !frame.open)).toEqual([])
  expect([...new Set(seen.map(frame => frame.height))]).toHaveLength(1)
}

/**
 * Watches the band at [swipe] from now on, answering with a read of the frame [gate] is first
 * drawn in: whether [testid] is open in it, and how many panels the band holds, two of them
 * saying the pass is still on.
 *
 * Begun before the change is asked for, and counted on the page. A pass is over in the time a
 * visitor who asked for less motion allows it — a tenth of a second — so a watch installed
 * after the click is a race against the runner's own round trips, and a loaded one loses it:
 * the band then answers with one panel for a pass that did happen. [gate] is what says the
 * arriving stop is the one being read, where the slice is not itself that proof; the band
 * leaving answers to no name, its testids taken off it at the swap.
 */
export async function landingFrom(
  page: Page, swipe: string, testid: string, gate = testid,
): Promise<() => Promise<{open: boolean, panels: number}>> {
  await page.evaluate(([sel, id, key]) => {
    const tick = () => {
      if (!document.querySelector(`[data-testid="${key}"]`)) return requestAnimationFrame(tick)
      const slice = document.querySelector(`[data-testid="${id}"]`)
      ;(window as unknown as {__landing?: unknown}).__landing = {
        open: slice?.className.includes("slice--open") ?? false,
        panels: document.querySelectorAll(`${sel} > *`).length,
      }
    }
    delete (window as unknown as {__landing?: unknown}).__landing
    requestAnimationFrame(tick)
  }, [swipe, testid, gate] as const)

  return async () => {
    const handle = await page.waitForFunction(() => (window as unknown as {
      __landing?: {open: boolean, panels: number}
    }).__landing ?? null)
    return handle.jsonValue()
  }
}

/** The height the band at [swipe] is holding right now, or nothing where it holds none. */
export function heldHeight(page: Page, swipe: string): Promise<number | null> {
  return page.evaluate((sel) => {
    const aim = (document.querySelector(sel) as HTMLElement | null)?.style.height
    return aim ? Math.round(parseFloat(aim)) : null
  }, swipe)
}

/**
 * The height a pass is aiming at, waited for on the page rather than polled from the runner.
 *
 * Both of the band's height animations set the resting height on the element before animating
 * over it, so what is written there is the end of the pass stated at the start of it — which is
 * the intention, where a height sampled mid-pass is only a frame of one.
 *
 * A pass that swaps the stop pins the height it is leaving first, a tick before it can measure
 * the one it is arriving at, so the first inline height there is where the band was rather than
 * where it is going. [stoodAt] is the height it is leaving, and tells the two apart.
 */
export async function aimedAt(page: Page, swipe: string, stoodAt: number): Promise<number> {
  const handle = await page.waitForFunction(([sel, held]) => {
    const aim = (document.querySelector(sel) as HTMLElement | null)?.style.height
    if (!aim) return null
    const px = Math.round(parseFloat(aim))
    return px === held ? null : px
  }, [swipe, stoodAt] as const)
  return handle.jsonValue()
}

/**
 * Watches the band at [swipe] for every height it is given from now on, answering with them.
 *
 * A height is held for the length of a pass and released at the end of one, so sampling after
 * the fact proves nothing about a change that held none: what is claimed is that none was held.
 */
export async function heightsHeldFrom(page: Page, swipe: string): Promise<() => Promise<string[]>> {
  await page.evaluate((sel) => {
    const el = document.querySelector(sel) as HTMLElement
    const seen: string[] = []
    ;(window as unknown as {__heldHeights: string[]}).__heldHeights = seen
    new MutationObserver(() => {
      if (el.style.height) seen.push(el.style.height)
    }).observe(el, {attributes: true, attributeFilter: ["style"]})
  }, swipe)
  return () => page.evaluate(() => (window as unknown as {__heldHeights: string[]}).__heldHeights)
}
