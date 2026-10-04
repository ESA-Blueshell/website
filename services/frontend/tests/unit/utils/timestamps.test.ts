import {describe, expect, it} from "vitest"
import {formatDate, formatDateNoSeconds, formatDay, formatMoment} from "@/utils/timestamps"

describe("timestamps", () => {
  it("says nothing rather than nothing readable when there is no value", () => {
    expect(formatDate(undefined)).toBe("-")
    expect(formatDate(null)).toBe("-")
    expect(formatDate("")).toBe("-")
    expect(formatDateNoSeconds(undefined)).toBe("-")
  })

  it("hands back what it cannot read, rather than 'Invalid Date'", () => {
    expect(formatDate("not-a-date")).toBe("not-a-date")
    expect(formatDateNoSeconds("not-a-date")).toBe("not-a-date")
  })

  it("renders a moment, and the row version without its seconds", () => {
    expect(formatDate("2026-01-15T10:30:45Z")).toMatch(/2026/)
    const row = formatDateNoSeconds("2026-01-15T10:30:45Z")
    expect(row).toMatch(/2026/)
    expect(row).not.toMatch(/45/)
  })

  it("writes a day as Management does, with a three-letter month", () => {
    expect(formatDay("2024-09-01")).toBe("1 Sep 2024")
    expect(formatDay(null)).toBe("-")
    expect(formatDay("soon")).toBe("soon")
  })

  it("writes a moment with its weekday, and its year once that is not this one", () => {
    const thisYear = new Date().getFullYear()
    expect(formatMoment(`${thisYear}-03-04T03:05:00`)).toMatch(/^[A-Z][a-z]{2} 4 Mar 03:05$/)
    expect(formatMoment("2020-09-29T15:00:00")).toBe("Tue 29 Sep 2020 15:00")
    expect(formatMoment(undefined)).toBe("-")
    expect(formatMoment("soon")).toBe("soon")
  })
})
