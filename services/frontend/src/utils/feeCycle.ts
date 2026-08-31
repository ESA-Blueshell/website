import type {FeeCycleRowResponse} from "@/services/api"
import {formatBulkDate} from "@/utils/bulkDisposition"
import {BulkFeeType, FeeCycleGroup, type BulkRow} from "@/utils/bulkRow"

/**
 * The fee cycle's rows, as the dialog reads them.
 *
 * Unlike the paid/unpaid dialogs, these are not computed in the browser: the cycle is over
 * every unpaid member of a period rather than over a selection the page already holds, and
 * the api decides it once so the preview and the send cannot disagree. This is the mapping
 * from that answer onto the row shape the shared scaffold renders.
 *
 * A straight mapping rather than the join `bulkRowsFromPreview` does for the membership
 * actions, because the row already carries the member details. `memberSince` in particular
 * is the start of the membership the api *judged* — the active one where the member has one
 * — and a browser deriving it would be a second implementation of that rule, free to
 * disagree with the one that decided the partition.
 */

/** One row, plus the side of the partition, which the scaffold renders as its own column. */
export function toBulkRow(row: FeeCycleRowResponse): BulkRow {
  return {
    userId: row.userId,
    name: row.name,
    disposition: row.disposition,
    reason: row.reason ?? undefined,
    memberType: row.memberType,
    memberSince: row.memberSince ?? undefined,
    amount: row.amount ?? null,
    recommendedFeeType: row.feeType ?? undefined,
    lastSentOn: row.lastAskedOn ?? undefined,
    group: row.group,
  }
}

export function toBulkRows(rows: FeeCycleRowResponse[]): BulkRow[] {
  return rows.map(toBulkRow)
}

export const feeCycleGroupLabels: Record<FeeCycleGroup, string> = {
  [FeeCycleGroup.DIRECT_DEBIT]: "Direct debit",
  [FeeCycleGroup.TRANSFER]: "Transfer",
}

export function feeCycleGroupLabel(group: FeeCycleGroup | undefined): string {
  return group ? feeCycleGroupLabels[group] : "—"
}

/** Two chips rather than one, because the count per group is the thing being checked. */
export function countByGroup(rows: BulkRow[]): Record<FeeCycleGroup, number> {
  return {
    DIRECT_DEBIT: rows.filter((row) => row.group === FeeCycleGroup.DIRECT_DEBIT).length,
    TRANSFER: rows.filter((row) => row.group === FeeCycleGroup.TRANSFER).length,
  }
}

/**
 * A member never asked reads as never, not as an empty cell — the treasurer is looking for
 * the ones already asked, so the absence has to be as legible as the date.
 */
export function lastAskedLabel(iso: string | undefined): string {
  const formatted = formatBulkDate(iso)
  // formatBulkDate is the one date format every bulk dialog uses; only the word for an
  // absent one differs, because here the absence is the answer rather than missing data.
  return formatted === "—" ? "Never" : formatted
}

/**
 * Whether this member has already been asked for this period on this side of the partition.
 *
 * Sending again is allowed, as often as the treasurer needs, so an already-asked member
 * stays included rather than becoming a row to tick back in — half way through the year that
 * would be a hundred ticks. It is warned about instead, and each ask is its own record, so
 * the warning is a note about history rather than a constraint.
 */
export function askedAlready(row: BulkRow): boolean {
  return !!row.lastSentOn
}

/** How many of the members about to be written to have already been asked. */
export function countAskedAlready(rows: BulkRow[]): number {
  return rows.filter((row) => row.disposition === "INCLUDED" && askedAlready(row)).length
}

/**
 * Only the fee types the treasurer actually changed.
 *
 * Sending every row's type would make the request state a choice where none was made, and
 * the api refuses a type naming somebody it does not write to — so a row that dropped out
 * of the cycle since the preview would refuse a send the treasurer did not ask to change.
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
