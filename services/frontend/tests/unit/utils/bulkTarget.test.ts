import {describe, expect, it} from "vitest"
import {halfYearCutoffDefault} from "@/utils/bulkTarget"

describe("halfYearCutoffDefault", () => {
  it("returns the midpoint + 1 month, first of that month, for a full calendar year", () => {
    // 2025-01-01 .. 2025-12-31: span 364 days, midpoint 2025-07-02, +1 month → 2025-08-02,
    // day set to 1 → 2025-08-01.
    expect(halfYearCutoffDefault({startDate: "2025-01-01", endDate: "2025-12-31"})).toBe("2025-08-01")
  })

  it("clamps to the period end when +1 month would overshoot", () => {
    // A short period: midpoint mid-month, +1 month lands past the end → clamped to end.
    const result = halfYearCutoffDefault({startDate: "2025-01-01", endDate: "2025-01-20"})
    expect(result).toBe("2025-01-20")
  })

  it("stays within the period bounds", () => {
    const start = "2025-03-01"
    const end = "2025-09-30"
    const result = halfYearCutoffDefault({startDate: start, endDate: end})
    expect(result >= start).toBe(true)
    expect(result <= end).toBe(true)
    // Midpoint 2025-06-15, +1 month → 2025-07-15, day 1 → 2025-07-01.
    expect(result).toBe("2025-07-01")
  })

  it("returns an empty string for a missing or invalid period", () => {
    expect(halfYearCutoffDefault(null)).toBe("")
    expect(halfYearCutoffDefault(undefined)).toBe("")
    expect(halfYearCutoffDefault({startDate: "", endDate: ""})).toBe("")
  })
})
