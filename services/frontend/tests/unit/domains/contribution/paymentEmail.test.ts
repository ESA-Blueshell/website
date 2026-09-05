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
  forcedUserIds,
  isReCharged,
  isSwitched,
  kindFor,
  lastAskedOn,
  lastSentOfKind,
  paymentDateProblem,
  periodDateWindow,
  reapplyChoices,
  seedChoices,
  seedSendTo,
  summarise,
  switchedNote,
  toBulkRow,
  toBulkRows,
  willSend,
} from "@/domains/contribution"

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
      .toContain("Pays by direct debit")
    expect(switchedNote(row({defaultKind: ContributionEmailKind.REMINDER})))
      .toContain("No direct-debit mandate")
  })
})

describe("lastAskedOn", () => {
  // A member moved onto direct debit has been asked by transfer and never pre-notified.
  it("answers for the member, not for the email the row is set to", () => {
    const moved = row({
      userId: 2,
      defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION,
      lastRemindedOn: "2026-03-04",
      lastNotifiedOn: undefined,
    })

    expect(lastAskedOn(moved)).toBe("2026-03-04")
  })

  it("takes the later of the two asks", () => {
    expect(lastAskedOn(row({lastRemindedOn: "2026-03-04", lastNotifiedOn: "2026-05-01"})))
      .toBe("2026-05-01")
    expect(lastAskedOn(row({lastRemindedOn: "2026-05-01", lastNotifiedOn: "2026-03-04"})))
      .toBe("2026-05-01")
  })

  it("has no answer for a member nobody has asked", () => {
    expect(lastAskedOn(row({lastRemindedOn: undefined, lastNotifiedOn: undefined})))
      .toBeUndefined()
  })
})

describe("lastSentOfKind", () => {
  // What a resend would duplicate, which is the one thing the merged column cannot say.
  it("reads the date for the email the row is set to", () => {
    const moved = row({
      userId: 2,
      defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION,
      lastRemindedOn: "2026-03-04",
      lastNotifiedOn: undefined,
    })

    expect(lastSentOfKind(moved, {})).toBeUndefined()
    expect(lastSentOfKind(moved, {2: ContributionEmailKind.REMINDER})).toBe("2026-03-04")
  })
})

describe("the selection", () => {
  const rows = [
    row({userId: 1, disposition: "INCLUDED"}),
    row({userId: 2, disposition: "WARNING", reason: BulkRowReason.ALREADY_PAID}),
    row({userId: 3, disposition: "EXCLUDED", reason: BulkRowReason.HONORARY}),
  ]

  it("starts a warned member unticked and gives an unreachable one no box at all", () => {
    expect(seedSendTo(rows)).toEqual({1: true, 2: false})
  })

  it("sends to a row only while it is ticked", () => {
    expect(willSend(rows[0]!, {1: true})).toBe(true)
    expect(willSend(rows[0]!, {1: false})).toBe(false)
    expect(willSend(rows[1]!, {2: true})).toBe(true)
  })

  it("never sends to a member it cannot reach, whatever is ticked", () => {
    expect(willSend(rows[2]!, {3: true})).toBe(false)
  })

  it("names the warned rows the treasurer ticked back in", () => {
    expect(forcedUserIds(rows, {1: true, 2: true, 3: true})).toEqual([2])
    expect(forcedUserIds(rows, {1: true})).toEqual([])
  })
})

describe("isReCharged", () => {
  const priced = row({userId: 1, recommendedFeeType: BulkFeeType.FULL_YEAR_FEE})

  it("is false while the row is on the fee its membership works out to", () => {
    expect(isReCharged(priced, {})).toBe(false)
    expect(isReCharged(priced, {1: BulkFeeType.FULL_YEAR_FEE})).toBe(false)
  })

  it("is true once the treasurer picks another fee", () => {
    expect(isReCharged(priced, {1: BulkFeeType.ALUMNI_FEE})).toBe(true)
  })
})

describe("carrying choices onto a plan that was read again", () => {
  const rows = [
    row({userId: 1, disposition: "INCLUDED"}),
    row({userId: 2, disposition: "INCLUDED"}),
    row({userId: 3, disposition: "WARNING", reason: BulkRowReason.ALREADY_PAID}),
    row({userId: 4, disposition: "EXCLUDED", reason: BulkRowReason.HONORARY}),
  ]

  const made = {
    sendTo: {1: false, 2: true, 3: true},
    fees: {1: BulkFeeType.ALUMNI_FEE, 2: BulkFeeType.ALUMNI_FEE, 3: BulkFeeType.ALUMNI_FEE},
    kinds: {2: ContributionEmailKind.INCASSO_NOTIFICATION},
  }

  it("seeds a plan the way the api proposes it", () => {
    expect(seedChoices(rows).sendTo).toEqual({1: true, 2: true, 3: false})
    expect(seedChoices(rows).fees[1]).toBe(BulkFeeType.FULL_YEAR_FEE)
    expect(seedChoices(rows).kinds[1]).toBe(ContributionEmailKind.REMINDER)
  })

  it("puts back every choice the refusal did not name", () => {
    const next = reapplyChoices(rows, seedChoices(rows), made, [])

    expect(next.sendTo).toEqual({1: false, 2: true, 3: true})
    expect(next.fees[2]).toBe(BulkFeeType.ALUMNI_FEE)
    expect(next.kinds[2]).toBe(ContributionEmailKind.INCASSO_NOTIFICATION)
  })

  it("gives a member the refusal named the new plan's answer instead", () => {
    const next = reapplyChoices(rows, seedChoices(rows), made, [2])

    expect(next.sendTo[2]).toBe(true)
    expect(next.fees[2]).toBe(BulkFeeType.FULL_YEAR_FEE)
    expect(next.kinds[2]).toBe(ContributionEmailKind.REMINDER)
  })

  it("leaves a member the plan can no longer reach out of the selection", () => {
    const next = reapplyChoices(rows, seedChoices(rows), {...made, sendTo: {4: true}}, [])

    expect(next.sendTo[4]).toBeUndefined()
  })

  it("forgets a choice for somebody the new plan does not list", () => {
    const next = reapplyChoices([rows[0]!], seedChoices([rows[0]!]), made, [])

    expect(next.fees[2]).toBeUndefined()
    expect(next.sendTo[2]).toBeUndefined()
  })
})

