import {MemberType, type MembershipResponse} from "@/services/api"
import {deriveLatestMembership, deriveMemberSince} from "@/composables/useUserRows"

// Re-exported at the name its callers already use. The rule lives beside the other row
// derivations in useUserRows, so an edit to it cannot reach only half the pages that read it.
export {deriveLatestMembership}

/**
 * A selected user, with everything a bulk contribution preview needs already worked out.
 *
 * Derived once for the whole selection rather than per dialog, so every row is judged
 * against the same membership — the most recent one — instead of each caller picking its
 * own and disagreeing.
 */
export interface BulkTarget {
  userId: number
  name: string
  email: string | null
  /**
   * The earliest start across every membership, which is what the manager table means by
   * "member since". A member who left and came back keeps the date they first joined, so
   * the dialogs and the table behind them cannot disagree about it.
   */
  memberSince: string | null
  mostRecentMembership: {
    type: MemberType
    startDate: string
    endDate: string | null
    incasso: boolean
  } | null
  mostRecentContribution: {
    paid: boolean
  }
  isHonorary: boolean
}

/** Build a target per selected id from the data the page has already loaded. */
export function computeBulkTargets(
  selectedIds: number[],
  membershipsByUserId: Map<number, MembershipResponse[]>,
  paidUserIds: Set<number>,
  usersById: Map<number, {fullName?: string | null; email?: string | null}>,
): BulkTarget[] {
  return selectedIds.map((userId) => {
    const user = usersById.get(userId)
    const held = membershipsByUserId.get(userId) ?? []
    const latest = deriveLatestMembership(held)

    return {
      userId,
      name: user?.fullName ?? String(userId),
      email: user?.email ?? null,
      memberSince: deriveMemberSince(held),
      mostRecentMembership: latest
        ? {
            type: latest.memberType,
            startDate: latest.startDate,
            endDate: latest.endDate ?? null,
            incasso: latest.incasso,
          }
        : null,
      mostRecentContribution: {paid: paidUserIds.has(userId)},
      isHonorary: latest?.memberType === MemberType.HONORARY,
    }
  })
}
