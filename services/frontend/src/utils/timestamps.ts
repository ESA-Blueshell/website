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
