import type {Page} from "@playwright/test"
import {expect} from "./test"

/**
 * Chooses an entry from the user manager's bulk actions menu once the menu has stopped moving.
 *
 * The menu grows out of its button over a transition that reduced motion does not switch off.
 * Playwright can read an entry as stable part way through it, and a press there lands where the
 * entry has just left, so nothing opens. A click also scrolls its target into view first, and the
 * menu is anchored to its button, so that scroll moves the entry again. So the button goes to the
 * middle of the window first, where the whole menu opens in view and neither press has anything
 * to scroll, and the entry is pressed once its transition is over.
 */
export async function chooseBulkAction(page: Page, testid: string): Promise<void> {
  const button = page.getByTestId("bulk-actions-menu-btn")
  await button.evaluate(el => el.scrollIntoView({block: "center", behavior: "instant"}))
  await button.click()
  const entry = page.getByTestId(testid)
  await expect.poll(() => entry.evaluate(el =>
    el.closest(".v-overlay__content")?.getAnimations({subtree: true}).length ?? 0)).toBe(0)
  await expect(entry).toBeInViewport({ratio: 1})
  await entry.click()
}
