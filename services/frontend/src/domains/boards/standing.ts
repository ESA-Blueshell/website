/**
 * Where a board stands today: the one in office, the one that has not taken office yet, and the
 * ones that have handed over.
 *
 * Derived from the dates, never stored (`docs/CONTEXT.md`, **In office**). A flag would be a
 * second thing to keep right, and it would be wrong for a year at a time: the board that takes
 * office in the autumn is written down in the spring, and nobody goes back to the record on the
 * day it changes hands.
 *
 * Board knowledge rather than layout knowledge, so it lives in the domain beside the other
 * reading rules and imports nothing (frontend ADR-001). A number and two date strings are the
 * whole input, which is what lets the rule be read against the seeded history in a unit test.
 */

/**
 * The least of a board this rule reads: which board it is, and the stretch it runs.
 *
 * Structural rather than the generated `BoardResponse`, so the rule states what it needs and the
 * adapter satisfies it by answering with what the api already sends (frontend ADR-002).
 */
export interface Termed {
  number: number
  startDate: string
  /** Absent where a board has not handed over, or where nobody wrote the handover down. */
  endDate?: string | null
}

/** Where a board stands, in the association's own words. */
export type BoardStanding = "in office" | "candidate" | "past"

/**
 * Today, as the calendar date the reader is on.
 *
 * Composed from the local parts rather than sliced off an iso timestamp: `toISOString` is utc,
 * so an evening in the Netherlands is already tomorrow by it and a board would take office a
 * day early. Every date here is a plain `YYYY-MM-DD`, so the dates compare as strings.
 */
function today(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, "0")
  const day = String(now.getDate()).padStart(2, "0")
  return `${now.getFullYear()}-${month}-${day}`
}

/** The day part of a date, so a stored timestamp compares against a calendar date. */
const dayOf = (date?: string | null): string => (date ?? "").trim().slice(0, 10)

/**
 * A board that has not taken office yet: its term opens later than today.
 *
 * A candidate board is on the page from the day it is written down — that is how a board being
 * elected is recorded, and how the members on it are filled in before the handover. It is never
 * what a visitor is shown first, though: the association is still run by the board in office.
 */
export function isCandidate(board: Termed, on: string = today()): boolean {
  const from = dayOf(board.startDate)
  return from !== "" && from > dayOf(on)
}

/** Whether today falls inside a board's term. An unrecorded handover leaves the term open. */
function inTerm(board: Termed, on: string): boolean {
  const from = dayOf(board.startDate)
  if (from === "" || from > on) return false
  const until = dayOf(board.endDate)
  return until === "" || on <= until
}

/** Which of two boards is the newer, by the day its term opened and then by its number. */
const byAge = (left: Termed, right: Termed): number =>
  dayOf(left.startDate).localeCompare(dayOf(right.startDate)) || left.number - right.number

/**
 * The board in office: the one whose term contains today.
 *
 * Where no term does, the newest board that is not a candidate answers instead. That is the gap
 * between one board's handover and the next board's first day — a day or a fortnight most years,
 * and longer wherever an end date was written down as the end of August. Somebody arriving in
 * that gap is asking who runs the association, and the answer is the board that has been running
 * it, not a blank page and not a board that has not started.
 *
 * Null only where there are no boards at all, or where every board there is has yet to begin.
 *
 * A board whose start date cannot be read is passed over: an undated board cannot be shown to be
 * running the association, and answering with one would put a broken record on the page ahead of
 * every board that is properly dated.
 */
export function boardInOffice<T extends Termed>(boards: readonly T[], on: string = today()): T | null {
  const day = dayOf(on)
  const sitting = boards.find(board => inTerm(board, day))
  if (sitting) return sitting
  const began = (board: Termed) => dayOf(board.startDate) !== "" && !isCandidate(board, day)
  return boards.reduce<T | null>(
    (newest, board) =>
      began(board) && (newest == null || byAge(board, newest) > 0) ? board : newest,
    null,
  )
}

/**
 * Where one board stands among the rest.
 *
 * Against the whole set rather than on its own, because being in office is a fact about the line
 * of boards and not about one board's dates: only one board is in office, and which one it is
 * depends on what else has been written down.
 */
export function standingOf(
  board: Termed,
  boards: readonly Termed[],
  on: string = today(),
): BoardStanding {
  const day = dayOf(on)
  if (isCandidate(board, day)) return "candidate"
  return boardInOffice(boards, day)?.number === board.number ? "in office" : "past"
}
