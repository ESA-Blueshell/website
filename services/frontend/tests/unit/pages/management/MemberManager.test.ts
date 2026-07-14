import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import MemberManager, {type MemberRow} from "@/pages/management/MemberManager.vue"
import {MemberType} from "@/services/api"
import {settle} from "../helpers"

const {
  mockFindUsers,
  mockFindUserById,
  mockFindMemberships,
  mockFindContributionsByPeriodId,
  mockDeleteUserById,
  mockLgAndUp,
} = vi.hoisted(() => ({
  mockFindUsers: vi.fn(),
  mockFindUserById: vi.fn(),
  mockFindMemberships: vi.fn(),
  mockFindContributionsByPeriodId: vi.fn(),
  mockDeleteUserById: vi.fn(),
  mockLgAndUp: {value: true},
}))

vi.mock("vuetify", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vuetify")>()
  return {
    ...(actual as Record<string, unknown>),
    useDisplay: () => ({lgAndUp: mockLgAndUp}),
  }
})

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {
    ...actual,
    findUsers: mockFindUsers,
    findUserById: mockFindUserById,
    findMemberships: mockFindMemberships,
    findContributionsByPeriodId: mockFindContributionsByPeriodId,
    deleteUserById: mockDeleteUserById,
  }
})

vi.mock("@/components/common/lists/ContributionPeriodList.vue", () => ({
  default: {
    name: "ContributionPeriodList",
    template: "<div />",
  },
}))

vi.mock("@/components/common/banners/TopBanner.vue", () => ({
  default: {
    name: "TopBanner",
    template: "<div />",
  },
}))

vi.mock("@/components/common/modals/DeletionConfirmationDialog.vue", () => ({
  default: {
    name: "DeletionConfirmationDialog",
    template: "<div />",
  },
}))

vi.mock("@/components/common/modals/ManageMembershipDialog.vue", () => ({
  default: {
    name: "ManageMembershipDialog",
    template: "<div />",
  },
}))

vi.mock("@/components/form/UserForm.vue", () => ({
  default: {
    name: "UserForm",
    template: "<div />",
  },
}))

/** Complete MembershipResponse mock factory */
function makeMembership(overrides: {
  id: number
  userId: number
  startDate: string
  endDate?: string
  memberType?: MemberType
  incasso?: boolean
}): import("@/services/api").MembershipResponse {
  return {
    id: overrides.id,
    userId: overrides.userId,
    startDate: overrides.startDate,
    endDate: overrides.endDate,
    memberType: overrides.memberType ?? MemberType.REGULAR,
    incasso: overrides.incasso ?? false,
    version: 1,
    createdAt: "2025-01-01T00:00:00.000Z",
    updatedAt: "2025-01-01T00:00:00.000Z",
  }
}

