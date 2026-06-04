import {DateTime} from "luxon"

/** The day the first board signed Blueshell Esports' official Statutes. */
export const ASSOCIATION_FOUNDED = DateTime.fromISO("2017-12-12")

/** Completed years since the association was founded. */
export function associationYears(now: DateTime = DateTime.now()): number {
  return Math.floor(now.diff(ASSOCIATION_FOUNDED, "years").years)
}

const ORDINAL_WORDS = [
  "first", "second", "third", "fourth", "fifth",
  "sixth", "seventh", "eighth", "ninth", "tenth",
  "eleventh", "twelfth",
] as const

/** Ordinal word for a count, falling back to numeric ordinals (13th, 21st …). */
export function ordinalWord(n: number): string {
  if (n >= 1 && n <= ORDINAL_WORDS.length) {
    return ORDINAL_WORDS[n - 1]!
  }
  let suffix = "th"
  if (n % 10 === 1 && n % 100 !== 11) suffix = "st"
  else if (n % 10 === 2 && n % 100 !== 12) suffix = "nd"
  else if (n % 10 === 3 && n % 100 !== 13) suffix = "rd"
  return `${n}${suffix}`
}

/** Ordinal word for the association's current year of existence. */
export function associationYearOrdinal(now: DateTime = DateTime.now()): string {
  return ordinalWord(associationYears(now))
}
