import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount, type VueWrapper} from "@vue/test-utils"
import ContributionManager from "@/pages/management/ContributionManager.vue"
import {settle} from "../helpers"

const {
  mockFindUsers,
  mockFindContributionsByPeriodId,
} = vi.hoisted(() => ({
  mockFindUsers: vi.fn(),
  mockFindContributionsByPeriodId: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  findUsers: mockFindUsers,
  findContributionsByPeriodId: mockFindContributionsByPeriodId,
}))

const contribution = (userId: number, contributionPeriodId: number, version = 1) => ({
  contributionPeriodId,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  userId,
  version,
})

const period = (id: number) => ({
  alumniFee: 15,
  createdAt: "2026-01-01T00:00:00Z",
  endDate: "2026-12-31",
  fullYearFee: 30,
  halfYearFee: 15,
  id,
  startDate: "2026-01-01",
  updatedAt: "2026-01-01T00:00:00Z",
  version: 1,
})

const usernames = (users: unknown) =>
  (users as Array<{username: string}>).map((user) => user.username)

const mountPage = () =>
  shallowMount(ContributionManager, {
    global: {
      stubs: {
        TopBanner: true,
        ContributionPeriodList: true,
        ContributionUserList: {
          name: "ContributionUserList",
          props: [
            "contributionPeriodId",
            "contributions",
            "disabled",
            "panelKey",
            "users",
            "title",
          ],
          template: "<div />",
        },
      },
    },
  })

const contributionList = (wrapper: VueWrapper, panelKey: string) => {
  const list = wrapper.findAllComponents({name: "ContributionUserList"})
    .find((component) => component.props("panelKey") === panelKey)
  if (!list) throw new Error(`ContributionUserList ${panelKey} was not rendered`)
  return list
}

describe("ContributionManager page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindUsers.mockResolvedValue({
      status: 200,
      data: {
        content: [
          {id: 1, username: "alice", fullName: "Alice Member", roles: ["MEMBER"]},
          {id: 2, username: "bob", fullName: "Bob Guest", roles: ["GUEST"]},
          {id: 3, username: "carol", fullName: "Carol Member", roles: ["MEMBER"]},
        ],
      },
    })
    mockFindContributionsByPeriodId.mockResolvedValue({
      data: [contribution(1, 8)],
    })
  })

  it("shows all users and splits them by selected-period contribution", async () => {
    mockFindContributionsByPeriodId.mockResolvedValueOnce({
      data: [
        contribution(1, 8),
        contribution(2, 7),
      ],
    })

    const wrapper = mountPage()
    await settle()

    const initialPaid = contributionList(wrapper, "paid")
    const initialUnpaid = contributionList(wrapper, "unpaid")

    expect(initialPaid.props("panelKey")).toBe("paid")
    expect(initialUnpaid.props("panelKey")).toBe("unpaid")
    expect(usernames(initialPaid.props("users"))).toEqual([])
    expect(usernames(initialUnpaid.props("users"))).toEqual(["alice", "bob", "carol"])
    expect(initialPaid.props("disabled")).toBe(true)
    expect(initialUnpaid.props("disabled")).toBe(true)

    await (wrapper.vm as any).contributionPeriodChanged(period(8))
    await settle()

    const paid = contributionList(wrapper, "paid")
    const unpaid = contributionList(wrapper, "unpaid")

    expect(mockFindContributionsByPeriodId).toHaveBeenCalledWith({path: {periodId: 8}})
    expect(usernames(paid.props("users"))).toEqual(["alice"])
    expect(usernames(unpaid.props("users"))).toEqual(["bob", "carol"])
    expect(paid.props("disabled")).toBe(false)
    expect(unpaid.props("disabled")).toBe(false)
  })

  it("clears contribution state and disables lists when selected period is undefined", async () => {
    const wrapper = mountPage()
    await settle()

    await (wrapper.vm as any).contributionPeriodChanged(period(8))
    await settle()

    expect((wrapper.vm as any).selectedPeriodId).toBe(8)
    expect((wrapper.vm as any).contributions).toHaveLength(1)

    await (wrapper.vm as any).contributionPeriodChanged(undefined)
    await settle()

    const paid = contributionList(wrapper, "paid")
    const unpaid = contributionList(wrapper, "unpaid")

    expect((wrapper.vm as any).selectedPeriodId).toBe(0)
    expect((wrapper.vm as any).contributions).toEqual([])
    expect(usernames(paid.props("users"))).toEqual([])
    expect(usernames(unpaid.props("users"))).toEqual(["alice", "bob", "carol"])
    expect(paid.props("disabled")).toBe(true)
    expect(unpaid.props("disabled")).toBe(true)
  })

  it("upserts contributions by user and selected period", async () => {
    const wrapper = mountPage()
    await settle()

    await (wrapper.vm as any).contributionPeriodChanged(period(8))
    await settle()

    expect(mockFindContributionsByPeriodId).toHaveBeenCalledWith({path: {periodId: 8}})
    expect(usernames((wrapper.vm as any).usersPaid)).toEqual(["alice"])
    expect(usernames((wrapper.vm as any).usersUnpaid)).toEqual(["bob", "carol"])

    ;(wrapper.vm as any).contributionAddedOrUpdated(contribution(1, 8, 1))
    ;(wrapper.vm as any).contributionAddedOrUpdated(contribution(1, 8, 2))

    expect((wrapper.vm as any).contributions).toHaveLength(1)
    expect((wrapper.vm as any).contributions[0].version).toBe(2)

    ;(wrapper.vm as any).contributionDeleted(1)

    expect((wrapper.vm as any).contributions).toHaveLength(0)
    expect(usernames((wrapper.vm as any).usersPaid)).toEqual([])
    expect(usernames((wrapper.vm as any).usersUnpaid)).toEqual(["alice", "bob", "carol"])
  })
})
