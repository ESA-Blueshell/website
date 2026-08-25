import type {BulkRow} from "@/utils/bulkRow"
import {BulkRowDisposition, BulkRowReason} from "@/utils/bulkRow"
import type {BulkTarget} from "@/utils/bulkTarget"

/**
 * What a bulk contribution action would do to each selected row, worked out in the browser
 * from what the page already loaded — the memberships, the paid set and the users.
 *
 * The api decides again on submit and refuses a selection it cannot apply whole, so this is
 * a preview and not an authority: it exists so the operator sees who is included before
 * pressing send.
 */

/**
 * Mark as paid: honorary → SKIPPED(HONORARY); already paid → SKIPPED(ALREADY_PAID); else INCLUDED.
 */
export function computeMarkPaidRows(targets: BulkTarget[]): BulkRow[] {
  return targets.map((target) => {
    const row: BulkRow = {
      disposition: BulkRowDisposition.INCLUDED,
      userId: target.userId,
      name: target.name,
    }

    // Populate membership info for every row regardless of disposition, so even
    // skipped rows show Type + Member-since (only "—" when there is no membership).
    if (target.mostRecentMembership) {
      row.memberType = target.mostRecentMembership.type
      row.memberSince = target.mostRecentMembership.startDate
    }

    if (target.isHonorary) {
      row.disposition = BulkRowDisposition.SKIPPED
      row.reason = BulkRowReason.HONORARY
    } else if (target.mostRecentContribution.paid) {
      row.disposition = BulkRowDisposition.SKIPPED
      row.reason = BulkRowReason.ALREADY_PAID
    } else {
      row.disposition = BulkRowDisposition.INCLUDED
    }

    return row
  })
}

/**
 * Mark as unpaid: honorary → SKIPPED(HONORARY); not paid → SKIPPED(NOT_PAID); else INCLUDED.
 */
export function computeMarkUnpaidRows(targets: BulkTarget[]): BulkRow[] {
  return targets.map((target) => {
    const row: BulkRow = {
      disposition: BulkRowDisposition.INCLUDED,
      userId: target.userId,
      name: target.name,
    }

    // Populate membership info for every row regardless of disposition, so even
    // skipped rows show Type + Member-since (only "—" when there is no membership).
    if (target.mostRecentMembership) {
      row.memberType = target.mostRecentMembership.type
      row.memberSince = target.mostRecentMembership.startDate
    }

    if (target.isHonorary) {
      row.disposition = BulkRowDisposition.SKIPPED
      row.reason = BulkRowReason.HONORARY
    } else if (!target.mostRecentContribution.paid) {
      row.disposition = BulkRowDisposition.SKIPPED
      row.reason = BulkRowReason.NOT_PAID
    } else {
      row.disposition = BulkRowDisposition.INCLUDED
    }

    return row
  })
}
