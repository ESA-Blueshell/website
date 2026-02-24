import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import RecoveryManager from "@/pages/management/RecoveryManager.vue"
import {settle} from "../helpers"

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
    const wrapper = shallowMount(RecoveryManager, {
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
})
