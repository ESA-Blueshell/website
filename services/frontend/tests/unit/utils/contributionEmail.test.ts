import {describe, expect, it} from "vitest"
import {
  BulkFeeType,
  BulkRowDisposition,
  BulkRowReason,
  ContributionEmailKind,
  MemberType,
} from "@/services/api"
import type {BulkContributionEmailRowResponse} from "@/services/api"
import type {BulkRow} from "@/utils/bulkRow"
import {
  changedFeeTypes,
  changedKinds,
  countByKind,
  isSwitched,
  kindFor,
  lastSentLabel,
  lastSentOn,
  switchedNote,
  toBulkRow,
  toBulkRows,
  willSend,
} from "@/utils/contributionEmail"

function apiRow(
  overrides: Partial<BulkContributionEmailRowResponse> = {},
): BulkContributionEmailRowResponse {
  return {
    userId: 1,
    name: "Ann Regular",
    memberType: MemberType.REGULAR,
    memberSince: "2025-09-01",
    disposition: BulkRowDisposition.INCLUDED,
    reason: null,
    defaultKind: ContributionEmailKind.REMINDER,
    feeType: BulkFeeType.FULL_YEAR_FEE,
    amount: 45,
    lastRemindedOn: null,
    lastNotifiedOn: null,
    ...overrides,
  }
}

function row(overrides: Partial<BulkRow> = {}): BulkRow {
  return {...toBulkRow(apiRow()), ...overrides}
}

describe("toBulkRow", () => {
  it("carries the member, their disposition, their email and what they owe", () => {
    const mapped = toBulkRow(
      apiRow({
        userId: 7,
        amount: 25,
        feeType: BulkFeeType.HALF_YEAR_FEE,
        defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION,
      }),
    )

    expect(mapped).toMatchObject({
      userId: 7,
      name: "Ann Regular",
      memberType: MemberType.REGULAR,
      memberSince: "2025-09-01",
      disposition: BulkRowDisposition.INCLUDED,
      defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION,
      amount: 25,
      recommendedFeeType: BulkFeeType.HALF_YEAR_FEE,
    })
  })

  // The client models absence as null; the row model uses undefined.
  it("turns the api's nulls into absent fields", () => {
    const mapped = toBulkRow(apiRow({reason: null, feeType: null, amount: null, lastRemindedOn: null}))

    expect(mapped.reason).toBeUndefined()
    expect(mapped.recommendedFeeType).toBeUndefined()
    expect(mapped.lastRemindedOn).toBeUndefined()
    expect(mapped.amount).toBeNull()
  })

  it("maps every row it is given", () => {
    expect(toBulkRows([apiRow({userId: 1}), apiRow({userId: 2})]).map((r) => r.userId)).toEqual([1, 2])
  })
})

describe("kindFor and isSwitched", () => {
  it("uses the flag's choice until the treasurer picks another", () => {
    const debit = row({userId: 2, defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION})

    expect(kindFor(debit, {})).toBe(ContributionEmailKind.INCASSO_NOTIFICATION)
    expect(kindFor(debit, {2: ContributionEmailKind.REMINDER})).toBe(ContributionEmailKind.REMINDER)
  })

  it("flags a row only once it differs from the flag's choice", () => {
    const debit = row({userId: 2, defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION})

    expect(isSwitched(debit, {2: ContributionEmailKind.INCASSO_NOTIFICATION})).toBe(false)
    expect(isSwitched(debit, {2: ContributionEmailKind.REMINDER})).toBe(true)
  })

  it("names the flag the switch contradicts", () => {
    expect(switchedNote(row({defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION})))
      .toBe("Pays by direct debit")
    expect(switchedNote(row({defaultKind: ContributionEmailKind.REMINDER})))
      .toBe("Not marked for direct debit")
  })
})

describe("lastSentOn", () => {
  // A member moved onto direct debit has been asked by transfer and never pre-notified.
  it("reads the date for the email the row is set to", () => {
    const moved = row({
      userId: 2,
      defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION,
      lastRemindedOn: "2026-03-04",
      lastNotifiedOn: undefined,
    })

    expect(lastSentOn(moved, {})).toBeUndefined()
    expect(lastSentOn(moved, {2: ContributionEmailKind.REMINDER})).toBe("2026-03-04")
  })

  it("reads a date the way every other bulk dialog does, and says never for none", () => {
    expect(lastSentLabel("2026-03-04")).toBe("04/03/2026")
    expect(lastSentLabel(undefined)).toBe("Never")
  })
})

describe("willSend", () => {
  it("includes an included row whatever the overrides say", () => {
    expect(willSend(row({disposition: "INCLUDED"}), {})).toBe(true)
  })

  it("leaves a warned row out until it is ticked back in", () => {
    const warned = row({userId: 2, disposition: "WARNING", reason: BulkRowReason.ALREADY_PAID})

    expect(willSend(warned, {})).toBe(false)
    expect(willSend(warned, {2: true})).toBe(true)
  })

  it("never includes a hard-excluded row", () => {
    const excluded = row({userId: 3, disposition: "EXCLUDED", reason: BulkRowReason.HONORARY})

    expect(willSend(excluded, {3: true})).toBe(false)
  })
})

describe("countByKind", () => {
  it("counts only the rows about to be written to, by the email they are set to", () => {
    const rows = [
      row({userId: 1, defaultKind: ContributionEmailKind.REMINDER}),
      row({userId: 2, defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION}),
      row({
        userId: 3,
        defaultKind: ContributionEmailKind.REMINDER,
        disposition: "WARNING",
        reason: BulkRowReason.ALREADY_PAID,
      }),
      row({userId: 4, defaultKind: ContributionEmailKind.REMINDER, disposition: "EXCLUDED"}),
    ]

    expect(countByKind(rows, {}, {})).toEqual({REMINDER: 1, INCASSO_NOTIFICATION: 1})
    expect(countByKind(rows, {}, {3: true})).toEqual({REMINDER: 2, INCASSO_NOTIFICATION: 1})
  })

  it("follows a switched row to the other count", () => {
    const rows = [row({userId: 2, defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION})]

    expect(countByKind(rows, {2: ContributionEmailKind.REMINDER}, {}))
      .toEqual({REMINDER: 1, INCASSO_NOTIFICATION: 0})
  })
})

describe("changedKinds and changedFeeTypes", () => {
  it("names only the rows moved off the email their flag chose", () => {
    const rows = [
      row({userId: 1, defaultKind: ContributionEmailKind.REMINDER}),
      row({userId: 2, defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION}),
    ]

    const changed = changedKinds(rows, {
      1: ContributionEmailKind.REMINDER,
      2: ContributionEmailKind.REMINDER,
    })

    expect(changed).toEqual({"2": ContributionEmailKind.REMINDER})
  })

  // Stating every row's type would claim a choice where none was made.
  it("names only the rows whose fee type the treasurer changed", () => {
    const rows = [
      row({userId: 1, recommendedFeeType: BulkFeeType.FULL_YEAR_FEE}),
      row({userId: 2, recommendedFeeType: BulkFeeType.HALF_YEAR_FEE}),
    ]

    const changed = changedFeeTypes(rows, {
      1: BulkFeeType.FULL_YEAR_FEE,
      2: BulkFeeType.ALUMNI_FEE,
    })

    expect(changed).toEqual({"2": BulkFeeType.ALUMNI_FEE})
  })

  it("names nothing when no row was touched", () => {
    expect(changedFeeTypes([row({recommendedFeeType: BulkFeeType.FULL_YEAR_FEE})], {
      1: BulkFeeType.FULL_YEAR_FEE,
    })).toEqual({})
  })
})
