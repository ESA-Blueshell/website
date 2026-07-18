import type {MembershipResponse} from "@/services/api"
import type {BulkPreviewRow} from "@/services/api/blueshell/types.gen"

/**
 * Frontend preview computations for the stateless bulk actions (mark-paid/unpaid are
 * trivial and live in their dialogs; end-membership is here because it needs the
 * memberships list and a timezone-safe `today`). These are pure functions so they can
 * be unit-tested against useMemberRows-shaped data, including the serverToday boundary.
 * See docs/proposals/bulk-actions/REDESIGN.md §4 & §7.
 */

/**
 * Compute the end-membership preview rows locally.
 *
 * A user is INCLUDED when they have at least one active (no endDate) membership that
 * started strictly before `serverToday`. If they have an active membership that started
 * *today*, they are SKIPPED (STARTED_TODAY). If they have no active membership at all,
 * they are SKIPPED (NO_ACTIVE_MEMBERSHIP). `serverToday` MUST be the server's date
 * (from the end-preview response), never the browser's, to avoid timezone drift.
 */
export function computeEndMembershipRows(
  userIds: number[],
  membershipsByUserId: Map<number, MembershipResponse[]>,
  namesById: Record<number, string>,
  serverToday: string,
): BulkPreviewRow[] {
  return userIds.map((userId) => {
    const memberships = membershipsByUserId.get(userId) ?? []
    const active = memberships.filter((m) => m.endDate == null)
    const endable = active.filter((m) => m.startDate < serverToday)
    const first = active[0]
    return {
      userId,
      name: namesById[userId] ?? String(userId),
      memberType: first?.memberType,
      memberSince: first?.startDate,
      disposition: endable.length > 0 ? "INCLUDED" : "SKIPPED",
      reason: endable.length > 0 ? undefined : active.length > 0 ? "STARTED_TODAY" : "NO_ACTIVE_MEMBERSHIP",
    }
  })
}
