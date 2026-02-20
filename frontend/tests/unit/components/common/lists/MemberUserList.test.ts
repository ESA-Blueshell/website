import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import MemberUserList from "@/components/common/lists/MemberUserList.vue"

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

vi.mock("@/components/common/rows/MemberUserRow.vue", () => ({
  default: {
    name: "MemberUserRow",
    template: "<div />",
  },
}))

vi.mock("@/components/form/UserForm.vue", () => ({
  default: {
    name: "UserForm",
    template: "<div />",
  },
}))

const users = [
  {id: 1, fullName: "Emma Dokter", username: "lyndisluna", enabled: false, roles: ["MEMBER"]},
]

describe("MemberUserList", () => {
  it("handles create draft and row delete events", async () => {
    const wrapper = mount(MemberUserList, {
      props: {
        title: "Members",
        users,
        allowCreate: true,
        startOpen: true,
      },
      global: {
        stubs: {
          MemberUserRow: {
            template: "<button @click=\"$emit('delete:user', { id: 1 })\">member-row</button>",
          },
          UserForm: true,
        },
      },
    })

    ;(wrapper.vm as any).createDraft = {id: 3, fullName: "New User", username: "new-user"}
    ;(wrapper.vm as any).onCreateSubmitted(true)
    expect(wrapper.emitted("update:user")?.length).toBe(1)

    await wrapper.find("button").trigger("click")
    expect(wrapper.emitted("delete:user")?.length).toBe(1)
  })
})
