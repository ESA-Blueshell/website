import {DateTime} from "luxon"
import {ContributionEmailKind, type MemberPeriodContribution} from "@/services/api"
import {feeTypeLabels} from "@/utils/feePreview"

export interface ContributionRow {
  years: string
  meta: string
  state: "Paid" | "Not paid yet" | "Nothing to pay"
  tone: "ok" | "warn" | "quiet"
}

const day = (iso: string): string => DateTime.fromISO(iso).toFormat("d LLLL yyyy")
export const euros = (amount: number): string => `€ ${amount.toFixed(2)}`

/** What a member reads about one period: an honorary membership has no fee, so nothing to pay. */
export function contributionRow(period: MemberPeriodContribution): ContributionRow {
  const years = `${period.startDate.slice(0, 4)}-${period.endDate.slice(0, 4)}`
  if (!period.feeType || period.fee == null) return {years, meta: "Honorary membership", state: "Nothing to pay", tone: "quiet"}
  const fee = [feeTypeLabels[period.feeType], euros(period.fee)]
  if (period.paidAt) return {years, meta: [...fee, `Paid ${day(period.paidAt)}`].join(" · "), state: "Paid", tone: "ok"}
  return {years, meta: [...fee, ...lastAsk(period)].join(" · "), state: "Not paid yet", tone: "warn"}
}

function lastAsk(period: MemberPeriodContribution): string[] {
  if (!period.lastEmailAt) return []
  const debit = period.lastEmailKind === ContributionEmailKind.INCASSO_NOTIFICATION
  const asked = `${debit ? "Direct debit announced" : "Last asked"} ${day(period.lastEmailAt)}`
  if (!period.dueDate) return [asked]
  return [asked, `${debit ? "Taken from your account on" : "Pay by"} ${day(period.dueDate)}`]
}

/** The unpaid contributions among these periods, and what they add up to. */
export function owedIn(periods: MemberPeriodContribution[]): {count: number, total: number} {
  const unpaid = periods.filter(one => !one.paidAt && one.feeType && one.fee != null)
  return {count: unpaid.length, total: unpaid.reduce((sum, one) => sum + (one.fee ?? 0), 0)}
}
