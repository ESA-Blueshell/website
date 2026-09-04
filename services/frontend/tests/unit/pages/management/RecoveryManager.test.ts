import {beforeEach, describe, expect, it, vi} from "vitest"
import RecoveryManager from "@/pages/management/RecoveryManager.vue"
import {mountInApp, settle} from "../helpers"

const {mockFindUsers, mockFindDeletedUsers} = vi.hoisted(() => ({
  mockFindUsers: vi.fn(),
  mockFindDeletedUsers: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  findUsers: mockFindUsers,
  findDeletedUsers: mockFindDeletedUsers,
}))

describe("RecoveryManager page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindUsers.mockResolvedValue({
      status: 200,
      data: {
        content: [
          {id: 1, enabled: false, username: "inactive"},
          {id: 2, enabled: true, username: "active"},
        ],
      },
    })
    mockFindDeletedUsers.mockResolvedValue({
      status: 200,
      data: {
        content: [
          {id: 3, enabled: false, username: "deleted"},
        ],
      },
    })
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

  it("logs error when findUsers returns non-200", async () => {
    const consoleSpy = vi.spyOn(console, "log").mockImplementation(() => {})
    mockFindUsers.mockResolvedValue({
      status: 500,
      error: "server error",
    })

    const wrapper = mountInApp(RecoveryManager, {
      global: {stubs: {RecoveryUserList: true}},
    })
    await settle()

    expect(consoleSpy).toHaveBeenCalledWith("server error")
    expect((wrapper.vm as any).inactiveUsers).toHaveLength(0)
    expect((wrapper.vm as any).activeUsers).toHaveLength(0)
    consoleSpy.mockRestore()
  })

  it("logs error when findDeletedUsers returns non-200", async () => {
    const consoleSpy = vi.spyOn(console, "log").mockImplementation(() => {})
    mockFindDeletedUsers.mockResolvedValue({
      status: 500,
      error: "deleted fetch error",
    })

    const wrapper = mountInApp(RecoveryManager, {
      global: {stubs: {RecoveryUserList: true}},
    })
    await settle()

    expect(consoleSpy).toHaveBeenCalledWith("deleted fetch error")
    expect((wrapper.vm as any).deletedUsers).toHaveLength(0)
    consoleSpy.mockRestore()
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
