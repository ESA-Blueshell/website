import {DateTime} from "luxon"
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
  }
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
 * Get the most-recent *already-started* contribution period (latest startDate
 * that is not in the future). Used as the basis for resume-membership
 * classification. Not-yet-started (future) periods are ignored so a future
 * period does not become the resume basis — this mirrors the backend's
 * "current period" semantics and keeps the choice deterministic when several
 * periods exist. Falls back to the overall latest if none have started.
 */
export function latestPeriodOf(
  periods: ContributionPeriodResponse[],
): ContributionPeriodResponse | null {
  if (periods.length === 0) return null
  const today = amsterdamToday()
  const started = periods.filter((p) => p.startDate <= today)
  const pool = started.length > 0 ? started : periods
  return pool.reduce<ContributionPeriodResponse>(
    (latest, p) => (p.startDate > latest.startDate ? p : latest),
    pool[0]!,
  )
}

/**
 * Default half-year cutoff date for the reminder/incasso dialogs, derived from a period.
 *
 * Recipe (locked): take the midpoint of the period
 *   midpoint = startDate + floor((endDate - startDate) / 2) days
 * then add one month and set the day to the 1st of that month. The result is clamped
 * to stay within [startDate, endDate]. Returns "" when the period is missing/invalid.
 *
 * Both bounds are ISO (YYYY-MM-DD); the return is ISO too.
 */
export function halfYearCutoffDefault(
  period: Pick<ContributionPeriodResponse, "startDate" | "endDate"> | null | undefined,
): string {
  if (!period?.startDate || !period?.endDate) return ""
  const start = DateTime.fromISO(period.startDate)
  const end = DateTime.fromISO(period.endDate)
  if (!start.isValid || !end.isValid) return ""

  const spanDays = Math.floor(end.diff(start, "days").days)
  const midpoint = start.plus({days: Math.floor(spanDays / 2)})
  let cutoff = midpoint.plus({months: 1}).set({day: 1})

  // Clamp within the period.
  if (cutoff < start) cutoff = start
  if (cutoff > end) cutoff = end

  return cutoff.toFormat("yyyy-MM-dd")
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
