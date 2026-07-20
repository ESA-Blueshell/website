import {describe, expect, it} from "vitest"
import {
  computeEndMembershipRows,
  computeIncassoRows,
  computeMarkPaidRows,
  computeMarkUnpaidRows,
  computeReminderRows,
  computeResumeMembershipRows,
} from "@/utils/bulkCompute"
import {MemberType, type ContributionPeriodResponse} from "@/services/api"
import type {BulkTarget} from "@/utils/bulkTarget"

/**
 * Test suite for bulk-action row computation functions.
 * The new architecture computes all preview rows client-side from BulkTarget[] props.
 * See docs/proposals/bulk-actions/REDESIGN.md §4 & §5.2.
 */

// ── Test fixture builders ──────────────────────────────────────────────

function bulkTarget(
  userId: number,
  overrides?: Partial<BulkTarget>,
): BulkTarget {
  return {
    userId,
    name: `User ${userId}`,
    email: `user${userId}@example.com`,
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

function period(overrides?: Partial<ContributionPeriodResponse>): ContributionPeriodResponse {
  return {
    id: 1,
    startDate: "2025-01-01",
    endDate: "2025-12-31",
    fullYearFee: 20.0,
    halfYearFee: 10.0,
    alumniFee: 5.0,
    ...overrides,
  } as ContributionPeriodResponse
}

// ── Mark as Paid tests ─────────────────────────────────────────────────

describe("computeMarkPaidRows", () => {
  it("INCLUDES a regular unpaid member", () => {
    const targets = [bulkTarget(1)]
    const rows = computeMarkPaidRows(targets)
    expect(rows[0]).toMatchObject({
      userId: 1,
      disposition: "INCLUDED",
      memberType: MemberType.REGULAR,
      memberSince: "2024-01-01",
    })
  })

  it("SKIPS an already-paid member with ALREADY_PAID reason", () => {
    const targets = [
      bulkTarget(1, {
        mostRecentContribution: {paid: true},
      }),
    ]
    const rows = computeMarkPaidRows(targets)
    expect(rows[0]).toMatchObject({
      userId: 1,
      disposition: "SKIPPED",
      reason: "ALREADY_PAID",
    })
  })

  it("SKIPS a honorary member with HONORARY reason", () => {
    const targets = [
      bulkTarget(1, {
        isHonorary: true,
        mostRecentMembership: {
          type: MemberType.HONORARY,
          startDate: "2023-01-01",
          endDate: null,
          incasso: false,
        },
      }),
    ]
    const rows = computeMarkPaidRows(targets)
    expect(rows[0]).toMatchObject({
      userId: 1,
      disposition: "SKIPPED",
      reason: "HONORARY",
    })
  })

  it("handles mixed targets correctly", () => {
    const targets = [
      bulkTarget(1), // regular unpaid → INCLUDED
      bulkTarget(2, {mostRecentContribution: {paid: true}}), // paid → SKIPPED
      bulkTarget(3, {isHonorary: true}), // honorary → SKIPPED
    ]
    const rows = computeMarkPaidRows(targets)
    expect(rows[0]).toMatchObject({disposition: "INCLUDED"})
    expect(rows[1]).toMatchObject({disposition: "SKIPPED", reason: "ALREADY_PAID"})
    expect(rows[2]).toMatchObject({disposition: "SKIPPED", reason: "HONORARY"})
  })

  it("omits memberType/memberSince if no membership", () => {
    const targets = [
      bulkTarget(1, {
        mostRecentMembership: null,
      }),
    ]
    const rows = computeMarkPaidRows(targets)
    expect(rows[0]).toMatchObject({
      userId: 1,
      disposition: "INCLUDED",
    })
    expect(rows[0].memberType).toBeUndefined()
    expect(rows[0].memberSince).toBeUndefined()
  })
})

// ── Mark as Unpaid tests ───────────────────────────────────────────────

describe("computeMarkUnpaidRows", () => {
  it("INCLUDES a regular paid member", () => {
    const targets = [bulkTarget(1, {mostRecentContribution: {paid: true}})]
    const rows = computeMarkUnpaidRows(targets)
    expect(rows[0]).toMatchObject({
      userId: 1,
      disposition: "INCLUDED",
    })
  })

  it("SKIPS an unpaid member with NOT_PAID reason", () => {
    const targets = [bulkTarget(1, {mostRecentContribution: {paid: false}})]
    const rows = computeMarkUnpaidRows(targets)
    expect(rows[0]).toMatchObject({
      userId: 1,
      disposition: "SKIPPED",
      reason: "NOT_PAID",
    })
  })

  it("SKIPS honorary members with HONORARY reason", () => {
    const targets = [bulkTarget(1, {isHonorary: true})]
    const rows = computeMarkUnpaidRows(targets)
    expect(rows[0]).toMatchObject({
      userId: 1,
      disposition: "SKIPPED",
      reason: "HONORARY",
    })
  })
})

// ── Contribution Reminder tests ────────────────────────────────────────

describe("computeReminderRows", () => {
  it("INCLUDES a regular member with no email → SKIPPED(NO_EMAIL)", () => {
    const targets = [bulkTarget(1, {email: null})]
    const rows = computeReminderRows(targets, period(), "2025-01-01")
    expect(rows[0]).toMatchObject({
      userId: 1,
      disposition: "SKIPPED",
      reason: "NO_EMAIL",
    })
  })

  it("EXCLUDED a honorary member", () => {
    const targets = [bulkTarget(1, {isHonorary: true})]
    const rows = computeReminderRows(targets, period(), "2025-01-01")
    expect(rows[0]).toMatchObject({
      userId: 1,
      disposition: "EXCLUDED",
      reason: "HONORARY",
    })
  })

  it("EXCLUDED a member with no membership", () => {
    const targets = [bulkTarget(1, {mostRecentMembership: null})]
    const rows = computeReminderRows(targets, period(), "2025-01-01")
    expect(rows[0]).toMatchObject({
      userId: 1,
      disposition: "EXCLUDED",
      reason: "HONORARY",
    })
  })

  it("sets FULL_YEAR_FEE for a regular member starting before cutoff", () => {
    const targets = [bulkTarget(1, {mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-12-01",
      endDate: null,
      incasso: false,
    }})]
    const rows = computeReminderRows(targets, period(), "2025-01-01")
    expect(rows[0]).toMatchObject({
      recommendedFeeType: "FULL_YEAR_FEE",
      amount: 20.0,
    })
  })

  it("sets HALF_YEAR_FEE for a member starting on or after cutoff", () => {
    const targets = [bulkTarget(1, {mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2025-06-01",
      endDate: null,
      incasso: false,
    }})]
    const rows = computeReminderRows(targets, period(), "2025-06-01")
    expect(rows[0]).toMatchObject({
      recommendedFeeType: "HALF_YEAR_FEE",
      amount: 10.0,
    })
  })

  it("sets ALUMNI_FEE for an alumni member", () => {
    const targets = [bulkTarget(1, {mostRecentMembership: {
      type: MemberType.ALUMNI,
      startDate: "2024-01-01",
      endDate: null,
      incasso: false,
    }})]
    const rows = computeReminderRows(targets, period(), "2025-01-01")
    expect(rows[0]).toMatchObject({
      recommendedFeeType: "ALUMNI_FEE",
      amount: 5.0,
    })
  })

  it("sets WARNING for a member already paid", () => {
    const targets = [bulkTarget(1, {mostRecentContribution: {paid: true}})]
    const rows = computeReminderRows(targets, period(), "2025-01-01")
    expect(rows[0]).toMatchObject({
      disposition: "WARNING",
      reason: "ALREADY_PAID",
    })
  })

  it("returns null amount if period is null", () => {
    const targets = [bulkTarget(1)]
    const rows = computeReminderRows(targets, null, "2025-01-01")
    expect(rows[0]).toMatchObject({
      amount: null,
    })
  })
})

