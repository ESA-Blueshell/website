import {describe, expect, it} from "vitest"
import {computeEndMembershipRows} from "@/utils/bulkCompute"
import type {MembershipResponse} from "@/services/api"

// Minimal membership factory — only the fields computeEndMembershipRows reads.
function m(userId: number, startDate: string, endDate?: string): MembershipResponse {
  return {
    id: userId * 10,
    userId,
    startDate,
    endDate,
    memberType: "REGULAR",
    incasso: false,
    createdAt: "2020-01-01T00:00:00Z",
    updatedAt: "2020-01-01T00:00:00Z",
    version: 0,
  } as MembershipResponse
}

function byUser(...ms: MembershipResponse[]): Map<number, MembershipResponse[]> {
  const map = new Map<number, MembershipResponse[]>()
  for (const membership of ms) {
    const list = map.get(membership.userId)
    if (list) list.push(membership)
    else map.set(membership.userId, [membership])
  }
  return map
}

const names = {1: "Alice", 2: "Bob", 3: "Carol"}

describe("computeEndMembershipRows", () => {
  it("INCLUDES a user whose active membership started before serverToday", () => {
    const rows = computeEndMembershipRows([1], byUser(m(1, "2024-01-01")), names, "2025-07-01")
    expect(rows[0]).toMatchObject({userId: 1, disposition: "INCLUDED", reason: undefined})
  })

  it("SKIPS (STARTED_TODAY) a user whose only active membership started exactly on serverToday", () => {
    // The boundary is strict: startDate < serverToday. Equal → not endable.
    const rows = computeEndMembershipRows([1], byUser(m(1, "2025-07-01")), names, "2025-07-01")
    expect(rows[0]).toMatchObject({userId: 1, disposition: "SKIPPED", reason: "STARTED_TODAY"})
  })

  it("SKIPS (NO_ACTIVE_MEMBERSHIP) a user with only ended memberships", () => {
    const rows = computeEndMembershipRows([1], byUser(m(1, "2024-01-01", "2024-12-31")), names, "2025-07-01")
    expect(rows[0]).toMatchObject({userId: 1, disposition: "SKIPPED", reason: "NO_ACTIVE_MEMBERSHIP"})
  })

  it("SKIPS (NO_ACTIVE_MEMBERSHIP) a user with no memberships at all", () => {
    const rows = computeEndMembershipRows([2], byUser(), names, "2025-07-01")
    expect(rows[0]).toMatchObject({userId: 2, disposition: "SKIPPED", reason: "NO_ACTIVE_MEMBERSHIP"})
  })

  it("uses serverToday, not the browser date, for the boundary", () => {
    // A future serverToday makes an otherwise-'today' start endable — proving the
    // decision keys off the passed date rather than new Date().
    const rows = computeEndMembershipRows([1], byUser(m(1, "2025-07-01")), names, "2025-07-02")
    expect(rows[0]).toMatchObject({userId: 1, disposition: "INCLUDED"})
  })

  it("populates name / memberType / memberSince from the first active membership", () => {
    const rows = computeEndMembershipRows([3], byUser(m(3, "2024-03-04")), names, "2025-01-01")
    expect(rows[0]).toMatchObject({name: "Carol", memberType: "REGULAR", memberSince: "2024-03-04"})
  })
})
