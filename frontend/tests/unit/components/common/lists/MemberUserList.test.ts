import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import MemberUserList from "@/components/common/lists/MemberUserList.vue"

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
  {
    id: 1,
    fullName: "Emma Dokter",
    firstName: "Emma",
    username: "lyndisluna",
    discord: "emma-filter",
    enabled: false,
    roles: ["MEMBER"],
  },
  {
    id: 2,
    fullName: "Viktor Petrov",
    firstName: "Viktor",
    username: "ariosfury",
    discord: "viktor-filter",
    enabled: true,
    roles: ["USER"],
  },
]

const vuetifyStubs = {
  VCard: {template: "<div><slot /></div>"},
  VBadge: {template: "<div><slot /></div>"},
  VIcon: {template: "<span><slot /></span>"},
  VExpandTransition: {template: "<div><slot /></div>"},
  VList: {template: "<div><slot /></div>"},
  VListItem: {template: "<div><slot /></div>"},
  VListItemTitle: {template: "<div><slot /></div>"},
  VDivider: {template: "<hr />"},
  VTextField: {
    props: ["modelValue"],
    emits: ["update:modelValue"],
    template: "<input :value=\"modelValue\" @input=\"$emit('update:modelValue', $event.target.value)\" />",
  },
}

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
          ...vuetifyStubs,
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

  it("filters users by multiple fields", async () => {
    const wrapper = mount(MemberUserList, {
      props: {
        title: "Members",
        users,
        startOpen: true,
      },
      global: {
        stubs: {
          ...vuetifyStubs,
          MemberUserRow: {
            props: ["user"],
            template: "<div class='row-username'>{{ user.username }}</div>",
          },
          UserForm: true,
        },
      },
    })

    expect(wrapper.text()).toContain("lyndisluna")
    expect(wrapper.text()).toContain("ariosfury")

    const search = wrapper.find("input")
    await search.setValue("Emma emma-filter")

    expect(wrapper.text()).toContain("lyndisluna")
    expect(wrapper.text()).not.toContain("ariosfury")
  })
})