// ── Incasso Notification tests ─────────────────────────────────────────

describe("computeIncassoRows", () => {
  it("WARNING for a member without incasso flag", () => {
    const targets = [bulkTarget(1, {mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
      incasso: false,
    }})]
    const rows = computeIncassoRows(targets, period(), "2025-01-01")
    expect(rows[0]).toMatchObject({
      disposition: "WARNING",
      reason: "INCASSO_MISMATCH",
    })
  })

  it("INCLUDED for a member with incasso flag", () => {
    const targets = [bulkTarget(1, {mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
      incasso: true,
    }})]
    const rows = computeIncassoRows(targets, period(), "2025-01-01")
    expect(rows[0]).toMatchObject({
      disposition: "INCLUDED",
    })
  })

  it("keeps EXCLUDED rows unchanged", () => {
    const targets = [bulkTarget(1, {isHonorary: true})]
    const rows = computeIncassoRows(targets, period(), "2025-01-01")
    expect(rows[0]).toMatchObject({
      disposition: "EXCLUDED",
      reason: "HONORARY",
    })
  })

  it("respects WARNING from already-paid over incasso-mismatch", () => {
    const targets = [bulkTarget(1, {
      mostRecentMembership: {
        type: MemberType.REGULAR,
        startDate: "2024-01-01",
        endDate: null,
        incasso: false,
      },
      mostRecentContribution: {paid: true},
    })]
    const rows = computeIncassoRows(targets, period(), "2025-01-01")
    // already-paid warning comes first from computeReminderRows
    expect(rows[0]).toMatchObject({
      disposition: "WARNING",
      reason: "ALREADY_PAID",
    })
  })
})

