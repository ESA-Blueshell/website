import {describe, expect, it} from "vitest"
import {decemberDates, isoOf, localOf, said} from "@/domains/vote"

describe("a vote's dates", () => {
  it("open on the first of December, close on the 21st and are revealed a week later by default", () => {
    expect(decemberDates(2027)).toEqual({opensAt: "2027-12-01T00:00", closesAt: "2027-12-21T23:59", revealAt: "2027-12-28T20:00"})
  })

  it("go from the form to the api and back unchanged", () => {
    const iso = isoOf("2026-12-21T23:59")!

    expect(iso).toMatch(/^2026-12-2\dT\d\d:59:00Z$/)
    expect(localOf(iso)).toBe("2026-12-21T23:59")
  })

  it("are none where the form holds none", () => {
    expect(isoOf("")).toBeNull()
    expect(isoOf("2026-12-21")).toBeNull()
  })

  it("are said as a sentence says them", () => {
    expect(said("2026-12-21T12:00:00Z")).toContain("21 December 2026")
  })
})
