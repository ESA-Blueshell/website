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

const mountRow = (props: Record<string, unknown>) =>
  mount(ContributionUserRow, {
    props: {
      user: {id: 1, fullName: "Emma", username: "emma", roles: ["MEMBER"]},
      contributionPeriodId: 9,
      contributions: [],
      ...props,
    },
    global: {
      stubs: {
        VChip: {
          props: ["color"],
          template: "<span data-testid=\"membership-chip\" :data-color=\"color\"><slot /></span>",
        },
      },
    },
  })

describe("ContributionUserRow", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCreateContribution.mockResolvedValue({data: {id: 100, userId: 1, contributionPeriodId: 9}})
    mockDeleteContribution.mockResolvedValue({})
  })

  it("marks and unmarks contribution", async () => {
    const wrapper = mountRow({})

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

  it("renders membership chip from selected-period membership set instead of current roles", () => {
    const periodMember = mountRow({
      user: {id: 1, fullName: "Emma", username: "emma", roles: ["USER"]},
      periodMemberUserIds: new Set([1]),
    })

    expect(periodMember.get("[data-testid='membership-chip']").text()).toBe("Member")
    expect(periodMember.get("[data-testid='membership-chip']").attributes("data-color")).toBe("primary")

    const currentMemberOnly = mountRow({
      user: {id: 1, fullName: "Emma", username: "emma", roles: ["MEMBER"]},
      periodMemberUserIds: new Set<number>(),
    })

    expect(currentMemberOnly.get("[data-testid='membership-chip']").text()).toBe("User")
    expect(currentMemberOnly.get("[data-testid='membership-chip']").attributes("data-color")).toBe("grey")

    const noRoles = mountRow({
      user: {id: 1, fullName: "Emma", username: "emma", roles: []},
      periodMemberUserIds: new Set<number>(),
    })

    expect(noRoles.get("[data-testid='membership-chip']").text()).toBe("User")
    expect(noRoles.get("[data-testid='membership-chip']").attributes("data-color")).toBe("grey")
  })
})
