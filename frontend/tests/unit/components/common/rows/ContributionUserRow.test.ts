import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import ContributionUserRow from "@/components/common/rows/ContributionUserRow.vue"

const {mockCreateContribution, mockDeleteContribution} = vi.hoisted(() => ({
  mockCreateContribution: vi.fn(),
  mockDeleteContribution: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  createContribution: mockCreateContribution,
  deleteContribution: mockDeleteContribution,
}))

describe("ContributionUserRow", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCreateContribution.mockResolvedValue({data: {id: 100, userId: 1, contributionPeriodId: 9}})
    mockDeleteContribution.mockResolvedValue({})
  })

  it("marks and unmarks contribution", async () => {
    const wrapper = mount(ContributionUserRow, {
      props: {
        user: {id: 1, fullName: "Emma", username: "emma", roles: ["MEMBER"]},
        contributionPeriodId: 9,
        contributions: [],
      },
    })

    await (wrapper.vm as any).markPaid()
    expect(mockCreateContribution).toHaveBeenCalledWith({
      body: {
        userId: 1,
        contributionPeriodId: 9,
      },
    })

    await wrapper.setProps({
      contributions: [{id: 100, userId: 1, contributionPeriodId: 9}],
    })

    await (wrapper.vm as any).unmarkPaid()
    expect(mockDeleteContribution).toHaveBeenCalledWith({
      path: {
        contributionPeriodId: 9,
        userId: 1,
      },
    })
  })
})
