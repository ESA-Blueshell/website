import {describe, expect, it} from "vitest"
import {BulkFeeType} from "@/utils/bulkRow"
import {effectiveAmount, feeTypeItems, feeTypeLabels} from "@/utils/feePreview"
import {period} from "../helpers/bulkFixtures"

describe("effectiveAmount", () => {
  it("prices each fee type off the selected period", () => {
    const selected = period({fullYearFee: 42, halfYearFee: 21, alumniFee: 7})

    expect(effectiveAmount(BulkFeeType.FULL_YEAR_FEE, selected)).toBe(42)
    expect(effectiveAmount(BulkFeeType.HALF_YEAR_FEE, selected)).toBe(21)
    expect(effectiveAmount(BulkFeeType.ALUMNI_FEE, selected)).toBe(7)
  })

  it("prices the fee type it is handed and never re-decides it against the cutoff", () => {
    // The api decides which type applies from the membership's start; the treasurer may then
    // move a row onto the other one, and the amount has to follow that choice rather than
    // the date. A member who joined before the cutoff, priced half-year, pays the half year.
    const selected = period({halfYearCutoffDate: "2025-07-01", fullYearFee: 42, halfYearFee: 21})

    expect(effectiveAmount(BulkFeeType.HALF_YEAR_FEE, selected)).toBe(21)
    expect(effectiveAmount(BulkFeeType.FULL_YEAR_FEE, selected)).toBe(42)
  })

  it("has no amount to show before a type or a period is chosen", () => {
    expect(effectiveAmount(null, period())).toBeNull()
    expect(effectiveAmount(undefined, period())).toBeNull()
    expect(effectiveAmount(BulkFeeType.FULL_YEAR_FEE, null)).toBeNull()
    expect(effectiveAmount(BulkFeeType.FULL_YEAR_FEE, undefined)).toBeNull()
  })

  it("shows no amount for a fee type this build does not know", () => {
    // A period served by a newer api can name a type the bundled enum has no fee for.
    expect(effectiveAmount("SUMMER_FEE" as BulkFeeType, period())).toBeNull()
  })

  it("prices a period whose fee is zero as free rather than as unknown", () => {
    expect(effectiveAmount(BulkFeeType.ALUMNI_FEE, period({alumniFee: 0}))).toBe(0)
  })
})

describe("feeTypeItems", () => {
  it("offers every fee type the api defines", () => {
    expect(feeTypeItems.map((item) => item.value)).toEqual(Object.values(BulkFeeType))
    expect(feeTypeItems).toContainEqual({title: "Half-year fee", value: BulkFeeType.HALF_YEAR_FEE})
  })

  it("gives every option a label, so a type added on the api cannot reach the picker blank", () => {
    expect(feeTypeItems.every((item) => typeof item.title === "string" && item.title.length > 0)).toBe(true)
    expect(Object.keys(feeTypeLabels).sort()).toEqual([...Object.values(BulkFeeType)].sort())
  })
})
