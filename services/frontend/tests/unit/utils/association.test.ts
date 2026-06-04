import {describe, expect, it} from "vitest"
import {DateTime} from "luxon"
import {ASSOCIATION_FOUNDED, associationYearOrdinal, associationYears, ordinalWord} from "@/utils/association"

describe("association utils", () => {
  it("anchors the founding date to the day the Statutes were signed", () => {
    expect(ASSOCIATION_FOUNDED.toISODate()).toBe("2017-12-12")
  })

  it("counts completed years since founding", () => {
    expect(associationYears(DateTime.fromISO("2026-06-04"))).toBe(8)
  })

  it("rolls over only after the anniversary", () => {
    expect(associationYears(DateTime.fromISO("2027-12-11"))).toBe(9)
    expect(associationYears(DateTime.fromISO("2027-12-12"))).toBe(10)
  })

  it("renders ordinal words for small counts", () => {
    expect(ordinalWord(1)).toBe("first")
    expect(ordinalWord(8)).toBe("eighth")
    expect(ordinalWord(12)).toBe("twelfth")
  })

  it("falls back to numeric ordinals past the word table", () => {
    expect(ordinalWord(13)).toBe("13th")
    expect(ordinalWord(21)).toBe("21st")
    expect(ordinalWord(22)).toBe("22nd")
    expect(ordinalWord(23)).toBe("23rd")
  })

  it("renders the current year of existence as an ordinal word", () => {
    expect(associationYearOrdinal(DateTime.fromISO("2026-06-04"))).toBe("eighth")
  })
})
