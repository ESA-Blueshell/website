import type {BulkMembershipPreviewRow} from "@/services/api"
import type {BulkRow} from "@/utils/bulkRow"
import type {BulkTarget} from "@/utils/bulkTarget"

/**
 * Dresses a server-decided preview row with the member details the table already holds.
 *
 * Membership actions differ from the contribution ones: the api decides them, because the
 * rules are the api's and the date is the api's clock. What the browser still owns is who
 * these ids belong to — the name, the member type and the date they joined — so the two
 * are joined here rather than sending the whole roster back and forth.
 *
 * The preview's order is kept, and a row naming a member the table has since lost still
 * renders, under its id, rather than disappearing from a confirmation that counts it.
 */
export function bulkRowsFromPreview(
  targets: BulkTarget[],
  previewRows: BulkMembershipPreviewRow[],
): BulkRow[] {
  const byUserId = new Map(targets.map((target) => [target.userId, target]))

  return previewRows.map((previewRow) => {
    const target = byUserId.get(previewRow.userId)
    const row: BulkRow = {
      userId: previewRow.userId,
      name: target?.name ?? `#${previewRow.userId}`,
      disposition: previewRow.disposition,
    }
    if (previewRow.reason) row.reason = previewRow.reason
    if (target?.mostRecentMembership) row.memberType = target.mostRecentMembership.type
    if (target?.memberSince) row.memberSince = target.memberSince
    return row
  })
}
