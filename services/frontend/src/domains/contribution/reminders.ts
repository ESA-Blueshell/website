import {type BulkContributionEmailRowResponse, BulkRowDisposition} from "@/services/api"
import {reasonLabel} from "@/utils/bulkDisposition"

/**
 * How far past a period's end a due date may still fall. Mirrors `MONTHS_PAST_PERIOD_END` in the
 * api's BulkContributionEmailUseCases; change one, change the other.
 */
export const PERIOD_OVERHANG_MONTHS = 3

/** A day as yyyy-mm-dd in the reader's own time zone, `offset` days from today. */
export function localDay(offset = 0, from = new Date()): string {
  const day = new Date(from.getFullYear(), from.getMonth(), from.getDate() + offset)
  return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, "0")}-${String(day.getDate()).padStart(2, "0")}`
}

/** Why a due date would be refused, or null where the api takes it. */
export function dueDateProblem(date: string, period: {startDate: string; endDate: string}, today = localDay()): string | null {
  if (!date) return "Fill in the date to pay by."
  if (date <= today) return "The date to pay by has to be after today."
  const [year, month, day] = period.endDate.split("-").map(Number) as [number, number, number]
  // Clamped to the month's last day, as the api's plusMonths is: 31 Aug goes to 30 Nov.
  const target = new Date(year, month - 1 + PERIOD_OVERHANG_MONTHS, 1)
  const lastDay = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate()
  const latest = localDay(0, new Date(target.getFullYear(), target.getMonth(), Math.min(day, lastDay)))
  if (date < period.startDate || date > latest) return `The date to pay by has to fall between ${period.startDate} and ${latest}.`
  return null
}

/** One member on the payment reminder task, as its steps read them. */
export interface ReminderRow {
  userId: number
  name: string
  row: BulkContributionEmailRowResponse
  /** Why this member gets no reminder, or null for somebody who can be sent one. */
  leftOut: string | null
  /** When a reminder for this period last went to them, flagged before sending again. */
  remindedBefore: string | null
  /** A new member's first reminder, named apart on this page; the email itself is the same reminder. */
  first: boolean
}

/** The preview's rows as the reminder task reads them; a member on incasso is a warning, ticked on purpose. */
export function reminderRows(rows: BulkContributionEmailRowResponse[], periodStart: string | null): ReminderRow[] {
  return rows.map((row) => ({
    userId: row.userId,
    name: row.name,
    row,
    leftOut: row.disposition === BulkRowDisposition.EXCLUDED ? reasonLabel(row.reason) || "Not written to"
      : row.feeType == null ? "Owes nothing" : null,
    remindedBefore: row.lastRemindedOn ?? null,
    first: periodStart != null && row.lastRemindedOn == null && row.memberSince != null && row.memberSince >= periodStart,
  }))
}

/** What the email a row gets is called. */
export const reminderName = (row: ReminderRow): string => (row.first ? "First payment email" : "Payment reminder")
