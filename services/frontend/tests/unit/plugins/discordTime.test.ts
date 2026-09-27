import {describe, expect, it} from "vitest"
import {timestampText} from "@/plugins/discordTime"

const NOW = Date.UTC(2026, 8, 25, 18, 0) // 25 September 2026, 18:00 UTC
const at = (seconds: number) => Math.round(NOW / 1000) + seconds

describe("a timestamp as Discord shows it", () => {
  it("reads each dated style in the reader's own language", () => {
    const unix = at(0)
    const date = new Date(unix * 1000)

    expect(timestampText(unix, "t")).toBe(new Intl.DateTimeFormat(undefined, {timeStyle: "short"}).format(date))
    expect(timestampText(unix, "F")).toBe(new Intl.DateTimeFormat(undefined, {dateStyle: "full", timeStyle: "short"}).format(date))
    expect(timestampText(unix, "d")).not.toBe(timestampText(unix, "D"))
  })

  it("reads the relative style from now, in the largest unit that fits", () => {
    const relative = new Intl.RelativeTimeFormat(undefined, {numeric: "auto"})

    expect(timestampText(at(3 * 24 * 3600), "R", NOW)).toBe(relative.format(3, "day"))
    expect(timestampText(at(-2 * 3600), "R", NOW)).toBe(relative.format(-2, "hour"))
    expect(timestampText(at(0), "R", NOW)).toBe(relative.format(0, "second"))
  })
})
