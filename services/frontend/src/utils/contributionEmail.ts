import {ContributionEmailKind, type BulkContributionEmailRowResponse} from "@/services/api"
import {formatBulkDate} from "@/utils/bulkDisposition"
import {BulkFeeType, type BulkRow} from "@/utils/bulkRow"

/**
 * The payment-email rows, as the dialog reads them.
 *
 * Decided by the api rather than the browser: whether a member is warned about turns on
 * facts this page does not hold, and the send re-reads the same answer.
 */

export function toBulkRow(row: BulkContributionEmailRowResponse): BulkRow {
  return {
    userId: row.userId,
    name: row.name,
    disposition: row.disposition,
    reason: row.reason ?? undefined,
    memberType: row.memberType,
    memberSince: row.memberSince ?? undefined,
    amount: row.amount ?? null,
    recommendedFeeType: row.feeType ?? undefined,
    defaultKind: row.defaultKind,
    lastRemindedOn: row.lastRemindedOn ?? undefined,
    lastNotifiedOn: row.lastNotifiedOn ?? undefined,
  }
}

export function toBulkRows(rows: BulkContributionEmailRowResponse[]): BulkRow[] {
  return rows.map(toBulkRow)
}

export const contributionEmailLabels: Record<ContributionEmailKind, string> = {
  [ContributionEmailKind.REMINDER]: "Contribution reminder",
  [ContributionEmailKind.INCASSO_NOTIFICATION]: "Incasso notification",
}

export const contributionEmailItems: Array<{title: string; value: ContributionEmailKind}> =
  Object.values(ContributionEmailKind).map((value) => ({title: contributionEmailLabels[value], value}))

/** Which email a row is set to: the flag's choice unless the treasurer switched it. */
export function kindFor(row: BulkRow, chosen: Record<number, ContributionEmailKind>): ContributionEmailKind {
  return chosen[row.userId] ?? row.defaultKind ?? ContributionEmailKind.REMINDER
}

export function isSwitched(row: BulkRow, chosen: Record<number, ContributionEmailKind>): boolean {
  return !!row.defaultKind && kindFor(row, chosen) !== row.defaultKind
}

/** Why switching this row is worth flagging: the flag says the other thing. */
export function switchedNote(row: BulkRow): string {
  return row.defaultKind === ContributionEmailKind.INCASSO_NOTIFICATION
    ? "Pays by direct debit"
    : "Not marked for direct debit"
}

/** When this member last got the email they are getting now. */
export function lastSentOn(
  row: BulkRow,
  chosen: Record<number, ContributionEmailKind>,
): string | undefined {
  return kindFor(row, chosen) === ContributionEmailKind.INCASSO_NOTIFICATION
    ? row.lastNotifiedOn
    : row.lastRemindedOn
}

export function lastSentLabel(iso: string | undefined): string {
  const formatted = formatBulkDate(iso)
  // The absence is the answer here, so it reads as a word rather than an em dash.
  return formatted === "—" ? "Never" : formatted
}

/** The same rule the api applies on send: a warning is overridable, a hard exclusion is not. */
export function willSend(row: BulkRow, forciblyIncluded: Record<number, boolean>): boolean {
  if (row.disposition === "INCLUDED") return true
  return row.disposition === "WARNING" && !!forciblyIncluded[row.userId]
}

/** How many of each email this send would put out. */
export function countByKind(
  rows: BulkRow[],
  chosen: Record<number, ContributionEmailKind>,
  forciblyIncluded: Record<number, boolean>,
): Record<ContributionEmailKind, number> {
  const counts = {
    [ContributionEmailKind.REMINDER]: 0,
    [ContributionEmailKind.INCASSO_NOTIFICATION]: 0,
  }
  for (const row of rows) {
    if (willSend(row, forciblyIncluded)) counts[kindFor(row, chosen)]++
  }
  return counts
}

/** Only the rows the treasurer moved off the email their flag chose. */
export function changedKinds(
  rows: BulkRow[],
  chosen: Record<number, ContributionEmailKind>,
): Record<string, ContributionEmailKind> {
  const changed: Record<string, ContributionEmailKind> = {}
  for (const row of rows) {
    if (isSwitched(row, chosen)) changed[String(row.userId)] = kindFor(row, chosen)
  }
  return changed
}

/**
 * Only the fee types the treasurer changed. Stating every row would claim a choice where
 * none was made, and the api refuses a type naming somebody it does not write to.
 */
export function changedFeeTypes(
  rows: BulkRow[],
  selections: Record<number, BulkFeeType>,
): Record<string, BulkFeeType> {
  const changed: Record<string, BulkFeeType> = {}
  for (const row of rows) {
    const chosen = selections[row.userId]
    if (chosen && chosen !== row.recommendedFeeType) changed[String(row.userId)] = chosen
  }
  return changed
}
