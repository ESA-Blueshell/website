import {describe, expect, it} from "vitest"
import {BulkRowDisposition, BulkRowReason} from "@/services/api"
import {
  dispositionColor,
  dispositionLabel,
  effectiveDisposition,
  formatBulkDate,
  reasonLabel,
  rowColorClass,
} from "@/utils/bulkDisposition"

describe("effectiveDisposition", () => {
  it("counts a warned row the operator has re-included as one that will be acted on", () => {
    const row = {userId: 7, disposition: BulkRowDisposition.WARNING}

    expect(effectiveDisposition(row, {7: true})).toBe("INCLUDED")
  })

  it("leaves a warned row warned until the operator says otherwise", () => {
    const row = {userId: 7, disposition: BulkRowDisposition.WARNING}

    expect(effectiveDisposition(row, {})).toBe("WARNING")
    expect(effectiveDisposition(row, {7: false})).toBe("WARNING")
  })

  it("reads the override for this member, not for whoever else was re-included", () => {
    const row = {userId: 7, disposition: BulkRowDisposition.WARNING}

    expect(effectiveDisposition(row, {8: true})).toBe("WARNING")
  })

  it("keeps an excluded or skipped row out, whatever the overrides say", () => {
    // Only a warning is the operator's to overrule: the other two are the api's refusal.
    expect(effectiveDisposition({userId: 7, disposition: BulkRowDisposition.EXCLUDED}, {7: true}))
      .toBe("EXCLUDED")
    expect(effectiveDisposition({userId: 7, disposition: BulkRowDisposition.SKIPPED}, {7: true}))
      .toBe("SKIPPED")
  })
})

describe("rowColorClass", () => {
  it("marks the three dispositions that need the eye, and leaves the ordinary row plain", () => {
    expect(rowColorClass(BulkRowDisposition.EXCLUDED)).toBe("bulk-row--excluded")
    expect(rowColorClass(BulkRowDisposition.WARNING)).toBe("bulk-row--warning")
    expect(rowColorClass(BulkRowDisposition.SKIPPED)).toBe("bulk-row--skipped")
    expect(rowColorClass(BulkRowDisposition.INCLUDED)).toBe("")
  })
})

describe("dispositionLabel", () => {
  it("says every disposition in words", () => {
    expect(Object.values(BulkRowDisposition).map(dispositionLabel))
      .toEqual(["Included", "Skipped", "Excluded", "Warning"])
  })

  it("shows a disposition it has not been taught as the code itself, so nothing reads as blank", () => {
    expect(dispositionLabel("QUARANTINED" as BulkRowDisposition)).toBe("QUARANTINED")
  })
})

describe("dispositionColor", () => {
  it("colours the row by how much attention it wants", () => {
    expect(dispositionColor(BulkRowDisposition.EXCLUDED)).toBe("error")
    expect(dispositionColor(BulkRowDisposition.WARNING)).toBe("warning")
    expect(dispositionColor(BulkRowDisposition.SKIPPED)).toBe("grey")
    expect(dispositionColor(BulkRowDisposition.INCLUDED)).toBe("success")
  })
})

describe("reasonLabel", () => {
  it("says why a row was set aside in the operator's words rather than the api's code", () => {
    expect(reasonLabel(BulkRowReason.INCASSO_MISMATCH)).toBe("No direct-debit mandate")
    expect(reasonLabel(BulkRowReason.NO_EMAIL)).toBe("No email address on file")
  })

  it("has a sentence for every reason the api can send", () => {
    // The map is written by hand against the generated enum, so a reason added backend-side
    // would otherwise reach the operator as a shouted code.
    const unlabelled = Object.values(BulkRowReason)
      .filter(reason => reasonLabel(reason).includes("_"))

    expect(unlabelled).toEqual([])
  })

  it("softens a reason it has not been taught rather than showing nothing", () => {
    expect(reasonLabel("SOME_NEW_REASON" as BulkRowReason)).toBe("SOME NEW REASON")
  })

  it("says nothing at all where there is no reason, since the column is then empty", () => {
    expect(reasonLabel(null)).toBe("")
    expect(reasonLabel(undefined)).toBe("")
  })
})

describe("formatBulkDate", () => {
  it("shows a date day-first, the way the rest of the manager does", () => {
    expect(formatBulkDate("2025-09-01")).toBe("01/09/2025")
    expect(formatBulkDate("2025-09-01T14:30:00")).toBe("01/09/2025")
  })

  it("shows a dash where there is no date, or where what arrived is not one", () => {
    expect(formatBulkDate(null)).toBe("—")
    expect(formatBulkDate(undefined)).toBe("—")
    expect(formatBulkDate("")).toBe("—")
    expect(formatBulkDate("not a date")).toBe("—")
  })
})
