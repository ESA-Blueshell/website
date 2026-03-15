import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import ContributionManager from "@/pages/management/ContributionManager.vue"
import {settle} from "../helpers"

const {
  mockFindUsers,
  mockFindMemberships,
  mockFindContributionsByPeriodId,
} = vi.hoisted(() => ({
  mockFindUsers: vi.fn(),
  mockFindMemberships: vi.fn(),
  mockFindContributionsByPeriodId: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  findUsers: mockFindUsers,
  findMemberships: mockFindMemberships,
  findContributionsByPeriodId: mockFindContributionsByPeriodId,
}))

describe("ContributionManager page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindUsers.mockResolvedValue({
      status: 200,
      data: {
        content: [
          {id: 1, username: "alice"},
          {id: 2, username: "bob"},
        ],
      },
    })
    mockFindMemberships.mockResolvedValue({
      data: [
        {id: 11, userId: 1, endDate: null},
        {id: 12, userId: 2, endDate: null},
      ],
    })
    mockFindContributionsByPeriodId.mockResolvedValue({
      data: [{id: 91, userId: 1, contributionPeriodId: 8}],
    })
  })

  it("upserts contributions by user and selected period", async () => {
    const wrapper = shallowMount(ContributionManager, {
      global: {
        stubs: {
          ContributionPeriodList: true,
          ContributionUserList: true,
        },
      },
    })

    await settle()

    await (wrapper.vm as any).contributionPeriodChanged({id: 8, startDate: "2026-01-01", endDate: "2026-12-31"})
    expect(mockFindContributionsByPeriodId).toHaveBeenCalledWith({path: {periodId: 8}})

    ;(wrapper.vm as any).contributionAddedOrUpdated({id: 91, userId: 1, contributionPeriodId: 8})
    ;(wrapper.vm as any).contributionAddedOrUpdated({id: 92, userId: 1, contributionPeriodId: 8})

    expect((wrapper.vm as any).contributions).toHaveLength(1)
    expect((wrapper.vm as any).contributions[0].id).toBe(92)

    ;(wrapper.vm as any).contributionDeleted(1)
    expect((wrapper.vm as any).contributions).toHaveLength(0)
  })
})
