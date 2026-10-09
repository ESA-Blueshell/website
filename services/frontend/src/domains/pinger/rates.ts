/*
 * How the SNTPings boards print a rate and the moment it was set. The api ships the number and the
 * instant; the words are composed here.
 */

const COMPACT = new Intl.NumberFormat("en", {notation: "compact", maximumFractionDigits: 1})

/** A rate the way a headline reads it: 2.4M, 9.3K, 640. */
export const compactRate = (pps: number): string => COMPACT.format(pps)

const sameDay = (a: Date, b: Date): boolean =>
  a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()

/** When a rate was set, in the reader's own time: 21:14 today, or 5 Dec 21:14 on another day. */
export function setAt(at: string, now: Date = new Date()): string {
  const when = new Date(at)
  const time = when.toLocaleTimeString("en-GB", {hour: "2-digit", minute: "2-digit"})
  if (sameDay(when, now)) return time
  return `${when.toLocaleDateString("en-GB", {day: "numeric", month: "short"})} ${time}`
}
