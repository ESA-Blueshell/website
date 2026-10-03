import {type BulkContributionEmailRowResponse, BulkRowDisposition, ContributionEmailKind} from "@/services/api"
import {reasonLabel} from "@/utils/bulkDisposition"

/** One member on the payment reminder task, as its steps read them. */
export interface ReminderRow {
  userId: number
  name: string
  row: BulkContributionEmailRowResponse
  /** Why this member gets no reminder, or null for somebody who can be sent one. */
  leftOut: string | null
  /** When a reminder for this period last went to them, flagged before sending again. */
  remindedBefore: string | null
  /** A new member's first reminder, which is called the First payment email everywhere. */
  first: boolean
}

/**
 * The preview's rows as the reminder task reads them. Members on incasso get the incasso
 * notification instead, so they are left out here with that reason; the rest keep the api's own.
 */
export function reminderRows(rows: BulkContributionEmailRowResponse[], periodStart: string): ReminderRow[] {
  return rows.map((row) => ({
    userId: row.userId,
    name: row.name,
    row,
    leftOut: row.defaultKind === ContributionEmailKind.INCASSO_NOTIFICATION
      ? "Pays by incasso"
      : row.disposition === BulkRowDisposition.EXCLUDED ? reasonLabel(row.reason) || "Not written to" : null,
    remindedBefore: row.lastRemindedOn ?? null,
    first: row.lastRemindedOn == null && row.memberSince != null && row.memberSince >= periodStart,
  }))
}

/** What the email a row gets is called. */
export const reminderName = (row: ReminderRow): string => (row.first ? "First payment email" : "Payment reminder")
