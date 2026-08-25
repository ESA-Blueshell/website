/**
 * Selecting one member must patch one row.
 *
 * With the table inlined in the page, every row's bindings read the same `selectedIds` set,
 * so one toggle replaced that set and re-rendered all of them. Now each row is its own
 * component: only the toggled row's `selected` prop changes, and Vue skips the rest.
 *
 * Each row exposes `updateCount`, incremented in `onUpdated`. The test snapshots those
 * counters, toggles one row, and asserts the neighbours' counters did not move. The
 * pre-selection step matters: the very first toggle of an empty selection is a legitimate
 * change for every row, because it is what turns the header checkbox indeterminate.
 */

import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {nextTick} from "vue"
import UserManager from "@/pages/management/UserManager.vue"
import UserManagerRow from "@/components/common/rows/UserManagerRow.vue"
import {MemberType} from "@/services/api"

const {
  mockFindUsers,
  mockFindMemberships,
  mockFindUserById,
  mockFindContributionsByPeriodId,
  mockDeleteUserById,
  mockLgAndUp,
} = vi.hoisted(() => ({
  mockFindUsers: vi.fn(),
  mockFindMemberships: vi.fn(),
  mockFindUserById: vi.fn(),
  mockFindContributionsByPeriodId: vi.fn(),
  mockDeleteUserById: vi.fn(),
  // Shaped as a ref so the template unwraps it and v-if="lgAndUp" sees the boolean.
  mockLgAndUp: {value: true, __v_isRef: true} as {value: boolean; __v_isRef: true},
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
    findMemberships: mockFindMemberships,
    findUserById: mockFindUserById,
    findContributionsByPeriodId: mockFindContributionsByPeriodId,
    deleteUserById: mockDeleteUserById,
  }
})

vi.mock("@/components/common/lists/ContributionPeriodList.vue", () => ({
  default: {name: "ContributionPeriodList", template: "<div />"},
}))
vi.mock("@/components/common/banners/TopBanner.vue", () => ({
  default: {name: "TopBanner", template: "<div />"},
}))
vi.mock("@/components/common/modals/DeletionConfirmationDialog.vue", () => ({
  default: {name: "DeletionConfirmationDialog", template: "<div />"},
}))
vi.mock("@/components/common/modals/ManageMembershipDialog.vue", () => ({
  default: {name: "ManageMembershipDialog", template: "<div />"},
}))
vi.mock("@/components/common/modals/bulk/PaidStatusDialog.vue", () => ({
  default: {name: "PaidStatusDialog", template: "<div />"},
}))
vi.mock("@/components/common/modals/BaseModal.vue", () => ({
  default: {name: "BaseModal", template: "<div><slot /></div>"},
}))
vi.mock("@/components/form/UserForm.vue", () => ({
  default: {name: "UserForm", template: "<div />"},
}))

function makeUser(id: number, fullName: string, username: string) {
  return {
    id,
    fullName,
    username,
    roles: ["MEMBER"],
    email: `${username}@test.com`,
    enabled: true,
    firstName: fullName.split(" ")[0] ?? "",
    lastName: fullName.split(" ")[1] ?? "",
    initials: "XX",
    newsletter: false,
    photoConsent: false,
    createdAt: "2025-01-01T00:00:00.000Z",
    updatedAt: "2025-01-01T00:00:00.000Z",
    version: 0,
  }
}

function makeMembership(id: number, userId: number) {
  return {
    id,
    userId,
    startDate: "2024-01-01",
    endDate: undefined,
    memberType: MemberType.REGULAR,
    incasso: false,
    version: 1,
    createdAt: "2025-01-01T00:00:00.000Z",
    updatedAt: "2025-01-01T00:00:00.000Z",
  }
}

const rowId = (row: {props: () => unknown}) => (row.props() as {row: {id: number}}).row.id
const updateCount = (row: {vm: unknown}) => (row.vm as {updateCount: number}).updateCount

describe("UserManager row isolation", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindUsers.mockResolvedValue({
      status: 200,
      data: {
        content: [
          makeUser(1, "Alice Smith", "alice"),
          makeUser(2, "Bob Jones", "bob"),
          makeUser(3, "Carol White", "carol"),
        ],
      },
    })
    mockFindMemberships.mockResolvedValue({
      data: [makeMembership(10, 1), makeMembership(11, 2), makeMembership(12, 3)],
    })
    mockFindContributionsByPeriodId.mockResolvedValue({data: []})
    mockFindUserById.mockResolvedValue({data: makeUser(1, "Alice Smith", "alice")})
    mockDeleteUserById.mockResolvedValue({})
  })

  it("selecting a member leaves the other rows untouched", async () => {
    const wrapper = mount(UserManager)
    await flushPromises()
    await nextTick()

    const rows = wrapper.findAllComponents(UserManagerRow)
    expect(rows).toHaveLength(3)

    // Carol goes first, so the selection is already non-empty before the measured toggle.
    rows.find((row) => rowId(row) === 3)!.vm.$emit("toggle-selection", 3)
    await nextTick()
    await nextTick()

    const before = rows.map((row) => ({id: rowId(row), count: updateCount(row)}))

    rows.find((row) => rowId(row) === 1)!.vm.$emit("toggle-selection", 1)
    await nextTick()

    const after = new Map(rows.map((row) => [rowId(row), updateCount(row)]))

    expect(after.get(1)!).toBeGreaterThan(before.find((row) => row.id === 1)!.count)
    for (const {id, count} of before.filter((row) => row.id !== 1)) {
      expect(after.get(id)!).toBe(count)
    }
  })
})
