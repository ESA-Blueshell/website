import type {MemberType} from "@/services/api"

/**
 * Local FE type model for bulk-action preview rows.
 *
 * The bulk preview is now computed entirely client-side (see bulkCompute.ts): the
 * backend no longer exposes preview endpoints, so the generated client no longer
 * carries the former preview-row / preview-result types. These hand-written types
 * replace that generated shape and are the single source of truth for the per-action
 * dialogs, the scaffold, and the display helpers. See docs/proposals/bulk-actions/REDESIGN.md.
 */

/** How a selected user will be treated by a bulk action. */
export type BulkDisposition = "INCLUDED" | "SKIPPED" | "EXCLUDED" | "WARNING"

/** Machine-readable reason code for a non-INCLUDED disposition (or a resume outcome). */
export type BulkRowReason =
  | "ALREADY_PAID"
  | "NOT_PAID"
  | "HONORARY"
  | "NO_EMAIL"
  | "INCASSO_MISMATCH"
  | "NO_ACTIVE_MEMBERSHIP"
  | "ALREADY_ACTIVE"
  | "WILL_RESUME"
  | "WILL_START_NEW"
  | "NO_CONTRIBUTION_PERIOD"
  | "STARTED_TODAY"
  | "PAYS_VIA_INCASSO"
  | "CANNOT_END_COMMITTEE"
  | "CANNOT_END_BOARD"
  | "CANNOT_END_ADMIN"
  | "CANNOT_END_HONORARY"

/** Fee type for contribution-reminder / incasso-notification bulk actions. */
export type FeeType = "FULL_YEAR_FEE" | "HALF_YEAR_FEE" | "ALUMNI_FEE"

/** Summary counts for the bulk-action confirmation dialog's summary bar. */
export interface BulkActionCounts {
  selected: number
  willApply: number
  skipped: number
  excluded: number
  warned: number
}

/** A single computed preview row for a bulk action. */
export interface BulkRow {
  userId: number
  name: string
  disposition: BulkDisposition
  reason?: BulkRowReason
  memberType?: MemberType
  memberSince?: string
  amount?: number | null
  recommendedFeeType?: FeeType
  /** Email actions only: when the last reminder/incasso was sent, if known. */
  lastSentOn?: string
}
