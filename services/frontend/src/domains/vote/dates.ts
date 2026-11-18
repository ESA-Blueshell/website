import {ASSOCIATION_ZONE, formatISOIn, isoFrom} from "@/utils/datetime"
import type {VoteSchedule} from "./adapters/vote"

// The board's form holds a date as `yyyy-MM-ddTHH:mm` in Amsterdam time: the December defaults
// mean it, so a board member abroad who keeps them must not move every date by their own offset.
const LOCAL = "yyyy-MM-dd'T'HH:mm"

/** A moment from the api as the form shows it, in Amsterdam time. */
export const localOf = (iso: string): string => formatISOIn(iso, LOCAL, ASSOCIATION_ZONE)

/** A moment from the form, read as Amsterdam time, as the api takes it, or null where the form holds none. */
export const isoOf = (local: string): string | null => isoFrom(local, LOCAL, ASSOCIATION_ZONE)

/** The December a vote runs in by default, in Amsterdam time: open all month to the 21st, revealed a week later. */
export const decemberDates = (year: number): VoteSchedule => ({
  opensAt: `${year}-12-01T00:00`,
  closesAt: `${year}-12-21T23:59`,
  revealAt: `${year}-12-28T20:00`,
})

/**
 * A moment as a sentence says it to the reader, in their own time, such as "21 December 2026 at
 * 23:59". The page's sentences are British English, so the date is too, whatever the browser's locale.
 */
export const said = (iso: string): string =>
  new Date(iso).toLocaleString("en-GB", {dateStyle: "long", timeStyle: "short"})

/** A moment as a tag says it, short, in the reader's own time, such as "21 Dec, 23:59". */
export const saidShort = (iso: string): string =>
  new Date(iso).toLocaleString("en-GB", {day: "numeric", month: "short", hour: "2-digit", minute: "2-digit"})
