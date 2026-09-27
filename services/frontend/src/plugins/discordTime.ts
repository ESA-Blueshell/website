/** Discord's timestamp styles, each written `<t:unix:style>`; `f` where none is written. */
export const TIME_STYLES = ["t", "T", "d", "D", "f", "F", "R"] as const
export type TimeStyle = typeof TIME_STYLES[number]

const FORMATS: Record<Exclude<TimeStyle, "R">, Intl.DateTimeFormatOptions> = {
  t: {timeStyle: "short"},
  T: {timeStyle: "medium"},
  d: {dateStyle: "short"},
  D: {dateStyle: "long"},
  f: {dateStyle: "long", timeStyle: "short"},
  F: {dateStyle: "full", timeStyle: "short"},
}

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ["year", 365 * 24 * 3600],
  ["month", 30 * 24 * 3600],
  ["day", 24 * 3600],
  ["hour", 3600],
  ["minute", 60],
  ["second", 1],
]

/** A moment as Discord shows it in a style, in the reader's own language and time zone. */
export const timestampText = (unix: number, style: TimeStyle, now: number = Date.now()): string => {
  if (style !== "R") return new Intl.DateTimeFormat(undefined, FORMATS[style]).format(new Date(unix * 1000))
  const seconds = unix - Math.round(now / 1000)
  const [unit, size] = UNITS.find(([, length]) => Math.abs(seconds) >= length) ?? ["second", 1]
  return new Intl.RelativeTimeFormat(undefined, {numeric: "auto"}).format(Math.round(seconds / size), unit)
}

/** `<t:unix>` or `<t:unix:style>`, as Discord writes a timestamp. */
export const TIMESTAMP = /<t:(-?\d{1,12})(?::([tTdDfFR]))?>/g
