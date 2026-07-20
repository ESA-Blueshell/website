import {describe, expect, it} from "vitest"
import {autoFeeType, effectiveAmount} from "@/utils/feePreview"
import {MemberType} from "@/services/api"

const period = {fullYearFee: 20, halfYearFee: 10, alumniFee: 5}

function membership(type: MemberType, startDate: string) {
  return {type, startDate}
}

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

describe("feePreview.autoFeeType (locked rule)", () => {
  it("excludes honorary members (returns null)", () => {
    expect(autoFeeType(membership(MemberType.REGULAR, "2024-01-01"), true, "2025-06-01")).toBeNull()
  })

  it("returns null when there is no membership", () => {
    expect(autoFeeType(null, false, "2025-06-01")).toBeNull()
  })

  it("maps ALUMNI membership to ALUMNI_FEE regardless of cutoff", () => {
    expect(autoFeeType(membership(MemberType.ALUMNI, "2025-12-01"), false, "2025-06-01")).toBe("ALUMNI_FEE")
  })

  it("resolves startDate < cutoff to FULL_YEAR_FEE", () => {
    expect(autoFeeType(membership(MemberType.REGULAR, "2025-01-01"), false, "2025-06-01")).toBe("FULL_YEAR_FEE")
  })

  it("resolves the boundary startDate == cutoff to FULL_YEAR_FEE", () => {
    expect(autoFeeType(membership(MemberType.REGULAR, "2025-06-01"), false, "2025-06-01")).toBe("FULL_YEAR_FEE")
  })

  it("resolves startDate > cutoff to HALF_YEAR_FEE", () => {
    expect(autoFeeType(membership(MemberType.REGULAR, "2025-06-02"), false, "2025-06-01")).toBe("HALF_YEAR_FEE")
  })

  it("defaults to FULL_YEAR_FEE when no cutoff is given", () => {
    expect(autoFeeType(membership(MemberType.REGULAR, "2025-06-02"), false, "")).toBe("FULL_YEAR_FEE")
  })
})
