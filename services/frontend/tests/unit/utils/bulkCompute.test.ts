import {describe, expect, it} from "vitest"
import {
  computeMarkPaidRows,
  computeMarkUnpaidRows,
} from "@/utils/bulkCompute"
import {MemberType} from "@/services/api"
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
    highestRole: null,
    ...overrides,
  }
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
        highestRole: null,
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

  it("still populates memberType/memberSince on a SKIPPED (already-paid) row", () => {
    const targets = [
      bulkTarget(1, {
        mostRecentContribution: {paid: true},
        mostRecentMembership: {
          type: MemberType.ALUMNI,
          startDate: "2023-03-04",
          endDate: null,
          incasso: false,
        },
      }),
    ]
    const rows = computeMarkPaidRows(targets)
    expect(rows[0]).toMatchObject({
      disposition: "SKIPPED",
      reason: "ALREADY_PAID",
      memberType: MemberType.ALUMNI,
      memberSince: "2023-03-04",
    })
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
