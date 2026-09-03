import type {Page} from "@playwright/test"

/**
 * A line of boards long enough to travel along, and how to watch the strip travel it.
 *
 * Shared by the ordinary board spec and the motion one beside it, because the same line has to
 * be walked under both settings: the band follows a finger whatever the visitor has asked for,
 * and the difference between them is only in what happens once the finger has lifted.
 */

export const SCROLLER = "[data-testid=\"board-timeline\"] .timeline__scroll"

declare global {
  interface Window {
    /** How the strip asked for each scroll it made, recorded by these specs alone. */
    stripScrolls?: string[]
  }
}

const member = (id: number, boardId: number, role: string) => ({
  id, boardId, userId: null, role, name: `Member ${id}`, nickname: null,
  description: null, image: null, portrait: null,
  startDate: "2020-09-01", endDate: "2021-08-31", version: 0,
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
})

const board = (number: number, name: string, from: number, members: number) => ({
  id: number, number, name, candidate: `Board ${number}`, cheer: null, accent: null,
  description: null, startDate: `${from}-09-01`,
  // The newest board's term is left open, so it is the one in office whatever day this runs on.
  endDate: number === 6 ? null : `${from + 1}-08-31`,
  image: null, photo: null, version: 0,
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
  members: Array.from({length: members}, (_, at) =>
    member(number * 10 + at, number, at === 0 ? "Chairman" : "Treasurer")),
})

/**
 * Six boards of unequal size, newest first as the api answers.
 *
 * Six because five is where the strip stops shrinking and starts scrolling, and a line that fits
 * its own window cannot be watched travelling. Unequal because the band stands at the height of
 * the board showing while the board beside it is drawn out of the flow, so a commit has a height
 * to carry as well as a width, and boards of one size would never show it.
 *
 * No photographs and no portraits: what is being watched here is a movement, and a line this
 * long with a photograph on every board is a page of fetches to no purpose.
 */
export const sixBoards = [
  board(6, "Rainbow road", 2025, 2),
  board(5, "Eeveelutions", 2024, 3),
  board(4, "Overcooked", 2023, 1),
  board(3, "Drieden", 2022, 2),
  board(2, "Tweeden", 2021, 6),
  board(1, "Eersteling", 2020, 1),
]

/** How far along its track the strip is scrolled. */
export const scrolled = (page: Page) => page.locator(SCROLLER).evaluate(box => box.scrollLeft)

/**
 * How the strip asked for its scrolls, which is the one thing about a scroll a spec cannot see.
 *
 * Whether a scroll was smooth or instant is nowhere in the dom and is over in a few hundred
 * milliseconds, so sampling for it is a flake waiting to happen. The strip's own request is
 * recorded instead, and where it ended up is asserted separately: between them they are the
 * whole of the claim.
 */
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
