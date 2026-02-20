import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import ContributionUserList from "@/components/common/lists/ContributionUserList.vue"

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

describe("ContributionUserList", () => {
  it("renders list and forwards contribution update event", async () => {
    const wrapper = mount(ContributionUserList, {
      props: {
        title: "Contribution paid",
        users,
        contributionPeriodId: 2,
        contributions: [],
        startOpen: true,
      },
      global: {
        stubs: {
          ContributionUserRow: {
            template: "<button @click=\"$emit('update:contribution', { id: 99, userId: 1, contributionPeriodId: 2 })\">row</button>",
          },
        },
      },
    })

    expect(wrapper.text()).toContain("Contribution paid")
    await wrapper.find("button").trigger("click")
    expect(wrapper.emitted("update:contribution")?.length).toBe(1)
  })
})
