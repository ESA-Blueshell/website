import {beforeEach, describe, expect, it, vi} from "vitest"
import RecoveryManager from "@/pages/management/RecoveryManager.vue"
import {mountInApp, settle} from "../helpers"

const {mockHandleNetworkError, mockFindUsers, mockFindDeletedUsers} = vi.hoisted(() => ({
  mockHandleNetworkError: vi.fn(), mockFindUsers: vi.fn(),
  mockFindDeletedUsers: vi.fn(),
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
  $showStatusMessage: vi.fn(),
}))

vi.mock("@/domains/user", () => ({
  listUsers: mockFindUsers,
  listDeletedUsers: mockFindDeletedUsers,
}))

vi.mock("@/domains/recovery", () => ({
  listPendingActivations: vi.fn().mockResolvedValue({}),
  TokenPurpose: {USER_ACTIVATION: "USER_ACTIVATION", MEMBER_ACTIVATION: "MEMBER_ACTIVATION"},
}))

describe("RecoveryManager page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindUsers.mockResolvedValue([
          {id: 1, enabled: false, username: "inactive"},
          {id: 2, enabled: true, username: "active"},
        ])
    mockFindDeletedUsers.mockResolvedValue([
          {id: 3, enabled: false, username: "deleted"},
        ])
  })

  it("splits users into active and inactive lists", async () => {
    const wrapper = mountInApp(RecoveryManager, {
      global: {
        stubs: {
          RecoveryUserList: true,
        },
      },
    })

    await settle()

    expect(mockFindUsers).toHaveBeenCalledTimes(1)
    expect(mockFindDeletedUsers).toHaveBeenCalledTimes(1)
    expect((wrapper.vm as any).inactiveUsers).toHaveLength(1)
    expect((wrapper.vm as any).activeUsers).toHaveLength(1)
    expect((wrapper.vm as any).deletedUsers).toHaveLength(1)
  })

  it("reports a refused read rather than showing an association with nobody in it", async () => {
    mockFindUsers.mockRejectedValue(new Error("server error"))

    const wrapper = mountInApp(RecoveryManager, {
      global: {stubs: {RecoveryUserList: true}},
    })
    await settle()

    expect(mockHandleNetworkError).toHaveBeenCalled()
    expect((wrapper.vm as any).inactiveUsers).toHaveLength(0)
    expect((wrapper.vm as any).activeUsers).toHaveLength(0)
  })

  it("reports a refused read of the deleted accounts", async () => {
    mockFindDeletedUsers.mockRejectedValue(new Error("deleted fetch error"))

    const wrapper = mountInApp(RecoveryManager, {
      global: {stubs: {RecoveryUserList: true}},
    })
    await settle()

    expect(mockHandleNetworkError).toHaveBeenCalled()
    expect((wrapper.vm as any).deletedUsers).toHaveLength(0)
  })

  it("deleted users list has restore action type", async () => {
    const wrapper = mountInApp(RecoveryManager, {
      global: {stubs: {RecoveryUserList: true}},
    })
    await settle()

    const lists = wrapper.findAllComponents({name: "RecoveryUserList"})
    const deletedList = lists.find((w) => w.props("panelKey") === "deleted")
    expect(deletedList).toBeDefined()
    expect(deletedList!.props("actionType")).toBe("restore")
    expect(deletedList!.props("users")).toHaveLength(1)
  })
})
