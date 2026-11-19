import {DateTime} from "luxon"

/**
 * The association's own time: a date it sets, such as a vote's December, means Amsterdam wherever
 * the reader is. The api's AnnounceMorning reads the same zone; the two name each other.
 */
export const ASSOCIATION_ZONE = "Europe/Amsterdam"

/** [iso] as [fmt] in [zone], or "" where it is not a moment. */
export function formatISOIn(iso: string, fmt: string, zone: string): string {
  const dt = DateTime.fromISO(iso || "", {zone})
  return dt.isValid ? dt.toFormat(fmt) : ""
}

/** [text] in [fmt], read as a moment in [zone], as the api takes it, or null where it is not one. */
export function isoFrom(text: string, fmt: string, zone: string): string | null {
  const moment = DateTime.fromFormat(text, fmt, {zone})
  return moment.isValid ? moment.toUTC().toISO({suppressMilliseconds: true}) : null
}

/** The year it is now in [zone]. */
export const yearIn = (zone: string): number => DateTime.now().setZone(zone).year

export function safeFormatISO(iso: string, fmt: string) {
  const dt = DateTime.fromISO(iso || "")
  return dt.isValid ? dt.toFormat(fmt) : ""
}

/** Convert separate date/time edits to full ISO strings */
export function toISO(args: { date?: string; time?: string; dateTime?: string }): string {
  const {date, time, dateTime} = args
  const base = dateTime ? DateTime.fromISO(dateTime) : null
  const hasBase = !!base && base.isValid
  const d = date ?? (hasBase ? base!.toFormat("yyyy-MM-dd") : undefined)
  const t = time ?? (hasBase ? base!.toFormat("HH:mm") : undefined)
  if (!d && !t) return ""
  if (d && t) return DateTime.fromFormat(`${d} ${t}`, "yyyy-MM-dd HH:mm").toISO() || ""
  if (d) {
    return (
      DateTime.fromFormat(d, "yyyy-MM-dd")
        .set({hour: hasBase ? base!.hour : 0, minute: hasBase ? base!.minute : 0})
        .toISO() || ""
    )
  }
  if (t) {
    const ref = hasBase ? base! : DateTime.now()
    const [h, m] = (t || "00:00").split(":").map(Number)
    return ref.set({hour: h ?? 0, minute: m ?? 0}).toISO() || ""
  }
  return ""
}