describe("paymentDateProblem", () => {
  const period = {startDate: "2025-09-01", endDate: "2026-08-31"}
  const today = "2026-03-01"

  it("accepts a date inside the period", () => {
    expect(paymentDateProblem("2026-04-01", period, today)).toBeNull()
  })

  it("says nothing about a date nobody has entered yet", () => {
    expect(paymentDateProblem("", period, today)).toBeNull()
  })

  it("refuses today and anything before it", () => {
    expect(paymentDateProblem(today, period, today)).toBe("The date must be after today.")
    expect(paymentDateProblem("2026-02-28", period, today)).toBe("The date must be after today.")
  })

  it("refuses a date before the period starts", () => {
    expect(paymentDateProblem("2025-08-31", period, "2025-01-01"))
      .toBe("The date must fall between 01/09/2025 and 30/11/2026.")
  })

  // Chasing the last unpaid members near the end of a period has to stay possible.
  it("allows three months past the end of the period, and refuses the day after", () => {
    expect(paymentDateProblem("2026-11-30", period, today)).toBeNull()
    expect(paymentDateProblem("2026-12-01", period, today)).not.toBeNull()
  })

  it("has nothing to say without a period", () => {
    expect(paymentDateProblem("2030-01-01", null, today)).toBeNull()
  })

  it("states the window the api will accept", () => {
    expect(periodDateWindow(period)).toEqual({from: "2025-09-01", until: "2026-11-30"})
    expect(periodDateWindow(null)).toBeNull()
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

    expect(countByKind(rows, {}, {1: true, 2: true, 4: true}))
      .toEqual({REMINDER: 1, INCASSO_NOTIFICATION: 1})
    expect(countByKind(rows, {}, {1: true, 2: true, 3: true}))
      .toEqual({REMINDER: 2, INCASSO_NOTIFICATION: 1})
  })

  it("follows a switched row to the other count", () => {
    const rows = [row({userId: 2, defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION})]

    expect(countByKind(rows, {2: ContributionEmailKind.REMINDER}, {2: true}))
      .toEqual({REMINDER: 1, INCASSO_NOTIFICATION: 0})
  })
})

describe("summarise", () => {
  const rows = [
    row({userId: 1, lastRemindedOn: "2026-01-05"}),
    row({userId: 2, defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION}),
    row({userId: 3, disposition: "WARNING", reason: BulkRowReason.ALREADY_PAID}),
    row({userId: 4, disposition: "EXCLUDED", reason: BulkRowReason.HONORARY}),
  ]

  it("counts what the send would do, before it does it", () => {
    expect(summarise(rows, {}, {}, seedSendTo(rows))).toEqual({
      reminders: 1,
      incassoNotifications: 1,
      total: 2,
      notEmailed: 2,
      forced: [],
      switched: [],
      reCharged: [],
      alreadySent: [{userId: 1, name: "Ann Regular", note: "last sent 05/01/2026"}],
    })
  })

  it("counts each override the operator made", () => {
    const summary = summarise(
      rows,
      {2: ContributionEmailKind.REMINDER},
      {1: BulkFeeType.ALUMNI_FEE},
      {1: true, 2: true, 3: true},
    )

    expect(summary).toMatchObject({
      reminders: 3,
      incassoNotifications: 0,
      total: 3,
      notEmailed: 1,
    })
    // Named, not just counted: the confirmation shows who, and what was overruled.
    expect(summary.forced).toEqual([
      {userId: 3, name: "Ann Regular", note: "Already paid this contribution"},
    ])
    expect(summary.switched.map((m) => m.userId)).toEqual([2])
    expect(summary.reCharged.map((m) => m.userId)).toEqual([1])
  })

  // A hard exclusion is never a recipient, so it never reaches any of the override counts.
  it("never counts a hard-excluded row as sent, whatever is ticked", () => {
    expect(summarise(rows, {}, {}, {1: true, 2: true, 3: true, 4: true}))
      .toMatchObject({total: 3, notEmailed: 1})
  })

  it("drops a member the treasurer unticked", () => {
    expect(summarise(rows, {}, {}, {1: true})).toMatchObject({total: 1, notEmailed: 3})
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
