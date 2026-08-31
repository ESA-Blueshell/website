import type {MemberType} from "@/services/api"
import {BulkFeeType, BulkRowDisposition, BulkRowReason, FeeCycleGroup} from "@/services/api"

/**
 * Local FE model for bulk-action rows. Rows are computed client-side (bulkCompute.ts),
 * so there is no generated row type — but the vocabulary below is generated, which is
 * what stops it drifting from the Kotlin enums.
 */

// String-literal unions, so helpers and templates can compare against raw values while
// still failing to compile on a code the backend does not define. The enums themselves
// are re-exported for callers that need the values, e.g. to build a select.
export type BulkDisposition = `${BulkRowDisposition}`
export type BulkRowReasonCode = `${BulkRowReason}`
export type FeeCycleSide = `${FeeCycleGroup}`
export {BulkFeeType, BulkRowDisposition, BulkRowReason, FeeCycleGroup}

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
  /**
   * The date the dialog shows under "Member since", which is a different fact per action
   * because the operator is reading it for a different reason.
   *
   * The contribution actions fill it with the current membership's start, because the
   * half-year cutoff is measured from that and the column is how the operator sees which
   * fee a row will be charged. The membership actions fill it with the earliest start
   * across the set, because there the question is when this person first joined — a
   * returning member keeps their original date.
   */
  memberSince?: string | null
  amount?: number | null
  recommendedFeeType?: BulkFeeType
  /** Email actions only: when the last reminder/incasso was sent, if known. */
  lastSentOn?: string
  /**
   * Fee cycle only: which side of the direct-debit partition this member is on. Set by the
   * api from the member's own flag, so the dialog shows it rather than offering it.
   */
  group?: FeeCycleSide
}
