import {describe, expect, it} from "vitest"
import {BulkFeeType, BulkRowDisposition, BulkRowReason, FeeCycleGroup, MemberType} from "@/services/api"
import type {FeeCycleRowResponse} from "@/services/api"
import type {BulkRow} from "@/utils/bulkRow"
import {
  changedFeeTypes,
  countByGroup,
  feeCycleGroupLabel,
  lastAskedLabel,
  toBulkRow,
  toBulkRows,
} from "@/utils/feeCycle"

function apiRow(overrides: Partial<FeeCycleRowResponse> = {}): FeeCycleRowResponse {
  return {
    userId: 1,
    name: "Ann Regular",
    memberType: MemberType.REGULAR,
    memberSince: "2025-09-01",
    group: FeeCycleGroup.TRANSFER,
    disposition: BulkRowDisposition.INCLUDED,
    reason: null,
    feeType: BulkFeeType.FULL_YEAR_FEE,
    amount: 45,
    lastAskedOn: null,
    ...overrides,
  }
}

function row(overrides: Partial<BulkRow> = {}): BulkRow {
  return {...toBulkRow(apiRow()), ...overrides}
}

describe("toBulkRow", () => {
  it("carries the member, the side of the partition and what they owe", () => {
    const mapped = toBulkRow(apiRow({userId: 7, group: FeeCycleGroup.DIRECT_DEBIT, amount: 25}))

    expect(mapped).toMatchObject({
      userId: 7,
      name: "Ann Regular",
      group: FeeCycleGroup.DIRECT_DEBIT,
      disposition: BulkRowDisposition.INCLUDED,
      memberSince: "2025-09-01",
      amount: 25,
      recommendedFeeType: BulkFeeType.FULL_YEAR_FEE,
    })
  })

  it("carries the reason a row is not written to", () => {
    const mapped = toBulkRow(
      apiRow({disposition: BulkRowDisposition.EXCLUDED, reason: BulkRowReason.HONORARY, feeType: null, amount: null}),
    )

    expect(mapped.disposition).toBe(BulkRowDisposition.EXCLUDED)
    expect(mapped.reason).toBe(BulkRowReason.HONORARY)
    expect(mapped.recommendedFeeType).toBeUndefined()
  })

  // The api answers with null; the row shape uses undefined, and the scaffold reads that.
  it("turns the api's nulls into absent fields", () => {
    const mapped = toBulkRow(apiRow({memberSince: null, lastAskedOn: null, reason: null}))

    expect(mapped.memberSince).toBeUndefined()
    expect(mapped.lastAskedOn).toBeUndefined()
    expect(mapped.reason).toBeUndefined()
  })

  it("reads the last-asked date onto the row", () => {
    expect(toBulkRow(apiRow({lastAskedOn: "2026-03-04"})).lastSentOn).toBe("2026-03-04")
  })

  it("maps a whole cycle in order", () => {
    const mapped = toBulkRows([apiRow({userId: 1}), apiRow({userId: 2})])
    expect(mapped.map((r) => r.userId)).toEqual([1, 2])
  })
})

describe("countByGroup", () => {
  it("counts each side of the partition separately", () => {
    const counts = countByGroup([
      row({userId: 1, group: FeeCycleGroup.DIRECT_DEBIT}),
      row({userId: 2, group: FeeCycleGroup.DIRECT_DEBIT}),
      row({userId: 3, group: FeeCycleGroup.TRANSFER}),
    ])

    expect(counts).toEqual({DIRECT_DEBIT: 2, TRANSFER: 1})
  })

  it("counts an empty cycle as zero on both sides", () => {
    expect(countByGroup([])).toEqual({DIRECT_DEBIT: 0, TRANSFER: 0})
  })
})

describe("feeCycleGroupLabel", () => {
  it("names each side in the treasurer's words", () => {
    expect(feeCycleGroupLabel(FeeCycleGroup.DIRECT_DEBIT)).toBe("Direct debit")
    expect(feeCycleGroupLabel(FeeCycleGroup.TRANSFER)).toBe("Transfer")
  })

  it("has a dash for a row with no side", () => {
    expect(feeCycleGroupLabel(undefined)).toBe("—")
  })
})

describe("lastAskedLabel", () => {
  it("formats a date the way the rest of the table does", () => {
    expect(lastAskedLabel("2026-03-04")).toBe("04/03/2026")
  })

  // The treasurer is scanning for who has already been asked, so never has to read as loudly
  // as a date.
  it("reads never rather than blank when nobody has asked yet", () => {
    expect(lastAskedLabel(undefined)).toBe("Never")
    expect(lastAskedLabel("not-a-date")).toBe("Never")
  })
})

describe("changedFeeTypes", () => {
  it("sends only the types the treasurer actually changed", () => {
    const rows = [
      row({userId: 1, recommendedFeeType: BulkFeeType.FULL_YEAR_FEE}),
      row({userId: 2, recommendedFeeType: BulkFeeType.FULL_YEAR_FEE}),
    ]
    const selections = {
      1: BulkFeeType.FULL_YEAR_FEE,
      2: BulkFeeType.HALF_YEAR_FEE,
    }

    expect(changedFeeTypes(rows, selections)).toEqual({"2": BulkFeeType.HALF_YEAR_FEE})
  })

  it("sends nothing when nothing was changed", () => {
    const rows = [row({userId: 1, recommendedFeeType: BulkFeeType.ALUMNI_FEE})]
    expect(changedFeeTypes(rows, {1: BulkFeeType.ALUMNI_FEE})).toEqual({})
  })

  /**
   * The api refuses a type naming somebody it does not write to, so a row with no selection
   * — an excluded one — must not appear in the request at all.
   */
  it("leaves out a row that has no selection", () => {
    const rows = [row({userId: 9, disposition: BulkRowDisposition.EXCLUDED, recommendedFeeType: undefined})]
    expect(changedFeeTypes(rows, {})).toEqual({})
  })
})
