import {DateTime} from "luxon"
import type {BulkDisposition, BulkRow, BulkRowReason} from "@/utils/bulkRow"

/**
 * Pure display helpers for bulk-action preview rows, lifted verbatim out of the old
 * BulkActionConfirmDialog monolith. Shared by every per-action dialog via the scaffold.
 * See docs/proposals/bulk-actions/REDESIGN.md §5.1.
 */

// Re-exported for existing importers that pulled these off bulkDisposition.
export type {BulkDisposition, BulkRowReason}

/**
 * The disposition as it applies after operator overrides: a WARNING row the operator
 * has re-included counts as INCLUDED. The original reason is still shown in the note
 * column so the flag stays visible.
 */
export function effectiveDisposition(
  row: Pick<BulkRow, "disposition" | "userId">,
  reincludeOverrides: Record<number, boolean>,
): BulkDisposition {
  if (row.disposition === "WARNING" && reincludeOverrides[row.userId]) return "INCLUDED"
  return row.disposition
}

export function rowColorClass(disposition: BulkDisposition): string {
  if (disposition === "EXCLUDED") return "bulk-row--excluded"
  if (disposition === "WARNING") return "bulk-row--warning"
  if (disposition === "SKIPPED") return "bulk-row--skipped"
  return ""
}

export function dispositionLabel(disposition: BulkDisposition): string {
  if (disposition === "INCLUDED") return "Included"
  if (disposition === "EXCLUDED") return "Excluded"
  if (disposition === "WARNING") return "Warning"
  if (disposition === "SKIPPED") return "Skipped"
  return disposition
}

export function dispositionColor(disposition: BulkDisposition): string {
  if (disposition === "EXCLUDED") return "error"
  if (disposition === "WARNING") return "warning"
  if (disposition === "SKIPPED") return "grey"
  return "success"
}

const REASON_LABELS: Record<BulkRowReason, string> = {
  INCASSO_MISMATCH: "Not marked for incasso",
  ALREADY_PAID: "Already paid",
  HONORARY: "Honorary (no contribution needed)",
  NOT_PAID: "Not paid",
  NO_ACTIVE_MEMBERSHIP: "No active membership",
  STARTED_TODAY: "Started today",
  NO_EMAIL: "No email address on file",
  ALREADY_ACTIVE: "Already has an active membership",
  NO_CONTRIBUTION_PERIOD: "No contribution period",
  WILL_RESUME: "Will resume",
  WILL_START_NEW: "Will start new",
}

export function reasonLabel(reason: BulkRowReason | undefined | null): string {
  if (!reason) return ""
  return REASON_LABELS[reason] ?? String(reason).replace(/_/g, " ")
}

/** Every date a bulk dialog shows, in the day-first form the rest of the manager uses. */
export function formatBulkDate(dateStr: string | undefined | null): string {
  if (!dateStr) return "—"
  const dt = DateTime.fromISO(dateStr)
  return dt.isValid ? dt.toFormat("dd/MM/yyyy") : "—"
}
