import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import MemberUserRow from "@/components/common/rows/MemberUserRow.vue"

const {mockDeleteUserById, mockUpdateMembership} = vi.hoisted(() => ({
  mockDeleteUserById: vi.fn(),
  mockUpdateMembership: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  deleteUserById: mockDeleteUserById,
  updateMembership: mockUpdateMembership,
}))

vi.mock("@/components/form/UserForm.vue", () => ({
  default: {
    name: "UserForm",
    template: "<div />",
  },
}))

vi.mock("@/components/common/modals/StartMembershipDialog.vue", () => ({
  default: {
    name: "StartMembershipDialog",
    template: "<div />",
  },
}))

describe("MemberUserRow", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockDeleteUserById.mockResolvedValue({})
    mockUpdateMembership.mockResolvedValue({data: {id: 55, userId: 1}})
  })

  it("handles membership lifecycle and user deletion", async () => {
    const wrapper = mount(MemberUserRow, {
      props: {
        user: {id: 1, fullName: "Emma", username: "emma", roles: ["MEMBER"]},
        membership: {id: 55, userId: 1},
        expanded: 0,
      },
      global: {
        stubs: {
          UserForm: true,
          DeleteConfirmationDialog: true,
          StartMembershipDialog: true,
        },
      },
    })

    await (wrapper.vm as any).endMembership()
    expect(mockUpdateMembership).toHaveBeenCalled()

    await (wrapper.vm as any).resumeMembership()
    expect(mockUpdateMembership).toHaveBeenCalledTimes(2)

    await (wrapper.vm as any).confirmDeleteUser()
    expect(mockDeleteUserById).toHaveBeenCalledWith({path: {userId: 1}})
    expect(wrapper.emitted("delete:user")?.[0]).toEqual([{id: 1, fullName: "Emma", username: "emma", roles: ["MEMBER"]}])
  })
})
