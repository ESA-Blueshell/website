import {describe, expect, it} from "vitest"
import {formatDate, formatDateNoSeconds} from "@/utils/timestamps"

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
})
