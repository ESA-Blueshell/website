import {describe, expect, it} from "vitest"
import {DateTime} from "luxon"
import {feeQuote} from "@/domains/association/fees"
import type {ContributionPeriod} from "@/domains/association/adapters/association"

const period: ContributionPeriod = {
  id: 1,
  startDate: "2025-09-01",
  endDate: "2026-08-31",
  halfYearCutoffDate: "2026-02-01",
  fullYearFee: 20,
  halfYearFee: 12.5,
  alumniFee: 10,
  createdAt: "2025-09-01T00:00:00.000Z",
  updatedAt: "2025-09-01T00:00:00.000Z",
  version: 1,
}

const inside = DateTime.fromISO("2026-01-15")
const after = DateTime.fromISO("2026-09-04")

const amountOf = (quote: ReturnType<typeof feeQuote>, id: string) =>
  quote?.fees.find(fee => fee.id === id)?.amount

describe("feeQuote", () => {
  it("prices the year in the association's own currency", () => {
    const quote = feeQuote(period, inside)

    expect(quote?.year).toBe("2025/2026")
    expect(amountOf(quote, "full-year")).toContain("20,00")
    expect(amountOf(quote, "half-year")).toContain("12,50")
    expect(amountOf(quote, "alumni")).toContain("10,00")
  })

  it("says fees are subject to change while the year is running", () => {
    const quote = feeQuote(period, inside)

    expect(quote?.settled).toBe(true)
    expect(quote?.note).toContain("subject to change")
    expect(quote?.note).toContain("General Members Meeting")
  })

  // Past the end of the period the amounts are last year's, and saying so is the whole point
  // of the note: a visitor in September is reading a price that has not been set yet.
  it("names the year the amounts belong to once that year is over", () => {
    const quote = feeQuote(period, after)

    expect(quote?.settled).toBe(false)
    expect(quote?.note).toContain("These are the 2025/2026 fees")
    expect(quote?.note).toContain("subject to change")
  })

  it("quotes nothing where no period has been recorded", () => {
    expect(feeQuote(null)).toBeNull()
  })
})
