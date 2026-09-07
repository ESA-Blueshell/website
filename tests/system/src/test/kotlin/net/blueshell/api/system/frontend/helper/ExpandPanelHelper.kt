package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

/**
 * Waiting for a `v-expand-transition` panel to have finished opening, not merely to have appeared.
 *
 * The transition shows a panel at about two pixels and grows it for some 280ms. A submit button
 * inside `v-row align="end"` sits high in the clipped box and drops as it grows, and partway down
 * it holds one bounding box for about 100ms — two frames alike is all Playwright asks before it
 * calls an element stable, so a click aimed there is delivered to whatever has arrived at those
 * coordinates once the panel moves on. The handler is never entered and nothing is refused.
 */
object ExpandPanelHelper {
    /**
     * True once the panel has held the same non-zero height for three frames running.
     *
     * Counted on the node itself, so a reopened panel — a new node under `v-if` — starts from
     * nothing. Height is read rather than the `expand-transition` classes because those names are
     * Vuetify's own business and can change under us, while a box that has stopped growing is the
     * thing actually being waited for. Zero is excluded: the transition parks the panel there for
     * a frame before it starts, and stillness at zero is the wrong kind.
     */
    private const val SETTLED = """
        selector => {
          const el = document.querySelector(selector)
          if (!el) return false
          const height = el.getBoundingClientRect().height
          el.__settledFrames = height > 0 && el.__settledHeight === height ? (el.__settledFrames ?? 0) + 1 : 0
          el.__settledHeight = height
          return el.__settledFrames >= 2
        }
    """

    fun waitForOpened(page: Page, testId: String) {
        TestIdLocatorHelper.byTestId(page, testId).waitFor()
        page.waitForFunction(SETTLED, "[data-testid='$testId']")
    }
}
