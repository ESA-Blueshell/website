import type {BandDirection} from "@/components/island/BandSwipe.vue"
import type {Stop} from "@/components/island/stripAxis"
import {academicYear, boardName} from "./reading"
import {standingOf, type Termed} from "./standing"

/**
 * The boards as stops on the island's strip.
 *
 * Where the two halves of the timeline meet, the way `seasonAxis` does it for seasons: the
 * strip's arithmetic knows about stops and shares of a width, and what a board is called, which
 * academic year it ran and whether it is the one in office is knowledge about boards. Both live
 * on their own side of the seam, so the strip carries neither.
 *
 * A stop is identified by the board's **number** rather than by its database key, because the
 * number is what the url carries and what a reader is shown. The two are not the same value and
 * the vocabulary is deliberate about it (`docs/CONTEXT.md`, **Number, not ID**).
 */

/** The least of a board a stop is composed from: its number, its name, its term, its colour. */
export interface Stopped extends Termed {
  name?: string | null
  accent?: string | null
}

/**
 * The order boards read in: oldest first, by the term they ran.
 *
 * One comparator for the module, because the strip's left-to-right order and the direction a
 * board change travels in are the same question asked twice. Two boards recorded with the same
 * start date are ordered by number, so a line drawn twice is drawn the same way.
 */
const byTerm = (a: Stopped, b: Stopped): number =>
  a.startDate.localeCompare(b.startDate) || a.number - b.number

/**
 * Which way [to] lies from [from]: back down the line, or on up it.
 *
 * The line runs oldest to newest from left to right, so this is also which way the page travels
 * when the board changes. Either end being absent is "same": there is no direction to travel
 * from nowhere, which is what a page arriving for the first time does.
 */
export function travelBetween(from: Stopped | null, to: Stopped | null): BandDirection {
  if (!from || !to) return "same"
  const order = byTerm(to, from)
  if (order < 0) return "past"
  if (order > 0) return "future"
  return "same"
}

/** What a stop says about a board that is not simply another year: there are two such words. */
const MARKS = {
  "in office": "In office",
  candidate: "Candidate",
  past: "",
} as const

/**
 * The boards as stops, oldest first, each marked where it is more than another year of history.
 *
 * Oldest first because the strip runs left to right and the association's history does too, so
 * the lit stretch grows as a reader moves towards the present.
 *
 * The mark is composed here for the same reason the label is: "in office" is what the
 * association calls the board running it, and the strip has no idea what it is drawing stops for.
 *
 * A stop's spoken name carries all three (the board's name, its year and its mark) because the
 * two labels and the mark are drawn for the eye and hidden from a screen reader, which then has
 * only this to go on.
 */
export function boardStops(boards: readonly Stopped[], on?: string): Stop[] {
  const oldest = [...boards].sort(byTerm)
  return oldest.map(board => {
    const title = boardName(board.number, board.name)
    const year = academicYear(board.startDate, board.endDate)
    const mark = MARKS[standingOf(board, boards, on)]
    return {
      id: board.number,
      name: [title, year, mark.toLowerCase()].filter(Boolean).join(", "),
      label: title,
      sublabel: year,
      mark,
      accent: board.accent?.trim() || undefined,
    }
  })
}
