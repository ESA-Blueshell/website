import type {BulkTarget} from "@/utils/bulkTarget"
import {MemberType, type ContributionPeriodResponse} from "@/services/api"

/**
 * Create a minimal BulkTarget.
 */
export function target(userId: number, overrides?: Partial<BulkTarget>): BulkTarget {
  return {
    userId,
    name: `User ${userId}`,
    email: `user${userId}@example.com`,
    memberSince: "2024-01-01",
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
    },
    mostRecentContribution: {
      paid: false,
    },
    isHonorary: false,
    ...overrides,
  }
}

/**
 * Create a ContributionPeriodResponse with sensible fee defaults (full 20, half 10, alumni 5).
 */
export function period(overrides?: Partial<ContributionPeriodResponse>): ContributionPeriodResponse {
  return {
    id: 1,
    startDate: "2025-01-01",
    endDate: "2025-12-31",
    fullYearFee: 20.0,
    halfYearCutoffDate: "2025-07-01",
    halfYearFee: 10.0,
    alumniFee: 5.0,
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
    version: 0,
    ...overrides,
  }
}

// ── Reminder / incasso-email preset helpers ─────────────

/** Honorary member — EXCLUDED in reminder/incasso actions, SKIPPED in paid-status/end/resume. */
export function honoraryTarget(userId: number): BulkTarget {
  return target(userId, {
    isHonorary: true,
    mostRecentMembership: {
      type: MemberType.HONORARY,
      startDate: "2024-01-01",
      endDate: null,
    },
  })
}

/** Member whose contribution is already paid. */
export function alreadyPaidTarget(userId: number): BulkTarget {
  return target(userId, {
    mostRecentContribution: {paid: true},
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
    },
  })
}

/** Member with no membership record. */
export function noMembershipTarget(userId: number): BulkTarget {
  return target(userId, {mostRecentMembership: null, memberSince: null})
}

/**
 * Member with an ended membership (endDate in the past, before the latest period).
 * Default for resume-membership action: WILL_START_NEW.
 */
export function endedMemberTarget(userId: number): BulkTarget {
  return target(userId, {
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: "2024-12-31",
    },
  })
}
