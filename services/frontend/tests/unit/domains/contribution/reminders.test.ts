import {describe, expect, it} from "vitest"
import {BulkRowDisposition, BulkRowReason, ContributionEmailKind, MemberType} from "@/services/api"
import {reminderName, reminderRows} from "@/domains/contribution"

const row = (userId: number, fields: Record<string, unknown> = {}) => ({
  userId, name: `Member ${userId}`, memberType: MemberType.REGULAR, memberSince: "2020-01-01",
  disposition: BulkRowDisposition.INCLUDED, reason: null, defaultKind: ContributionEmailKind.REMINDER,
  feeType: null, amount: null, lastRemindedOn: null, lastNotifiedOn: null, ...fields,
})

describe("the reminder rows", () => {
  it("leaves out members on incasso and those the api will not write to, each with the reason", () => {
    const [incasso, honorary, noEmail, odd] = reminderRows([
      row(1, {defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION}),
      row(2, {disposition: BulkRowDisposition.EXCLUDED, reason: BulkRowReason.HONORARY}),
      row(3, {disposition: BulkRowDisposition.EXCLUDED, reason: BulkRowReason.NO_EMAIL}),
      row(4, {disposition: BulkRowDisposition.EXCLUDED, reason: null}),
    ], "2025-09-01")

    expect(incasso!.leftOut).toBe("Pays by incasso")
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
})
