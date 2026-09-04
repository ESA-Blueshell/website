import {describe, expect, it} from "vitest"
import {MemberType, type MembershipResponse} from "@/services/api"
import {computeBulkTargets, deriveLatestMembership} from "@/utils/bulkTarget"

function membership(overrides: Partial<MembershipResponse> & {userId: number; startDate: string}): MembershipResponse {
  return {
    id: overrides.id ?? 1,
    userId: overrides.userId,
    startDate: overrides.startDate,
    endDate: overrides.endDate ?? null,
    memberType: overrides.memberType ?? MemberType.REGULAR,
    incasso: overrides.incasso ?? false,
    version: 1,
    createdAt: "2025-01-01T00:00:00.000Z",
    updatedAt: "2025-01-01T00:00:00.000Z",
  }
}

function byUser(...ms: MembershipResponse[]): Map<number, MembershipResponse[]> {
  const map = new Map<number, MembershipResponse[]>()
  for (const m of ms) map.set(m.userId, [...(map.get(m.userId) ?? []), m])
  return map
}

describe("deriveLatestMembership", () => {
  it("has no membership to name when the member holds none", () => {
    expect(deriveLatestMembership([])).toBeNull()
  })

  it("names the membership with the latest start whatever order they arrive in", () => {
    const older = membership({id: 1, userId: 7, startDate: "2023-09-01"})
    const newer = membership({id: 2, userId: 7, startDate: "2025-02-01"})

    expect(deriveLatestMembership([newer, older])).toBe(newer)
    expect(deriveLatestMembership([older, newer])).toBe(newer)
  })

  it("keeps the first of two memberships starting on the same day", () => {
    const first = membership({id: 1, userId: 7, startDate: "2025-01-01"})
    const second = membership({id: 2, userId: 7, startDate: "2025-01-01"})

    expect(deriveLatestMembership([first, second])).toBe(first)
  })
})

describe("computeBulkTargets", () => {
  it("judges a returning member against their newest membership while member since keeps the day they first joined", () => {
    const memberships = byUser(
      membership({id: 1, userId: 7, startDate: "2021-09-01", endDate: "2022-08-31"}),
      membership({id: 2, userId: 7, startDate: "2025-01-15", memberType: MemberType.ALUMNI, incasso: true}),
    )

    const [target] = computeBulkTargets([7], memberships, new Set(), new Map([[7, {fullName: "Ada", email: "ada@example.com"}]]))

    expect(target).toEqual({
      userId: 7,
      name: "Ada",
      email: "ada@example.com",
      memberSince: "2021-09-01",
      mostRecentMembership: {
        type: MemberType.ALUMNI,
        startDate: "2025-01-15",
        endDate: null,
        incasso: true,
      },
      mostRecentContribution: {paid: false},
      isHonorary: false,
    })
  })

  it("leaves a member with no membership without one to be judged against", () => {
    const [target] = computeBulkTargets([7], new Map(), new Set(), new Map([[7, {fullName: "Ada", email: "ada@example.com"}]]))

    expect(target?.mostRecentMembership).toBeNull()
    expect(target?.memberSince).toBeNull()
    expect(target?.isHonorary).toBe(false)
  })

  it("falls back to the id as a name and no address when the page never loaded the user", () => {
    const [target] = computeBulkTargets([7], new Map(), new Set(), new Map())

    expect(target?.name).toBe("7")
    expect(target?.email).toBeNull()
  })

  it("reads honorary off the newest membership, so a former honorary member is no longer one", () => {
    const memberships = byUser(
      membership({id: 1, userId: 7, startDate: "2021-09-01", memberType: MemberType.HONORARY, endDate: "2022-08-31"}),
      membership({id: 2, userId: 7, startDate: "2025-01-15", memberType: MemberType.REGULAR}),
      membership({id: 3, userId: 8, startDate: "2025-01-15", memberType: MemberType.HONORARY}),
    )

    const targets = computeBulkTargets([7, 8], memberships, new Set(), new Map())

    expect(targets.map((t) => t.isHonorary)).toEqual([false, true])
  })

  it("marks the members the period was already paid for", () => {
    const targets = computeBulkTargets([7, 8], new Map(), new Set([8]), new Map())

    expect(targets.map((t) => t.mostRecentContribution.paid)).toEqual([false, true])
  })

  it("returns one target per selected id, in the order they were selected", () => {
    const targets = computeBulkTargets([9, 7, 8], new Map(), new Set(), new Map())

    expect(targets.map((t) => t.userId)).toEqual([9, 7, 8])
  })
})
