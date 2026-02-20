import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import RecoveryUserList from "@/components/common/lists/RecoveryUserList.vue"

const {mockFilterUsers} = vi.hoisted(() => ({
  mockFilterUsers: vi.fn((users: Array<{ fullName?: string; username?: string }>, query: string) => {
    if (!query) return users
    const q = query.toLowerCase()
    return users.filter((u) => `${u.fullName} ${u.username}`.toLowerCase().includes(q))
  }),
}))

vi.mock("@/plugins/userFilter", () => ({
  filterUsers: mockFilterUsers,
}))

const users = [
  {id: 1, fullName: "Emma Dokter", username: "lyndisluna", enabled: false, roles: ["MEMBER"]},
  {id: 2, fullName: "Viktor Petrov", username: "ariosfury", enabled: true, roles: ["USER"]},
]

describe("RecoveryUserList", () => {
  it("renders recovery rows", () => {
    const wrapper = mount(RecoveryUserList, {
      props: {
        title: "Inactive accounts",
        users,
        actionType: "activation",
        startOpen: true,
      },
      global: {
        stubs: {
          RecoveryUserRow: true,
        },
      },
    })

    expect(wrapper.text()).toContain("Inactive accounts")
    expect(wrapper.findAll("recovery-user-row-stub").length).toBe(2)
  })
})
