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
