import {DateTime} from "luxon"
import type {FeeCycleRowResponse} from "@/services/api"
import {FeeCycleGroup} from "@/services/api"
import {BulkFeeType, type BulkRow, type FeeCycleSide} from "@/utils/bulkRow"

/**
 * The fee cycle's rows, as the dialog reads them.
 *
 * Unlike the paid/unpaid dialogs, these are not computed in the browser: the cycle is over
 * every unpaid member of a period rather than over a selection the page already holds, and
 * the api decides it once so the preview and the send cannot disagree. This is the mapping
 * from that answer onto the row shape the shared scaffold renders.
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

export const feeCycleSideLabels: Record<FeeCycleSide, string> = {
  [FeeCycleGroup.DIRECT_DEBIT]: "Direct debit",
  [FeeCycleGroup.TRANSFER]: "Transfer",
}

export function feeCycleSideLabel(group: FeeCycleSide | undefined): string {
  return group ? feeCycleSideLabels[group] : "—"
}

/** Two chips rather than one, because the counts per side are the thing being checked. */
export function countBySide(rows: BulkRow[]): Record<FeeCycleSide, number> {
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
  if (!iso) return "Never"
  const parsed = DateTime.fromISO(iso)
  return parsed.isValid ? parsed.toFormat("dd/MM/yyyy") : "Never"
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
