import {MemberType, type ContributionPeriodResponse, type MembershipResponse} from "@/services/api"

/**
 * Preprocessed target for bulk-action FE computations. Encapsulates all data needed
 * to compute preview rows, avoiding re-derivation in each dialog and guaranteeing
 * consistent use of the most-recent membership (by startDate DESC).
 * See docs/proposals/bulk-actions/REDESIGN.md §4.
 */

export interface BulkTarget {
  userId: number
  name: string
  email: string | null
  mostRecentMembership: {
    type: MemberType
    startDate: string
    endDate: string | null
    incasso: boolean
  } | null
  mostRecentContribution: {
    paid: boolean
  } | null
  isHonorary: boolean
}

/**
 * Derive the most-recent (latest startDate) membership from a list.
 * The input list MUST be sorted by startDate DESC for O(1) access.
 * Falls back to linear scan if not pre-sorted (backwards compatibility).
 */
export function deriveLatestMembership(ms: MembershipResponse[]): MembershipResponse | null {
  if (ms.length === 0) return null
  const first = ms[0]!
  return ms.reduce<MembershipResponse>((latest, m) => (m.startDate > latest.startDate ? m : latest), first)
}

/**
 * Get today's date in Amsterdam timezone, ISO format (en-CA = YYYY-MM-DD).
 */
export function amsterdamToday(): string {
  return new Date().toLocaleDateString('en-CA', {timeZone: 'Europe/Amsterdam'})
}

/**
 * Get the period with the latest (max) startDate from a list.
 * Used for resume-membership classification (latest contribution period).
 */
export function latestPeriodOf(
  periods: ContributionPeriodResponse[],
): ContributionPeriodResponse | null {
  if (periods.length === 0) return null
  const first = periods[0]!
  return periods.reduce<ContributionPeriodResponse>(
    (latest, p) => (p.startDate > latest.startDate ? p : latest),
    first,
  )
}

/**
 * Build a BulkTarget for each selected user ID, pulling name, email, most-recent
 * membership, paid status, and honorary flag from the parent data.
 *
 * The host (MemberManager.vue) pre-sorts membershipsByUserId entries by startDate DESC
 * so [0] is always the most recent (or needs backfill if only one membership).
 */
export function computeBulkTargets(
  selectedIds: number[],
  membershipsByUserId: Map<number, MembershipResponse[]>,
  paidUserIds: Set<number>,
  usersById: Map<
    number,
    {fullName?: string | null; email?: string | null; [key: string]: unknown}
  >,
): BulkTarget[] {
  return selectedIds.map((userId) => {
    const user = usersById.get(userId)
    const memberships = membershipsByUserId.get(userId) ?? []
    const latest = deriveLatestMembership(memberships)

    return {
      userId,
      name: user?.fullName ?? String(userId),
      email: user?.email ?? null,
      mostRecentMembership: latest
        ? {
            type: latest.memberType,
            startDate: latest.startDate,
            endDate: latest.endDate ?? null,
            incasso: latest.incasso,
          }
        : null,
      mostRecentContribution: {
        paid: paidUserIds.has(userId),
      },
      isHonorary: latest?.memberType === MemberType.HONORARY,
    }
  })
}
