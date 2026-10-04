/**
 * Timestamps as a management table shows them: in the reader's own locale, and never blank.
 *
 * A value that is not a date is handed back untouched rather than rendered as "Invalid Date" —
 * whatever the api sent is more use to whoever is reading the row than that.
 */

/** A moment down to the second, for a detail panel. */
export function formatDate(value?: string | null): string {
  if (!value) return "-"
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

/** The same moment without its seconds, for a row that is scanned rather than read. */
export function formatDateNoSeconds(value?: string | null): string {
  if (!value) return "-"
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString(undefined, {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  })
}

// Written out, not read from the locale: en-GB abbreviates September to "Sept", and a table reads better at three letters.
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]

/** A day as Management writes one: "1 Sep 2024". */
export function formatDay(value?: string | null): string {
  if (!value) return "-"
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getDate()} ${MONTHS[date.getMonth()]} ${date.getFullYear()}`
}

/** A moment as Management writes one: "Tue 29 Sep 03:00", with the year once it is not this one. */
export function formatMoment(value?: string | null): string {
  if (!value) return "-"
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const day = `${WEEKDAYS[date.getDay()]} ${date.getDate()} ${MONTHS[date.getMonth()]}`
  const dated = date.getFullYear() === new Date().getFullYear() ? day : `${day} ${date.getFullYear()}`
  return `${dated} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`
}
