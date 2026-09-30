import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import BulkTask from "@/pages/management/BulkTask.vue"
import {aMembership, aUser} from "../../helpers/apiFixtures"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  findUsers: vi.fn(),
  findMemberships: vi.fn(),
  findContributionsByPeriodId: vi.fn(),
  previewBulkEnd: vi.fn(),
}))
const {mockRoute, mockPush, mockHandleNetworkError} = vi.hoisted(() => ({
  mockRoute: {params: {action: "end"} as Record<string, string>, query: {} as Record<string, string>},
  mockPush: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => mockRoute,
  useRouter: () => ({push: mockPush}),
}))

vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockHandleNetworkError}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

describe("the bulk task page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async (action: string, query: Record<string, string>) => {
    mockRoute.params = {action}
    mockRoute.query = query
    const wrapper = mountInApp(BulkTask)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.findUsers.mockResolvedValue({status: 200, data: {content: [aUser({id: 2, fullName: "Bob"}), aUser({id: 3, fullName: "Cas"})]}})
    api.findMemberships.mockResolvedValue({status: 200, data: [aMembership({id: 9, userId: 2, startDate: "2024-01-01", endDate: null})]})
    api.findContributionsByPeriodId.mockResolvedValue({status: 200, data: [{userId: 3, contributionPeriodId: 5}]})
    api.previewBulkEnd.mockResolvedValue({status: 200, data: {effectiveDate: "2026-09-30", rows: []}})
  })

  afterEach(() => {
    unmountAll(wrappers, "BulkTask")
  })

  it("draws the membership work in the page for the people named in its address", async () => {
    const wrapper = await mount("end", {ids: "2,x,3"})

    const task = wrapper.findComponent({name: "MembershipStatusDialog"})
    expect(task.props("inline")).toBe(true)
    expect(task.props("targets").map((one: {userId: number}) => one.userId)).toEqual([2, 3])
    expect(api.findContributionsByPeriodId).not.toHaveBeenCalled()
  })

  it("marks payments for the period it names, knowing who has paid", async () => {
    const wrapper = await mount("paid", {ids: "2,3", period: "5", back: "/management/contributions"})

    const task = wrapper.findComponent({name: "PaidStatusDialog"})
    expect(task.props("contributionPeriodId")).toBe(5)
    expect(task.props("targets").find((one: {userId: number}) => one.userId === 3).mostRecentContribution.paid).toBe(true)

    task.vm.$emit("done")
    expect(mockPush).toHaveBeenCalledWith("/management/contributions")
  })

  it("goes back to Users when it is cancelled, and never to a page outside Management", async () => {
    const wrapper = await mount("start", {ids: "2", back: "https://example.com"})

    wrapper.findComponent({name: "MembershipStatusDialog"}).vm.$emit("update:modelValue", false)
    expect(mockPush).toHaveBeenCalledWith("/management/users")
  })

  it("says so when nobody is selected, and when the people could not be read", async () => {
    expect((await mount("end", {})).find('[data-testid="bulk-task-empty"]').exists()).toBe(true)

    api.findUsers.mockRejectedValue(new Error("offline"))
    await mount("end", {ids: "2"})
    expect(mockHandleNetworkError).toHaveBeenCalled()
  })
})
