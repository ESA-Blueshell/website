import type {BulkTarget} from "@/utils/bulkTarget"
import {MemberType, type ContributionPeriodResponse} from "@/services/api"

/**
 * Create a minimal BulkTarget. incasso defaults false; pass {incasso: true} for dialogs
 * where the base roster is incasso-payers (Incasso, PaidStatus, EndMembership).
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
      incasso: false,
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

// ── Reminder / incasso-email preset helpers (base: incasso: false) ─────────────

/** Regular unpaid non-incasso member — included by default in reminder action. */
export function regularTarget(userId: number): BulkTarget {
  return target(userId)
}

/** Member on incasso — WARNING in reminder, INCLUDED in incasso action. */
export function incassoPayerTarget(userId: number): BulkTarget {
  return target(userId, {
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
      incasso: true,
    },
  })
}

/** Member explicitly NOT on incasso — used for INCASSO_MISMATCH tests. */
export function noIncassoTarget(userId: number): BulkTarget {
  return target(userId, {
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
      incasso: false,
    },
  })
}

/** Member with no email address — SKIPPED(NO_EMAIL) in any email action. */
export function noEmailTarget(userId: number, incasso = false): BulkTarget {
  return target(userId, {
    email: null,
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
      incasso,
    },
  })
}

/** Honorary member — EXCLUDED in reminder/incasso actions, SKIPPED in paid-status/end/resume. */
export function honoraryTarget(userId: number): BulkTarget {
  return target(userId, {
    isHonorary: true,
    mostRecentMembership: {
      type: MemberType.HONORARY,
      startDate: "2024-01-01",
      endDate: null,
      incasso: false,
    },
  })
}

/** Alumni member. */
export function alumniTarget(userId: number): BulkTarget {
  return target(userId, {
    mostRecentMembership: {
      type: MemberType.ALUMNI,
      startDate: "2024-01-01",
      endDate: null,
      incasso: false,
    },
  })
}

/** Member whose contribution is already paid. */
export function alreadyPaidTarget(userId: number, incasso = false): BulkTarget {
  return target(userId, {
    mostRecentContribution: {paid: true},
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
      incasso,
    },
  })
}

/** Member with no membership record. */
export function noMembershipTarget(userId: number): BulkTarget {
  return target(userId, {mostRecentMembership: null, memberSince: null})
}

/**
 * Member whose membership ended recently, within the latest period.
 * Useful for resume-membership WILL_RESUME tests.
 */
export function recentlyEndedTarget(userId: number): BulkTarget {
  return target(userId, {
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: "2025-06-15",
      incasso: false,
    },
  })
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
      incasso: false,
    },
  })
}