// ── End Membership tests ───────────────────────────────────────────────

describe("computeEndMembershipRows", () => {
  it("INCLUDES a member with active membership started before today", () => {
    const targets = [bulkTarget(1)]
    const rows = computeEndMembershipRows(targets, "2025-07-01")
    expect(rows[0]).toMatchObject({
      userId: 1,
      disposition: "INCLUDED",
    })
  })

  it("SKIPS a member with no membership", () => {
    const targets = [bulkTarget(1, {mostRecentMembership: null})]
    const rows = computeEndMembershipRows(targets, "2025-07-01")
    expect(rows[0]).toMatchObject({
      disposition: "SKIPPED",
      reason: "NO_ACTIVE_MEMBERSHIP",
    })
  })

  it("SKIPS a member with ended membership", () => {
    const targets = [bulkTarget(1, {mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: "2024-12-31",
      incasso: false,
    }})]
    const rows = computeEndMembershipRows(targets, "2025-07-01")
    expect(rows[0]).toMatchObject({
      disposition: "SKIPPED",
      reason: "NO_ACTIVE_MEMBERSHIP",
    })
  })

  it("populates memberType and memberSince", () => {
    const targets = [bulkTarget(1, {mostRecentMembership: {
      type: MemberType.ALUMNI,
      startDate: "2023-06-15",
      endDate: null,
      incasso: false,
    }})]
    const rows = computeEndMembershipRows(targets, "2025-07-01")
    expect(rows[0]).toMatchObject({
      memberType: MemberType.ALUMNI,
      memberSince: "2023-06-15",
    })
  })
})

// ── Resume Membership tests ────────────────────────────────────────────

describe("computeResumeMembershipRows", () => {
  it("SKIPS if already active (no endDate)", () => {
    const targets = [bulkTarget(1)]
    const rows = computeResumeMembershipRows(targets, period())
    expect(rows[0]).toMatchObject({
      disposition: "SKIPPED",
      reason: "ALREADY_ACTIVE",
    })
  })

  it("SKIPS if no contribution period", () => {
    const targets = [bulkTarget(1, {mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: "2024-12-31",
      incasso: false,
    }})]
    const rows = computeResumeMembershipRows(targets, null)
    expect(rows[0]).toMatchObject({
      disposition: "SKIPPED",
      reason: "NO_CONTRIBUTION_PERIOD",
    })
  })

  it("WILL_RESUME if endDate is in the latest contribution period", () => {
    const p = period()
    const targets = [bulkTarget(1, {mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: "2025-06-15",
      incasso: false,
    }})]
    const rows = computeResumeMembershipRows(targets, p)
    expect(rows[0]).toMatchObject({
      disposition: "INCLUDED",
      reason: "WILL_RESUME",
    })
  })

  it("WILL_START_NEW if endDate is before the latest period", () => {
    const p = period()
    const targets = [bulkTarget(1, {mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: "2024-12-31",
      incasso: false,
    }})]
    const rows = computeResumeMembershipRows(targets, p)
    expect(rows[0]).toMatchObject({
      disposition: "INCLUDED",
      reason: "WILL_START_NEW",
    })
  })

  it("WILL_START_NEW if no membership at all", () => {
    const targets = [bulkTarget(1, {mostRecentMembership: null})]
    const rows = computeResumeMembershipRows(targets, period())
    expect(rows[0]).toMatchObject({
      disposition: "INCLUDED",
      reason: "WILL_START_NEW",
    })
  })

  it("populates memberType and memberSince when membership exists", () => {
    const p = period()
    const targets = [bulkTarget(1, {mostRecentMembership: {
      type: MemberType.ALUMNI,
      startDate: "2023-01-01",
      endDate: "2024-12-31",
      incasso: false,
    }})]
    const rows = computeResumeMembershipRows(targets, p)
    expect(rows[0]).toMatchObject({
      memberType: MemberType.ALUMNI,
      memberSince: "2023-01-01",
    })
  })
})
