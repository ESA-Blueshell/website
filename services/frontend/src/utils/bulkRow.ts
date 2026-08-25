import type {MemberType} from "@/services/api"
import {BulkFeeType, BulkRowDisposition, BulkRowReason} from "@/services/api"

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
export type FeeType = `${BulkFeeType}`
export {BulkFeeType, BulkRowDisposition, BulkRowReason}

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
