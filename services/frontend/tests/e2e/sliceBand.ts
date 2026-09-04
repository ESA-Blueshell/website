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
 * How the band at [swipe] is drawn in the frame [testid] is first on the page: whether that
 * slice is open, and how many panels the band holds, two of them saying the pass is still on.
 *
 * Read at a frame boundary rather than polled, since the claim is about the first frame. [gate]
 * is what says the arriving stop is the one being read, where the slice is not itself that proof;
 * the band leaving answers to no name, its testids taken off it at the swap.
 */
export async function landing(
  page: Page, swipe: string, testid: string, gate = testid,
): Promise<{open: boolean, panels: number}> {
  const handle = await page.waitForFunction(([sel, id, key]) => {
    if (!document.querySelector(`[data-testid="${key}"]`)) return null
    const slice = document.querySelector(`[data-testid="${id}"]`)
    return {
      open: slice?.className.includes("slice--open") ?? false,
      panels: document.querySelectorAll(`${sel} > *`).length,
    }
  }, [swipe, testid, gate] as const)
  return handle.jsonValue()
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
 */
export async function aimedAt(page: Page, swipe: string): Promise<number> {
  const handle = await page.waitForFunction((sel) => {
    const aim = (document.querySelector(sel) as HTMLElement | null)?.style.height
    return aim ? Math.round(parseFloat(aim)) : null
  }, swipe)
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
