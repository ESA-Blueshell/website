import {describe, expect, it} from "vitest"
import {BulkFeeType, ContributionEmailKind, type MemberPeriodContribution} from "@/services/api"
import {contributionRow, owedIn} from "@/domains/contribution"

const period = (over: Partial<MemberPeriodContribution> = {}): MemberPeriodContribution => ({
  periodId: 3,
  startDate: "2026-09-01",
  endDate: "2027-08-31",
  feeType: BulkFeeType.FULL_YEAR_FEE,
  fee: 30,
  ...over,
})

describe("a contribution row", () => {
  it("names the years, the fee and the day the board recorded the payment", () => {
    expect(contributionRow(period({paidAt: "2026-10-04T09:12:00Z"}))).toEqual({
      years: "2026-2027",
      meta: "Full-year fee · € 30.00 · Paid 4 October 2026",
      state: "Paid",
      tone: "ok",
    })
  })

  it("names the latest ask of an unpaid one, and the day to pay by only where that ask set one", () => {
    const asked = period({lastEmailKind: ContributionEmailKind.REMINDER, lastEmailAt: "2027-02-12T10:00:00Z", dueDate: "2027-02-26"})
    expect(contributionRow(asked)).toEqual({
      years: "2026-2027",
      meta: "Full-year fee · € 30.00 · Last asked 12 February 2027 · Pay by 26 February 2027",
      state: "Not paid yet",
      tone: "warn",
    })
    expect(contributionRow({...asked, dueDate: null}).meta).toBe("Full-year fee · € 30.00 · Last asked 12 February 2027")
    expect(contributionRow(period()).meta).toBe("Full-year fee · € 30.00")
  })

  it("names the day a direct debit is taken", () => {
    const told = period({lastEmailKind: ContributionEmailKind.INCASSO_NOTIFICATION, lastEmailAt: "2026-10-12T10:00:00Z", dueDate: "2026-11-01"})

    expect(contributionRow(told).meta).toBe("Full-year fee · € 30.00 · Direct debit announced 12 October 2026 · Taken on 1 November 2026")
  })

  it("asks nothing of an honorary membership", () => {
    expect(contributionRow(period({feeType: null, fee: null}))).toEqual({
      years: "2026-2027",
      meta: "Honorary membership",
      state: "Nothing to pay",
      tone: "quiet",
    })
  })
})

describe("what is owed", () => {
  it("adds up the unpaid fees and counts them", () => {
    const rows = [period(), period({periodId: 2, fee: 15}), period({periodId: 1, paidAt: "2025-10-01T00:00:00Z"}), period({fee: null, feeType: null})]

    expect(owedIn(rows)).toEqual({count: 2, total: 45})
    expect(owedIn([])).toEqual({count: 0, total: 0})
  })
})
