import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import MemberManager from "@/pages/management/MemberManager.vue"
import {settle} from "../helpers"

const {
  mockFindUsers,
  mockFindUserById,
  mockFindMemberships,
  mockFindContributionsByPeriodId,
} = vi.hoisted(() => ({
  mockFindUsers: vi.fn(),
  mockFindUserById: vi.fn(),
  mockFindMemberships: vi.fn(),
  mockFindContributionsByPeriodId: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  findUsers: mockFindUsers,
  findUserById: mockFindUserById,
  findMemberships: mockFindMemberships,
  findContributionsByPeriodId: mockFindContributionsByPeriodId,
}))

vi.mock("@/components/common/lists/MemberUserList.vue", () => ({
  default: {
    name: "MemberUserList",
    template: "<div />",
  },
}))

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

describe("MemberManager page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindUsers.mockResolvedValue({
      status: 200,
      data: {content: [{id: 1, username: "alice"}]},
    })
    mockFindUserById.mockResolvedValue({
      data: {id: 1, username: "alice", roles: ["MEMBER"]},
    })
    mockFindMemberships.mockResolvedValue({data: [{id: 90, userId: 1}]})
    mockFindContributionsByPeriodId.mockResolvedValue({data: [{id: 91, userId: 1}]})
  })

  it("upserts users and memberships and refreshes user after membership change", async () => {
    const wrapper = shallowMount(MemberManager, {
      global: {
        stubs: {
          MemberUserList: true,
          ContributionPeriodList: true,
        },
      },
    })

    await settle()

    ;(wrapper.vm as any).updateUser({id: 1, username: "alice-updated"})
    expect((wrapper.vm as any).users).toHaveLength(1)
    expect((wrapper.vm as any).users[0].username).toBe("alice-updated")

    await (wrapper.vm as any).membershipChanged({id: 90, userId: 1})
    expect(mockFindUserById).toHaveBeenCalledWith({path: {userId: 1}})

    await (wrapper.vm as any).contributionPeriodChanged({id: 8, startDate: "2026-01-01", endDate: "2026-12-31"})
    expect(mockFindMemberships).toHaveBeenCalledWith({query: {from: "2026-01-01", to: "2026-12-31"}})
    expect(mockFindContributionsByPeriodId).toHaveBeenCalledWith({path: {periodId: 8}})
  })
})
