/**
 * How a board is written down for a reader: its number, the year it ran, and the name it falls
 * back to when it has none recorded.
 *
 * Board knowledge rather than layout knowledge, so it lives in the domain and not beside the
 * markup. The same numeral is read three times over — the hero band's oversized figure, the
 * identity eyebrow, the fallback name — and three copies of the arithmetic is how they would
 * come to disagree.
 *
 * Nothing here touches the wire (frontend ADR-001): a number and two date strings are the whole
 * input, so a rule can be read without a response to hand.
 */

/** The numerals, largest first, which is the order one is built in. */
const NUMERALS: readonly [number, string][] = [
  [1000, "M"],
  [900, "CM"],
  [500, "D"],
  [400, "CD"],
  [100, "C"],
  [90, "XC"],
  [50, "L"],
  [40, "XL"],
  [10, "X"],
  [9, "IX"],
  [5, "V"],
  [4, "IV"],
  [1, "I"],
]

/**
 * A board's number as a Roman numeral: the ninth board reads `IX`.
 *
 * Roman because a board is one of a line rather than a row in a list, and the association counts
 * its own history in boards. An academic year stays in digits — a year is a date, not an ordinal.
 *
 * A number the numerals cannot spell — nought, a negative, a fraction — reads as its digits.
 * There is no such board, and a page that met one should still say something true about it
 * rather than nothing at all.
 */
export function romanNumeral(number: number): string {
  if (!Number.isInteger(number) || number < 1) return String(number)
  let left = number
  let written = ""
  for (const [value, numeral] of NUMERALS) {
    while (left >= value) {
      written += numeral
      left -= value
    }
  }
  return written
}

/** The calendar year an iso date names, or null where none can be read out of it. */
function yearIn(date?: string | null): number | null {
  const year = Number(date?.slice(0, 4))
  return Number.isInteger(year) && year > 0 ? year : null
}

/**
 * The academic year a board ran, written `2025-2026`.
 *
 * Composed from the board's dates and never stored, so correcting a date corrects the year
 * everywhere it is written and the two can never disagree.
 *
 * The first of the pair is the calendar year the board took office in, because a board changes
 * in the autumn and so its term always opens in the year it is named for. The second is the year
 * it handed over in — but only where that is a later year than the first. Everything else reads
 * as the year after the start:
 *
 * - **No end date recorded.** The column is nullable and the seed fills it for every board, so
 *   this is a board that has not handed over yet, or one whose handover nobody wrote down.
 *   Either way it is the board of the year it took office in, and reading it that way means the
 *   year does not change when the end date is finally recorded to the following autumn — which
 *   is the only date it can be.
 * - **A term that closed in its own opening year.** A board that stood down in the December of
 *   its own year still ran the board year it took office in, so that is what it reads as.
 *
 * A board that ran longer than a year reads the whole stretch, `2025-2027`, because that is what
 * its dates say and pretending otherwise would hide it.
 *
 * Empty where the start date cannot be read, which leaves whoever writes the eyebrow to say the
 * rest without it.
 */
export function academicYear(startDate: string, endDate?: string | null): string {
  const from = yearIn(startDate)
  if (from == null) return ""
  const to = yearIn(endDate)
  return `${from}-${to != null && to > from ? to : from + 1}`
}

/**
 * What a board is called: the name it chose for itself, or its number where it has none.
 *
 * Built from the numeral, so the fallback name reads as `Board IV` and matches the figure the
 * hero band is already showing. A recorded name that is blank counts as none — a board is named
 * or it is not, and an empty string is how "not" arrives over the wire.
 */
export function boardName(number: number, name?: string | null): string {
  const own = name?.trim()
  return own ? own : `Board ${romanNumeral(number)}`
}

/**
 * The identity eyebrow above a board: `BOARD IX · 2025-2026`.
 *
 * Composed here rather than in the markup because it is the numeral and the year again, and a
 * page that assembled the pair itself would be the fourth place the numeral is spelled. Drops
 * the year, separator and all, where the dates cannot be read — a hanging middle dot says less
 * than nothing.
 */
export function boardEyebrow(number: number, startDate: string, endDate?: string | null): string {
  const year = academicYear(startDate, endDate)
  const numeral = `BOARD ${romanNumeral(number)}`
  return year ? `${numeral} · ${year}` : numeral
}
