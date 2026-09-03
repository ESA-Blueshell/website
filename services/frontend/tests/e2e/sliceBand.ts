import type {Locator} from "@playwright/test"
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
