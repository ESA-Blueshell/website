import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount, type VueWrapper} from "@vue/test-utils"
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

const contribution = (userId: number, contributionPeriodId: number, version = 1) => ({
  contributionPeriodId,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  userId,
  version,
})

const membership = (userId: number, id = userId) => ({
  createdAt: "2026-01-01T00:00:00Z",
  id,
  incasso: false,
  memberType: "REGULAR",
  startDate: "2026-01-01",
  updatedAt: "2026-01-01T00:00:00Z",
  userId,
  version: 1,
})

type ContributionsResponse = {
  data: Array<ReturnType<typeof contribution>>
}

type MembershipsResponse = {
  data: Array<ReturnType<typeof membership>>
}

const deferred = <T>() => {
  let resolve: (value: T) => void = () => undefined
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return {promise, resolve}
}

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

const memberUserIds = (list: VueWrapper) =>
  [...(list.props("periodMemberUserIds") as Set<number>)].sort((a, b) => a - b)

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
            "periodMemberUserIds",
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
    mockFindMemberships.mockResolvedValue({
      data: [
        membership(1, 90),
        membership(3, 91),
      ],
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
    expect(memberUserIds(initialPaid)).toEqual([])
    expect(memberUserIds(initialUnpaid)).toEqual([])
    expect(initialPaid.props("disabled")).toBe(true)
    expect(initialUnpaid.props("disabled")).toBe(true)

    await (wrapper.vm as any).contributionPeriodChanged(period(8))
    await settle()

    const paid = contributionList(wrapper, "paid")
    const unpaid = contributionList(wrapper, "unpaid")

    expect(mockFindContributionsByPeriodId).toHaveBeenCalledWith({path: {periodId: 8}})
    expect(mockFindMemberships).toHaveBeenCalledWith({query: {from: "2026-01-01", to: "2026-12-31"}})
    expect(usernames(paid.props("users"))).toEqual(["alice"])
    expect(usernames(unpaid.props("users"))).toEqual(["bob", "carol"])
    expect(memberUserIds(paid)).toEqual([1, 3])
    expect(memberUserIds(unpaid)).toEqual([1, 3])
    expect(paid.props("disabled")).toBe(false)
    expect(unpaid.props("disabled")).toBe(false)
  })

  it("clears contribution and membership state and disables lists when selected period is undefined", async () => {
    const wrapper = mountPage()
    await settle()

    await (wrapper.vm as any).contributionPeriodChanged(period(8))
    await settle()

    expect((wrapper.vm as any).selectedPeriodId).toBe(8)
    expect((wrapper.vm as any).contributions).toHaveLength(1)
    expect(memberUserIds(contributionList(wrapper, "paid"))).toEqual([1, 3])

    await (wrapper.vm as any).contributionPeriodChanged(undefined)
    await settle()

    const paid = contributionList(wrapper, "paid")
    const unpaid = contributionList(wrapper, "unpaid")

    expect((wrapper.vm as any).selectedPeriodId).toBe(0)
    expect((wrapper.vm as any).contributions).toEqual([])
    expect(usernames(paid.props("users"))).toEqual([])
    expect(usernames(unpaid.props("users"))).toEqual(["alice", "bob", "carol"])
    expect(memberUserIds(paid)).toEqual([])
    expect(memberUserIds(unpaid)).toEqual([])
    expect(paid.props("disabled")).toBe(true)
    expect(unpaid.props("disabled")).toBe(true)
  })

  it("passes an empty period membership set when selected period has no memberships", async () => {
    mockFindMemberships.mockResolvedValueOnce({data: []})

    const wrapper = mountPage()
    await settle()

    await (wrapper.vm as any).contributionPeriodChanged(period(8))
    await settle()

    expect(memberUserIds(contributionList(wrapper, "paid"))).toEqual([])
    expect(memberUserIds(contributionList(wrapper, "unpaid"))).toEqual([])
  })

  it("does not let stale period membership or contribution responses overwrite the current period", async () => {
    const period8Contributions = deferred<ContributionsResponse>()
    const period8Memberships = deferred<MembershipsResponse>()
    const period9Contributions = deferred<ContributionsResponse>()
    const period9Memberships = deferred<MembershipsResponse>()
    const newPeriod = {
      ...period(9),
      startDate: "2027-01-01",
      endDate: "2027-12-31",
    }

    mockFindContributionsByPeriodId
      .mockReturnValueOnce(period8Contributions.promise)
      .mockReturnValueOnce(period9Contributions.promise)
    mockFindMemberships
      .mockReturnValueOnce(period8Memberships.promise)
      .mockReturnValueOnce(period9Memberships.promise)

    const wrapper = mountPage()
    await settle()

    const request8 = (wrapper.vm as any).contributionPeriodChanged(period(8))
    const request9 = (wrapper.vm as any).contributionPeriodChanged(newPeriod)

    period9Contributions.resolve({data: [contribution(2, 9)]})
    period9Memberships.resolve({data: [membership(2, 92)]})
    await request9
    await settle()

    expect(mockFindMemberships).toHaveBeenNthCalledWith(1, {query: {from: "2026-01-01", to: "2026-12-31"}})
    expect(mockFindMemberships).toHaveBeenNthCalledWith(2, {query: {from: "2027-01-01", to: "2027-12-31"}})
    expect((wrapper.vm as any).selectedPeriodId).toBe(9)
    expect((wrapper.vm as any).contributions).toEqual([contribution(2, 9)])
    expect(usernames((wrapper.vm as any).usersPaid)).toEqual(["bob"])
    expect(memberUserIds(contributionList(wrapper, "paid"))).toEqual([2])
    expect(memberUserIds(contributionList(wrapper, "unpaid"))).toEqual([2])

    period8Contributions.resolve({data: [contribution(1, 8)]})
    period8Memberships.resolve({data: [membership(1, 91)]})
    await request8
    await settle()

    expect((wrapper.vm as any).selectedPeriodId).toBe(9)
    expect((wrapper.vm as any).contributions).toEqual([contribution(2, 9)])
    expect(usernames((wrapper.vm as any).usersPaid)).toEqual(["bob"])
    expect(memberUserIds(contributionList(wrapper, "paid"))).toEqual([2])
    expect(memberUserIds(contributionList(wrapper, "unpaid"))).toEqual([2])
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
