import {describe, expect, it} from "vitest"
import {
  allCountriesSorted,
  cca2Map,
  countriesWithFlagSorted,
  customFilterForCountry,
  deburrLower,
  displayCountry,
  displayNationality,
  findTopMatch,
  isValidCca2,
  partsFor,
} from "@/composables/countries"

describe("countries composable helpers", () => {
  it("normalizes accents", () => {
    expect(deburrLower("Åland")).toBe("aland")
  })

  it("contains countries in sorted collections", () => {
    expect(allCountriesSorted.length).toBeGreaterThan(0)
    expect(countriesWithFlagSorted.length).toBeGreaterThan(0)
    expect(cca2Map.has("NL")).toBe(true)
  })

  it("validates ISO country codes", () => {
    expect(isValidCca2("NL")).toBe(true)
    expect(isValidCca2("ZZ")).toBe(false)
    expect(isValidCca2(undefined)).toBe(false)
  })

  it("matches countries by top query result", () => {
    const result = findTopMatch("nederland", allCountriesSorted)
    expect(result?.cca2).toBe("NL")
  })

  it("builds searchable parts for a country", () => {
    const nl = cca2Map.get("NL")
    expect(nl).toBeDefined()
    expect(partsFor(nl!)).toContain("nederland")
  })

  it("applies custom country filter", () => {
    const nl = cca2Map.get("NL")!
    const matches = customFilterForCountry("", "dutch", {raw: nl} as never)
    const misses = customFilterForCountry("", "not-a-country", {raw: nl} as never)
    expect(matches).toBe(true)
    expect(misses).toBe(false)
  })

  it("formats country display strings", () => {
    const nl = cca2Map.get("NL")!
    expect(displayCountry(nl)).toContain("Netherlands")
    expect(displayNationality(nl)).toContain("Dutch")
  })
})
