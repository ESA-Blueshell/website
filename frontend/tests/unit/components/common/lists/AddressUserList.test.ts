import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import AddressUserList from "@/components/common/lists/AddressUserList.vue"

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

describe("AddressUserList", () => {
  it("renders users and forwards row events", async () => {
    const wrapper = mount(AddressUserList, {
      props: {
        title: "Users with address",
        users,
        addresses: [{id: 11, userId: 1}],
        startOpen: true,
      },
      global: {
        stubs: {
          AddressUserRow: {
            props: ["user"],
            template: "<button @click=\"$emit('update:address', { id: user.id, userId: user.id })\">row</button>",
          },
        },
      },
    })

    expect(wrapper.text()).toContain("Users with address")
    await wrapper.find("button").trigger("click")
    expect(wrapper.emitted("update:address")?.length).toBe(1)
  })
})
