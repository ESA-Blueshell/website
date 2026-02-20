import {describe, expect, it} from "vitest"
import {safeFormatISO, toISO} from "@/utils/datetime"
import {DateTime} from "luxon"

describe("datetime utils", () => {
  it("formats valid ISO strings", () => {
    expect(safeFormatISO("2026-02-20T12:34:00.000Z", "yyyy-MM-dd")).toBe("2026-02-20")
  })

  it("returns empty string for invalid ISO strings", () => {
    expect(safeFormatISO("not-an-iso", "yyyy-MM-dd")).toBe("")
  })

  it("combines date and time into one ISO", () => {
    const result = toISO({date: "2026-02-20", time: "09:45"})
    expect(DateTime.fromISO(result).isValid).toBe(true)
    expect(result).toContain("2026-02-20")
  })

  it("uses base dateTime when only date is provided", () => {
    const result = toISO({date: "2026-03-01", dateTime: "2026-02-20T09:45:00.000Z"})
    const dt = DateTime.fromISO(result)
    expect(dt.isValid).toBe(true)
    expect(dt.toFormat("yyyy-MM-dd")).toBe("2026-03-01")
  })

  it("returns empty string when no inputs are provided", () => {
    expect(toISO({})).toBe("")
  })
})
