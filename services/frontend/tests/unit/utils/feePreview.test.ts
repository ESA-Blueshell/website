import {describe, expect, it} from "vitest"
import {effectiveAmount} from "@/utils/feePreview"

const period = {fullYearFee: 20, halfYearFee: 10, alumniFee: 5}

describe("feePreview.effectiveAmount", () => {
  it("maps each fee type to its € amount from the period", () => {
    expect(effectiveAmount("FULL_YEAR_FEE", period)).toBe(20)
    expect(effectiveAmount("HALF_YEAR_FEE", period)).toBe(10)
    expect(effectiveAmount("ALUMNI_FEE", period)).toBe(5)
  })

  it("returns null when the fee type is missing", () => {
    expect(effectiveAmount(null, period)).toBeNull()
    expect(effectiveAmount(undefined, period)).toBeNull()
  })

  it("returns null when the period is missing", () => {
    expect(effectiveAmount("FULL_YEAR_FEE", null)).toBeNull()
  })
})
