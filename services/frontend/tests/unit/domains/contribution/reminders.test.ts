import {describe, expect, it} from "vitest"
import {BulkFeeType, BulkRowDisposition, BulkRowReason, MemberType} from "@/services/api"
import {dueDateProblem, localDay, reminderName, reminderRows} from "@/domains/contribution"

const row = (userId: number, fields: Record<string, unknown> = {}) => ({
  userId, name: `Member ${userId}`, memberType: MemberType.REGULAR, memberSince: "2020-01-01",
  disposition: BulkRowDisposition.INCLUDED, reason: null,
  feeType: BulkFeeType.FULL_YEAR_FEE, amount: 30, lastRemindedOn: null, lastNotifiedOn: null, ...fields,
})

describe("the reminder rows", () => {
  it("keeps a member on incasso as a warning, and leaves out those the api will not write to, each with the reason", () => {
    const [incasso, honorary, noEmail, odd] = reminderRows([
      row(1, {disposition: BulkRowDisposition.WARNING, reason: BulkRowReason.PAYS_BY_DIRECT_DEBIT}),
      row(2, {disposition: BulkRowDisposition.EXCLUDED, reason: BulkRowReason.HONORARY}),
      row(3, {disposition: BulkRowDisposition.EXCLUDED, reason: BulkRowReason.NO_EMAIL}),
      row(4, {disposition: BulkRowDisposition.EXCLUDED, reason: null}),
    ], "2025-09-01")

    expect(incasso!.leftOut).toBeNull()
    expect(honorary!.leftOut).toBeTruthy()
    expect(noEmail!.leftOut).toBeTruthy()
    expect(odd!.leftOut).toBe("Not written to")
  })

  it("calls a new member's first reminder the First payment email, and flags a repeat", () => {
    const [fresh, veteran, repeat] = reminderRows([
      row(1, {memberSince: "2025-10-01"}),
      row(2),
      row(3, {memberSince: "2025-10-01", lastRemindedOn: "2025-10-05"}),
    ], "2025-09-01")

    expect(reminderName(fresh!)).toBe("First payment email")
    expect(reminderName(veteran!)).toBe("Payment reminder")
    expect(reminderName(repeat!)).toBe("Payment reminder")
    expect(repeat!.remindedBefore).toBe("2025-10-05")
    expect(fresh!.leftOut).toBeNull()
  })
  it("leaves out a member without a fee, and names nobody's first reminder where the period is unknown", () => {
    const [feeless, fresh] = reminderRows([row(1, {feeType: null}), row(2, {memberSince: "2025-10-01"})], null)

    expect(feeless!.leftOut).toBe("Owes nothing")
    expect(reminderName(fresh!)).toBe("Payment reminder")
  })
})

describe("the date to pay by", () => {
  const period = {startDate: "2025-09-01", endDate: "2026-08-31"}

  it("takes a day after today inside the period or up to three months past its end, as the api does", () => {
    expect(dueDateProblem("", period, "2025-10-01")).toBe("Fill in the date to pay by.")
    expect(dueDateProblem("2025-10-01", period, "2025-10-01")).toBe("The date to pay by has to be after today.")
    expect(dueDateProblem("2025-10-02", period, "2025-10-01")).toBeNull()
    expect(dueDateProblem("2026-11-30", period, "2025-10-01")).toBeNull()
    expect(dueDateProblem("2026-12-01", period, "2025-10-01")).toBe("The date to pay by has to fall between 2025-09-01 and 2026-11-30.")
    expect(dueDateProblem("2025-08-31", period, "2025-08-01")).toContain("has to fall between")
  })

  it("reads a day in the reader's own time zone", () => {
    expect(localDay(0, new Date(2026, 0, 31, 23, 30))).toBe("2026-01-31")
    expect(localDay(1, new Date(2026, 0, 31, 23, 30))).toBe("2026-02-01")
    expect(localDay()).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })
})