describe("MemberManager page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindUsers.mockResolvedValue({
      status: 200,
      data: {
        content: [
          {id: 1, fullName: "Alice Smith", username: "alice", roles: ["MEMBER"], email: "alice@test.com", enabled: true, firstName: "Alice", lastName: "Smith", initials: "AS", newsletter: false, photoConsent: false, createdAt: "2025-01-01T00:00:00.000Z", updatedAt: "2025-01-01T00:00:00.000Z", version: 0},
          {id: 2, fullName: "Bob Jones", username: "bob", roles: ["USER"], email: "bob@test.com", enabled: true, firstName: "Bob", lastName: "Jones", initials: "BJ", newsletter: false, photoConsent: false, createdAt: "2025-01-01T00:00:00.000Z", updatedAt: "2025-01-01T00:00:00.000Z", version: 0},
        ],
      },
    })
    mockFindUserById.mockResolvedValue({
      data: {id: 1, fullName: "Alice Smith", username: "alice", roles: ["MEMBER"], email: "alice@test.com", enabled: true, firstName: "Alice", lastName: "Smith", initials: "AS", newsletter: false, photoConsent: false, createdAt: "2025-01-01T00:00:00.000Z", updatedAt: "2025-01-01T00:00:00.000Z", version: 0},
    })
    mockFindMemberships.mockResolvedValue({
      data: [
        makeMembership({id: 90, userId: 1, startDate: "2024-01-01"}),
      ],
    })
    mockFindContributionsByPeriodId.mockResolvedValue({data: [{id: 91, userId: 1, contributionPeriodId: 8}]})
    mockDeleteUserById.mockResolvedValue({})
  })

  it("fetches all users and memberships on mount", async () => {
    shallowMount(MemberManager)
    await settle()
    expect(mockFindUsers).toHaveBeenCalled()
    expect(mockFindMemberships).toHaveBeenCalledWith()
  })

  it("does NOT call findMemberships with period query on mount", async () => {
    shallowMount(MemberManager)
    await settle()
    // Must be called with no arguments (empty query = all memberships)
    expect(mockFindMemberships).toHaveBeenCalledWith()
    const calls = mockFindMemberships.mock.calls
    expect(calls.every((c: unknown[]) => c.length === 0)).toBe(true)
  })

  it("upserts users and refreshes user after membership change", async () => {
    const wrapper = shallowMount(MemberManager)
    await settle()

    ;(wrapper.vm as any).updateUser({id: 1, username: "alice-updated", fullName: "Alice Updated", roles: ["MEMBER"]})
    expect((wrapper.vm as any).users).toHaveLength(2)
    expect((wrapper.vm as any).users[0].username).toBe("alice-updated")

    ;(wrapper.vm as any).manageUserId = 1
    await (wrapper.vm as any).onMembershipChanged()
    expect(mockFindMemberships).toHaveBeenCalled()
    expect(mockFindUserById).toHaveBeenCalledWith({path: {userId: 1}})
  })

  it("openAddUser sets addDialog true", async () => {
    const wrapper = shallowMount(MemberManager)
    await settle()

    ;(wrapper.vm as any).openAddUser()
    expect((wrapper.vm as any).addDialog).toBe(true)
  })

  it("openEditProfile calls findUserById and opens edit dialog", async () => {
    const wrapper = shallowMount(MemberManager)
    await settle()

    const row = (wrapper.vm as any).rows[0]
    await (wrapper.vm as any).openEditProfile(row)
    expect(mockFindUserById).toHaveBeenCalledWith({path: {userId: row.id}})
    expect((wrapper.vm as any).editDialog).toBe(true)
  })

  it("openManageMembership opens manage dialog with correct userId", async () => {
    const wrapper = shallowMount(MemberManager)
    await settle()

    const row = (wrapper.vm as any).rows[0]
    ;(wrapper.vm as any).openManageMembership(row)
    expect((wrapper.vm as any).manageDialog).toBe(true)
    expect((wrapper.vm as any).manageUserId).toBe(row.id)
  })

  it("resets paidUserIds and fetches contributions on period change", async () => {
    const wrapper = shallowMount(MemberManager)
    await settle()

    await (wrapper.vm as any).contributionPeriodChanged({id: 8, startDate: "2026-01-01", endDate: "2026-12-31"})
    expect(mockFindContributionsByPeriodId).toHaveBeenCalledWith({path: {periodId: 8}})
    expect((wrapper.vm as any).paidUserIds.has(1)).toBe(true)

    // Period change should reset paidUserIds before re-populating
    mockFindContributionsByPeriodId.mockResolvedValue({data: []})
    await (wrapper.vm as any).contributionPeriodChanged({id: 9, startDate: "2027-01-01", endDate: "2027-12-31"})
    expect((wrapper.vm as any).paidUserIds.has(1)).toBe(false)
  })

  it("clears paidUserIds when period is undefined", async () => {
    const wrapper = shallowMount(MemberManager)
    await settle()

    await (wrapper.vm as any).contributionPeriodChanged({id: 8, startDate: "2026-01-01", endDate: "2026-12-31"})
    expect((wrapper.vm as any).paidUserIds.size).toBeGreaterThan(0)

    await (wrapper.vm as any).contributionPeriodChanged(undefined)
    expect((wrapper.vm as any).paidUserIds.size).toBe(0)
  })
})

