import type {Page} from "@playwright/test"

/**
 * How the island's strip asked for its scrolls, which is the one thing about a scroll a spec
 * cannot see.
 *
 * Whether a scroll was smooth or instant is nowhere in the dom and is over in a few hundred
 * milliseconds, so sampling for it is a flake waiting to happen. The strip's own request is
 * recorded instead, and where it ended up is asserted separately: between them they are the
 * whole of the claim.
 *
 * Shared by the board line and the esports seasons because the strip is one component drawn on
 * three pages: the recorder watches any element with the strip's own class, so it does not know
 * or care which page's line it is watching.
 */

declare global {
  interface Window {
    /** How the strip asked for each scroll it made, recorded by these specs alone. */
    stripScrolls?: string[]
  }
}

export const recordScrolls = (page: Page) => page.addInitScript(() => {
  window.stripScrolls = []
  const scrollTo = Element.prototype.scrollTo
  Element.prototype.scrollTo = function (this: Element, ...args: unknown[]) {
    const asked = args[0]
    if (this instanceof HTMLElement && this.classList.contains("timeline__scroll")) {
      const behavior = typeof asked === "object" && asked != null
        ? (asked as {behavior?: string}).behavior
        : undefined
      window.stripScrolls?.push(String(behavior ?? "auto"))
    }
    Reflect.apply(scrollTo, this, args)
  } as typeof Element.prototype.scrollTo
})

export const scrollsAsked = (page: Page) => page.evaluate(() => window.stripScrolls ?? [])

/** How far along its track a strip is scrolled. */
export const scrolledIn = (page: Page, selector: string) =>
  page.locator(selector).evaluate(box => box.scrollLeft)
