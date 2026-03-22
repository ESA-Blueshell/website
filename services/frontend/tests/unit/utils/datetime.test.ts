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
    const base = "2026-02-20T09:45:00.000Z"
    const result = toISO({date: "2026-03-01", dateTime: base})
    const dt = DateTime.fromISO(result)
    expect(dt.isValid).toBe(true)
    expect(dt.toFormat("yyyy-MM-dd")).toBe("2026-03-01")
    expect(dt.toFormat("HH:mm")).toBe(DateTime.fromISO(base).toFormat("HH:mm"))
  })

  it("uses base date when only time is provided", () => {
    const base = "2026-02-20T09:45:00.000Z"
    const result = toISO({time: "14:15", dateTime: base})
    const dt = DateTime.fromISO(result)
    expect(dt.isValid).toBe(true)
    expect(dt.toFormat("yyyy-MM-dd")).toBe(DateTime.fromISO(base).toFormat("yyyy-MM-dd"))
    expect(dt.toFormat("HH:mm")).toBe("14:15")
  })

  it("creates midnight timestamp when only date is provided without a base", () => {
    const result = toISO({date: "2026-03-01"})
    const dt = DateTime.fromISO(result)
    expect(dt.isValid).toBe(true)
    expect(dt.toFormat("HH:mm")).toBe("00:00")
  })

  it("defaults missing minutes when only time hour is provided", () => {
    const result = toISO({time: "07"})
    const dt = DateTime.fromISO(result)
    expect(dt.isValid).toBe(true)
    expect(dt.toFormat("HH:mm")).toBe("07:00")
  })

  it("returns empty string for invalid base dateTime when no date/time override is provided", () => {
    expect(toISO({dateTime: "bad-input"})).toBe("")
  })

  it("returns empty string when no inputs are provided", () => {
    expect(toISO({})).toBe("")
  })
})