describe("MemberManager row model", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockDeleteUserById.mockResolvedValue({})
    mockFindContributionsByPeriodId.mockResolvedValue({data: []})
    mockFindUserById.mockResolvedValue({data: {id: 1, username: "u", roles: []}})
  })

  function mountWithData(
    userData: Array<{id: number; fullName: string; username: string; roles: string[]}>,
    membershipData: ReturnType<typeof makeMembership>[],
    paidIds: number[] = [],
  ) {
    mockFindUsers.mockResolvedValue({
      status: 200,
      data: {
        content: userData.map((u) => ({
          ...u,
          email: `${u.username}@test.com`,
          enabled: true,
          firstName: u.fullName.split(" ")[0] ?? "",
          lastName: u.fullName.split(" ")[1] ?? "",
          initials: "XX",
          newsletter: false,
          photoConsent: false,
          createdAt: "2025-01-01T00:00:00.000Z",
          updatedAt: "2025-01-01T00:00:00.000Z",
          version: 0,
        })),
      },
    })
    mockFindMemberships.mockResolvedValue({data: membershipData})
    mockFindContributionsByPeriodId.mockResolvedValue({
      data: paidIds.map((uid, idx) => ({id: 900 + idx, userId: uid, contributionPeriodId: 1})),
    })
    return shallowMount(MemberManager)
  }

  it("derives status: Current when user has active membership (endDate null)", async () => {
    const wrapper = mountWithData(
      [{id: 1, fullName: "Active User", username: "active", roles: ["MEMBER"]}],
      [makeMembership({id: 10, userId: 1, startDate: "2024-01-01"})], // no endDate
    )
    await settle()
    const rows: MemberRow[] = (wrapper.vm as any).rows
    expect(rows[0].status).toBe("Current")
  })

  it("derives status: Former when user has membership(s) all ended", async () => {
    const wrapper = mountWithData(
      [{id: 2, fullName: "Former User", username: "former", roles: ["USER"]}],
      [makeMembership({id: 20, userId: 2, startDate: "2022-01-01", endDate: "2023-01-01"})],
    )
    await settle()
    const rows: MemberRow[] = (wrapper.vm as any).rows
    expect(rows[0].status).toBe("Former")
  })

  it("derives status: Never when user has no memberships", async () => {
    const wrapper = mountWithData(
      [{id: 3, fullName: "Never User", username: "never", roles: ["USER"]}],
      [],
    )
    await settle()
    const rows: MemberRow[] = (wrapper.vm as any).rows
    expect(rows[0].status).toBe("Never")
  })

  it("derives status: Current when ANY membership is active (not just latest)", async () => {
    // User has two memberships: one ended, one active
    const wrapper = mountWithData(
      [{id: 4, fullName: "Multi User", username: "multi", roles: ["MEMBER"]}],
      [
        makeMembership({id: 30, userId: 4, startDate: "2020-01-01", endDate: "2021-01-01"}),
        makeMembership({id: 31, userId: 4, startDate: "2023-06-01"}), // active
      ],
    )
    await settle()
    const rows: MemberRow[] = (wrapper.vm as any).rows
    expect(rows[0].status).toBe("Current")
  })

  it("derives memberSince as min(startDate) across user's memberships", async () => {
    const wrapper = mountWithData(
      [{id: 5, fullName: "Long User", username: "long", roles: ["MEMBER"]}],
      [
        makeMembership({id: 40, userId: 5, startDate: "2022-06-01"}),
        makeMembership({id: 41, userId: 5, startDate: "2020-01-01"}), // earliest
        makeMembership({id: 42, userId: 5, startDate: "2024-01-01"}),
      ],
    )
    await settle()
    const rows: MemberRow[] = (wrapper.vm as any).rows
    expect(rows[0].memberSince).toBe("2020-01-01")
  })

  it("memberSince is null for Never users (no memberships)", async () => {
    const wrapper = mountWithData(
      [{id: 6, fullName: "No Membership", username: "none", roles: ["USER"]}],
      [],
    )
    await settle()
    const rows: MemberRow[] = (wrapper.vm as any).rows
    expect(rows[0].memberSince).toBeNull()
  })

  it("derives latestType and incasso from latest membership (max startDate)", async () => {
    const wrapper = mountWithData(
      [{id: 7, fullName: "Type User", username: "typeuser", roles: ["MEMBER"]}],
      [
        makeMembership({id: 50, userId: 7, startDate: "2020-01-01", memberType: MemberType.REGULAR, incasso: false}),
        makeMembership({id: 51, userId: 7, startDate: "2024-01-01", memberType: MemberType.HONORARY, incasso: true}), // latest
      ],
    )
    await settle()
    const rows: MemberRow[] = (wrapper.vm as any).rows
    expect(rows[0].latestType).toBe(MemberType.HONORARY)
    expect(rows[0].latestIncasso).toBe(true)
  })

  it("marks user as paid if their id is in paidUserIds", async () => {
    const wrapper = mountWithData(
      [
        {id: 8, fullName: "Paid User", username: "paid", roles: ["MEMBER"]},
        {id: 9, fullName: "Unpaid User", username: "unpaid", roles: ["MEMBER"]},
      ],
      [
        makeMembership({id: 60, userId: 8, startDate: "2024-01-01"}),
        makeMembership({id: 61, userId: 9, startDate: "2024-01-01"}),
      ],
      [8], // only user 8 is paid
    )
    await settle()
    // Trigger a period change to populate paidUserIds
    await (wrapper.vm as any).contributionPeriodChanged({id: 1, startDate: "2025-01-01", endDate: "2025-12-31"})

    const rows: MemberRow[] = (wrapper.vm as any).rows
    const paidRow = rows.find((r) => r.id === 8)
    const unpaidRow = rows.find((r) => r.id === 9)
    expect(paidRow?.paid).toBe(true)
    expect(unpaidRow?.paid).toBe(false)
  })

  it("search filters across user fields", async () => {
    const wrapper = mountWithData(
      [
        {id: 10, fullName: "Search Alpha", username: "salpha", roles: ["MEMBER"]},
        {id: 11, fullName: "Search Beta", username: "sbeta", roles: ["USER"]},
      ],
      [],
    )
    await settle()

    ;(wrapper.vm as any).search = "salpha"
    await settle()
    const rows: MemberRow[] = (wrapper.vm as any).filteredRows
    expect(rows).toHaveLength(1)
    expect(rows[0].username).toBe("salpha")
  })

  it("sorts by name (default, ascending)", async () => {
    const wrapper = mountWithData(
      [
        {id: 20, fullName: "Zoe Last", username: "zlast", roles: ["MEMBER"]},
        {id: 21, fullName: "Anna First", username: "afirst", roles: ["MEMBER"]},
      ],
      [],
    )
    await settle()

    const rows: MemberRow[] = (wrapper.vm as any).filteredRows
    expect(rows[0].fullName).toBe("Anna First")
    expect(rows[1].fullName).toBe("Zoe Last")
  })

  it("sorts by status: Current before Former before Never", async () => {
    const wrapper = mountWithData(
      [
        {id: 30, fullName: "Never User", username: "nv", roles: ["USER"]},
        {id: 31, fullName: "Former User", username: "fm", roles: ["USER"]},
        {id: 32, fullName: "Current User", username: "cu", roles: ["MEMBER"]},
      ],
      [
        makeMembership({id: 70, userId: 31, startDate: "2020-01-01", endDate: "2021-01-01"}),
        makeMembership({id: 71, userId: 32, startDate: "2023-01-01"}),
      ],
    )
    await settle()
    ;(wrapper.vm as any).sortKey = "status"
    ;(wrapper.vm as any).sortAsc = true
    await settle()

    const rows: MemberRow[] = (wrapper.vm as any).filteredRows
    expect(rows[0].status).toBe("Current")
    expect(rows[1].status).toBe("Former")
    expect(rows[2].status).toBe("Never")
  })

  it("sorts by memberSince ascending", async () => {
    const wrapper = mountWithData(
      [
        {id: 40, fullName: "Later User", username: "later", roles: ["MEMBER"]},
        {id: 41, fullName: "Earlier User", username: "earlier", roles: ["MEMBER"]},
      ],
      [
        makeMembership({id: 80, userId: 40, startDate: "2024-06-01"}),
        makeMembership({id: 81, userId: 41, startDate: "2020-01-01"}),
      ],
    )
    await settle()
    ;(wrapper.vm as any).sortKey = "memberSince"
    ;(wrapper.vm as any).sortAsc = true
    await settle()

    const rows: MemberRow[] = (wrapper.vm as any).filteredRows
    expect(rows[0].username).toBe("earlier")
    expect(rows[1].username).toBe("later")
  })

  it("notable-only type icons: HONORARY and ALUMNI are notable, REGULAR is not", async () => {
    const wrapper = mountWithData(
      [
        {id: 50, fullName: "Honorary", username: "hon", roles: ["MEMBER"]},
        {id: 51, fullName: "Alumni", username: "alum", roles: ["MEMBER"]},
        {id: 52, fullName: "Regular", username: "reg", roles: ["MEMBER"]},
      ],
      [
        makeMembership({id: 90, userId: 50, startDate: "2024-01-01", memberType: MemberType.HONORARY}),
        makeMembership({id: 91, userId: 51, startDate: "2024-01-01", memberType: MemberType.ALUMNI}),
        makeMembership({id: 92, userId: 52, startDate: "2024-01-01", memberType: MemberType.REGULAR}),
      ],
    )
    await settle()

    const rows: MemberRow[] = (wrapper.vm as any).rows
    const honRow = rows.find((r) => r.id === 50)!
    const alumRow = rows.find((r) => r.id === 51)!
    const regRow = rows.find((r) => r.id === 52)!

    const isNotableType = (wrapper.vm as any).isNotableType
    expect(isNotableType(honRow)).toBe(true)
    expect(isNotableType(alumRow)).toBe(true)
    expect(isNotableType(regRow)).toBe(false)

    expect((wrapper.vm as any).typeIcon(honRow)).toBe("mdi-crown")
    expect((wrapper.vm as any).typeIcon(alumRow)).toBe("mdi-school")
    expect((wrapper.vm as any).typeIcon(regRow)).toBe("")
  })

  it("incasso icon is notable only when incasso=true", async () => {
    const wrapper = mountWithData(
      [
        {id: 60, fullName: "Incasso User", username: "incasso", roles: ["MEMBER"]},
        {id: 61, fullName: "No Incasso User", username: "noincasso", roles: ["MEMBER"]},
      ],
      [
        makeMembership({id: 100, userId: 60, startDate: "2024-01-01", incasso: true}),
        makeMembership({id: 101, userId: 61, startDate: "2024-01-01", incasso: false}),
      ],
    )
    await settle()

    const rows: MemberRow[] = (wrapper.vm as any).rows
    const incassoRow = rows.find((r) => r.id === 60)!
    const noIncassoRow = rows.find((r) => r.id === 61)!
    expect(incassoRow.latestIncasso).toBe(true)
    expect(noIncassoRow.latestIncasso).toBe(false)
  })

  it("openDeleteUser sets pendingDeleteUser and opens dialog", async () => {
    const wrapper = mountWithData(
      [{id: 70, fullName: "Delete Me", username: "deleteme", roles: ["USER"]}],
      [],
    )
    await settle()

    const user = (wrapper.vm as any).users[0]
    ;(wrapper.vm as any).openDeleteUser(user)
    expect((wrapper.vm as any).deleteDialog).toBe(true)
    expect((wrapper.vm as any).pendingDeleteUser?.id).toBe(70)
  })

  it("confirmDeleteUser calls deleteUserById and removes user from list", async () => {
    const wrapper = mountWithData(
      [{id: 71, fullName: "To Delete", username: "todelete", roles: ["USER"]}],
      [],
    )
    await settle()

    const user = (wrapper.vm as any).users[0]
    ;(wrapper.vm as any).openDeleteUser(user)
    await (wrapper.vm as any).confirmDeleteUser()
    expect(mockDeleteUserById).toHaveBeenCalledWith({path: {userId: 71}})
    expect((wrapper.vm as any).users).toHaveLength(0)
  })
})

