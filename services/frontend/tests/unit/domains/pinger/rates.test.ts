/**
 * How the boards print a rate and the moment it was set.
 */
import {describe, expect, it} from "vitest"
import {compactRate, setAt} from "@/domains/pinger"

describe("the rate words", () => {
  it("prints a rate the way a headline reads it", () => {
    expect(compactRate(2_400_000)).toBe("2.4M")
    expect(compactRate(9_300)).toBe("9.3K")
    expect(compactRate(640)).toBe("640")
    expect(compactRate(0)).toBe("0")
  })

  it("says only the time for a rate set today, and the day too for one set earlier", () => {
    const now = new Date(2026, 9, 9, 23, 0)

    expect(setAt(new Date(2026, 9, 9, 21, 14).toISOString(), now)).toBe("21:14")
    expect(setAt(new Date(2026, 9, 8, 9, 5).toISOString(), now)).toBe("8 Oct 09:05")
  })

  it("reads against the clock when no moment is given", () => {
    expect(setAt(new Date().toISOString())).toMatch(/^\d{2}:\d{2}$/)
  })
})
