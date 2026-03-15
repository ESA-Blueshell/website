import {DateTime} from "luxon"

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