describe("MemberManager filters", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockDeleteUserById.mockResolvedValue({})
    mockFindContributionsByPeriodId.mockResolvedValue({data: []})
    mockFindUserById.mockResolvedValue({data: {id: 1, username: "u", roles: []}})
  })

  function mountWithFilterData() {
    mockFindUsers.mockResolvedValue({
      status: 200,
      data: {
        content: [
          {id: 1, fullName: "Current Paid Incasso", username: "cpi", roles: ["MEMBER"], email: "a@test.com", enabled: true, firstName: "Current", lastName: "Paid", initials: "CP", newsletter: false, photoConsent: false, createdAt: "2025-01-01T00:00:00.000Z", updatedAt: "2025-01-01T00:00:00.000Z", version: 0},
          {id: 2, fullName: "Former Unpaid NoIncasso", username: "fun", roles: ["USER"], email: "b@test.com", enabled: true, firstName: "Former", lastName: "Unpaid", initials: "FU", newsletter: false, photoConsent: false, createdAt: "2025-01-01T00:00:00.000Z", updatedAt: "2025-01-01T00:00:00.000Z", version: 0},
          {id: 3, fullName: "Never Unpaid NoIncasso", username: "nun", roles: ["USER"], email: "c@test.com", enabled: true, firstName: "Never", lastName: "Unpaid", initials: "NU", newsletter: false, photoConsent: false, createdAt: "2025-01-01T00:00:00.000Z", updatedAt: "2025-01-01T00:00:00.000Z", version: 0},
        ],
      },
    })
    mockFindMemberships.mockResolvedValue({
      data: [
        // user 1: active membership with incasso
        makeMembership({id: 10, userId: 1, startDate: "2024-01-01", incasso: true}),
        // user 2: ended membership, no incasso
        makeMembership({id: 20, userId: 2, startDate: "2022-01-01", endDate: "2023-01-01", incasso: false}),
        // user 3: no memberships (handled by empty filter)
      ],
    })
    return shallowMount(MemberManager)
  }

  it("memberFilter=yes shows only Current members", async () => {
    const wrapper = mountWithFilterData()
    await settle()
    ;(wrapper.vm as any).memberFilter = "yes"
    await settle()
    const rows: MemberRow[] = (wrapper.vm as any).filteredRows
    expect(rows.every((r) => r.status === "Current")).toBe(true)
    expect(rows.find((r) => r.id === 1)).toBeTruthy()
    expect(rows.find((r) => r.id === 2)).toBeFalsy()
    expect(rows.find((r) => r.id === 3)).toBeFalsy()
  })

  it("memberFilter=no shows only non-Current members", async () => {
    const wrapper = mountWithFilterData()
    await settle()
    ;(wrapper.vm as any).memberFilter = "no"
    await settle()
    const rows: MemberRow[] = (wrapper.vm as any).filteredRows
    expect(rows.every((r) => r.status !== "Current")).toBe(true)
    expect(rows.find((r) => r.id === 1)).toBeFalsy()
  })

  it("paidFilter=yes shows only paid users after period change", async () => {
    mockFindContributionsByPeriodId.mockResolvedValue({
      data: [{id: 91, userId: 1, contributionPeriodId: 5}],
    })
    const wrapper = mountWithFilterData()
    await settle()
    await (wrapper.vm as any).contributionPeriodChanged({id: 5, startDate: "2025-01-01", endDate: "2025-12-31"})
    ;(wrapper.vm as any).paidFilter = "yes"
    await settle()
    const rows: MemberRow[] = (wrapper.vm as any).filteredRows
    expect(rows.every((r) => r.paid)).toBe(true)
    expect(rows.find((r) => r.id === 1)).toBeTruthy()
    expect(rows.find((r) => r.id === 2)).toBeFalsy()
  })

  it("paidFilter=no shows only unpaid users", async () => {
    mockFindContributionsByPeriodId.mockResolvedValue({
      data: [{id: 91, userId: 1, contributionPeriodId: 5}],
    })
    const wrapper = mountWithFilterData()
    await settle()
    await (wrapper.vm as any).contributionPeriodChanged({id: 5, startDate: "2025-01-01", endDate: "2025-12-31"})
    ;(wrapper.vm as any).paidFilter = "no"
    await settle()
    const rows: MemberRow[] = (wrapper.vm as any).filteredRows
    expect(rows.every((r) => !r.paid)).toBe(true)
    expect(rows.find((r) => r.id === 1)).toBeFalsy()
  })

  it("incassoFilter=yes shows only users with incasso", async () => {
    const wrapper = mountWithFilterData()
    await settle()
    ;(wrapper.vm as any).incassoFilter = "yes"
    await settle()
    const rows: MemberRow[] = (wrapper.vm as any).filteredRows
    expect(rows.every((r) => r.latestIncasso)).toBe(true)
    expect(rows.find((r) => r.id === 1)).toBeTruthy()
    expect(rows.find((r) => r.id === 2)).toBeFalsy()
  })

  it("incassoFilter=no shows only users without incasso", async () => {
    const wrapper = mountWithFilterData()
    await settle()
    ;(wrapper.vm as any).incassoFilter = "no"
    await settle()
    const rows: MemberRow[] = (wrapper.vm as any).filteredRows
    expect(rows.every((r) => !r.latestIncasso)).toBe(true)
    expect(rows.find((r) => r.id === 1)).toBeFalsy()
  })

  it("combined search + memberFilter narrows results", async () => {
    const wrapper = mountWithFilterData()
    await settle()
    ;(wrapper.vm as any).search = "current"
    ;(wrapper.vm as any).memberFilter = "yes"
    await settle()
    const rows: MemberRow[] = (wrapper.vm as any).filteredRows
    // Only "Current Paid Incasso" (id=1) matches both
    expect(rows).toHaveLength(1)
    expect(rows[0].id).toBe(1)
  })

  it("all filters default to 'all' so existing tests are unaffected", async () => {
    const wrapper = mountWithFilterData()
    await settle()
    expect((wrapper.vm as any).memberFilter).toBe("all")
    expect((wrapper.vm as any).paidFilter).toBe("all")
    expect((wrapper.vm as any).incassoFilter).toBe("all")
    // filteredRows includes all 3 users
    expect((wrapper.vm as any).filteredRows).toHaveLength(3)
  })
})
