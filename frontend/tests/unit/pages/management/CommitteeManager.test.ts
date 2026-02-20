import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import CommitteeManager from "@/pages/management/CommitteeManager.vue"
import {settle} from "../helpers"

const {
  mockFindCommittees,
  mockFindUsers,
  mockDeleteCommitteeById,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockFindCommittees: vi.fn(),
  mockFindUsers: vi.fn(),
  mockDeleteCommitteeById: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  findCommittees: mockFindCommittees,
  findUsers: mockFindUsers,
  deleteCommitteeById: mockDeleteCommitteeById,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

describe("CommitteeManager page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindCommittees.mockResolvedValue({
      data: [
        {id: 5, name: "Events", description: "desc", version: 1, members: [{userId: 1, role: "MEMBER"}]},
      ],
    })
    mockFindUsers.mockResolvedValue({data: {content: [{id: 1, username: "alice"}]}})
    mockDeleteCommitteeById.mockResolvedValue({})
  })

  it("loads committees/users and upserts committee updates", async () => {
    const wrapper = shallowMount(CommitteeManager, {
      global: {
        stubs: {
          CommitteeForm: true,
          DeletionConfirmationDialog: true,
        },
      },
    })

    await settle()

    expect(mockFindCommittees).toHaveBeenCalledTimes(1)
    expect(mockFindUsers).toHaveBeenCalledTimes(1)
    expect((wrapper.vm as any).committees).toHaveLength(1)

    ;(wrapper.vm as any).updateCommittee({id: 5, name: "Events Updated", description: "d", members: []})
    expect((wrapper.vm as any).committees).toHaveLength(1)
    expect((wrapper.vm as any).committees[0].name).toBe("Events Updated")

    ;(wrapper.vm as any).updateCommittee({id: 7, name: "SiteCie", description: "d", members: []})
    expect((wrapper.vm as any).committees).toHaveLength(2)
  })

  it("deletes selected committee", async () => {
    const wrapper = shallowMount(CommitteeManager)
    await settle()

    ;(wrapper.vm as any).committeeToDelete = {id: 5, name: "Events"}
    await (wrapper.vm as any).deleteCommittee()

    expect(mockDeleteCommitteeById).toHaveBeenCalledWith({path: {id: 5}})
    expect((wrapper.vm as any).committees).toHaveLength(0)
  })
})
