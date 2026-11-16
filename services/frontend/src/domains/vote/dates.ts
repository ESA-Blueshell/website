import {DateTime} from "luxon"

/** The three dates a vote runs on, as the board's form holds them: `yyyy-MM-ddTHH:mm` in Amsterdam time. */
export interface VoteDates {
  opensAt: string
  closesAt: string
  revealAt: string
}

const LOCAL = "yyyy-MM-dd'T'HH:mm"

// The association's own time: the December defaults mean it, so a board member abroad who keeps
// them must not move every date by their own offset.
const ZONE = "Europe/Amsterdam"

/** A moment from the api as the form shows it, in Amsterdam time. */
export const localOf = (iso: string): string => DateTime.fromISO(iso, {zone: ZONE}).toFormat(LOCAL)

/** A moment from the form, read as Amsterdam time, as the api takes it, or null where the form holds none. */
export function isoOf(local: string): string | null {
  const moment = DateTime.fromFormat(local, LOCAL, {zone: ZONE})
  return moment.isValid ? moment.toUTC().toISO({suppressMilliseconds: true}) : null
}

/** The December a vote runs in by default, in Amsterdam time: open all month to the 21st, revealed a week later. */
export const decemberDates = (year: number): VoteDates => ({
  opensAt: `${year}-12-01T00:00`,
  closesAt: `${year}-12-21T23:59`,
  revealAt: `${year}-12-28T20:00`,
})

/** A moment as a sentence says it to the reader, in their own time, such as "21 December 2026 at 23:59". */
export const said = (iso: string): string =>
  new Date(iso).toLocaleString("en-GB", {dateStyle: "long", timeStyle: "short"})
