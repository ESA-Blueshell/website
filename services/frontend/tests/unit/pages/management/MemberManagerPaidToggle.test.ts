import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import MemberManager from "@/pages/management/MemberManager.vue"
import {MemberType} from "@/services/api"
import {settle} from "../helpers"

const {
  mockFindUsers,
  mockFindUserById,
  mockFindMemberships,
  mockFindContributionsByPeriodId,
  mockDeleteUserById,
  mockCreateContribution,
  mockDeleteContribution,
  mockLgAndUp,
} = vi.hoisted(() => ({
  mockFindUsers: vi.fn(),
  mockFindUserById: vi.fn(),
  mockFindMemberships: vi.fn(),
  mockFindContributionsByPeriodId: vi.fn(),
  mockDeleteUserById: vi.fn(),
  mockCreateContribution: vi.fn(),
  mockDeleteContribution: vi.fn(),
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
    createContribution: mockCreateContribution,
    deleteContribution: mockDeleteContribution,
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

vi.mock("@/components/form/UserForm.vue", () => ({
  default: {name: "UserForm", template: "<div />"},
}))

describe("MemberManager paid toggle", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindUsers.mockResolvedValue({
      status: 200,
      data: {
        content: [
          {id: 1, fullName: "Alice Smith", username: "alice", roles: ["MEMBER"], email: "alice@test.com", enabled: true, firstName: "Alice", lastName: "Smith", initials: "AS", newsletter: false, photoConsent: false, createdAt: "2025-01-01T00:00:00.000Z", updatedAt: "2025-01-01T00:00:00.000Z", version: 0},
        ],
      },
    })
    mockFindUserById.mockResolvedValue({
      data: {id: 1, fullName: "Alice Smith", username: "alice", roles: ["MEMBER"], email: "alice@test.com", enabled: true, firstName: "Alice", lastName: "Smith", initials: "AS", newsletter: false, photoConsent: false, createdAt: "2025-01-01T00:00:00.000Z", updatedAt: "2025-01-01T00:00:00.000Z", version: 0},
    })
    mockFindMemberships.mockResolvedValue({
      data: [{
        id: 90, userId: 1, startDate: "2024-01-01",
        memberType: MemberType.REGULAR, incasso: false,
        version: 1, createdAt: "2025-01-01T00:00:00.000Z", updatedAt: "2025-01-01T00:00:00.000Z",
      }],
    })
    mockFindContributionsByPeriodId.mockResolvedValue({data: []})
    mockDeleteUserById.mockResolvedValue({})
    mockCreateContribution.mockResolvedValue({data: {userId: 1, contributionPeriodId: 5, version: 1, createdAt: "", updatedAt: ""}})
    mockDeleteContribution.mockResolvedValue({})
  })

  it("togglePaid is disabled when no period selected (isDisabled=true)", async () => {
    const wrapper = shallowMount(MemberManager)
    await settle()

    // No period selected → isDisabled should be true
    expect((wrapper.vm as any).toggleDisabled).toBe(true)
  })

  it("togglePaid becomes enabled after period is selected", async () => {
    const wrapper = shallowMount(MemberManager)
    await settle()

    await (wrapper.vm as any).contributionPeriodChanged({id: 5, startDate: "2025-01-01", endDate: "2025-12-31"})
    expect((wrapper.vm as any).toggleDisabled).toBe(false)
  })

  it("togglePaid calls createContribution for unpaid user and updates paidUserIds", async () => {
    const wrapper = shallowMount(MemberManager)
    await settle()

    await (wrapper.vm as any).contributionPeriodChanged({id: 5, startDate: "2025-01-01", endDate: "2025-12-31"})

    // user 1 is unpaid; toggle should create contribution
    await (wrapper.vm as any).togglePaid(1)
    expect(mockCreateContribution).toHaveBeenCalledWith({body: {userId: 1, contributionPeriodId: 5}})
    expect((wrapper.vm as any).paidUserIds.has(1)).toBe(true)
  })

  it("togglePaid calls deleteContribution for paid user and removes from paidUserIds", async () => {
    // Seed user 1 as paid
    mockFindContributionsByPeriodId.mockResolvedValue({data: [{userId: 1, contributionPeriodId: 5}]})
    const wrapper = shallowMount(MemberManager)
    await settle()

    await (wrapper.vm as any).contributionPeriodChanged({id: 5, startDate: "2025-01-01", endDate: "2025-12-31"})
    expect((wrapper.vm as any).paidUserIds.has(1)).toBe(true)

    await (wrapper.vm as any).togglePaid(1)
    expect(mockDeleteContribution).toHaveBeenCalledWith({path: {contributionPeriodId: 5, userId: 1}})
    expect((wrapper.vm as any).paidUserIds.has(1)).toBe(false)
  })

  it("isSaving returns false when not toggling", async () => {
    const wrapper = shallowMount(MemberManager)
    await settle()

    expect((wrapper.vm as any).isSaving(1)).toBe(false)
  })
})
