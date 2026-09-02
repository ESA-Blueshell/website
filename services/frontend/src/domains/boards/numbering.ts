/**
 * Which board comes next.
 *
 * A board's number is its place in the line, so the number of a board being written down
 * follows from the boards already recorded: ten of them exist, so the eleventh is derivable
 * and nobody has to remember it. Suggested rather than imposed — the field is still typed
 * over, because a board added out of order is a correction somebody may be making on purpose.
 *
 * Board knowledge rather than form knowledge, so it lives in the domain beside the other
 * reading rules and imports nothing (frontend ADR-001).
 */

/** The least of a board this rule reads. */
export interface Numbered {
  number: number
}

/**
 * One past the highest number recorded, and the first board where none is.
 *
 * The highest rather than the count: a history with a gap in it — a board nobody wrote down,
 * one removed since — would otherwise suggest a number that is already taken, and the api
 * refuses a number twice over. A number that is not a whole number above nought is not a
 * place in the line and is passed over.
 */
export function nextBoardNumber(boards: readonly Numbered[]): number {
  const highest = boards.reduce(
    (top, board) =>
      (Number.isInteger(board.number) && board.number > top ? board.number : top),
    0,
  )
  return highest + 1
}
