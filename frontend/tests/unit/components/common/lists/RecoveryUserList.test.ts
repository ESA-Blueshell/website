import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import RecoveryUserList from "@/components/common/lists/RecoveryUserList.vue"

const users = [
  {
    id: 1,
    fullName: "Emma Dokter",
    firstName: "Emma",
    username: "lyndisluna",
    email: "emma.filter@test.com",
    enabled: false,
    roles: ["MEMBER"],
  },
  {
    id: 2,
    fullName: "Viktor Petrov",
    firstName: "Viktor",
    username: "ariosfury",
    email: "viktor.filter@test.com",
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
  VDivider: {template: "<hr />"},
  VTextField: {
    props: ["modelValue"],
    emits: ["update:modelValue"],
    template: "<input :value=\"modelValue\" @input=\"$emit('update:modelValue', $event.target.value)\" />",
  },
}

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
          ...vuetifyStubs,
          RecoveryUserRow: true,
        },
      },
    })

    expect(wrapper.text()).toContain("Inactive accounts")
    expect(wrapper.findAll("recovery-user-row-stub").length).toBe(2)
  })

  it("filters users by multiple fields", async () => {
    const wrapper = mount(RecoveryUserList, {
      props: {
        title: "Inactive accounts",
        users,
        actionType: "activation",
        startOpen: true,
      },
      global: {
        stubs: {
          ...vuetifyStubs,
          RecoveryUserRow: {
            props: ["user"],
            template: "<div class='row-username'>{{ user.username }}</div>",
          },
        },
      },
    })

    expect(wrapper.text()).toContain("lyndisluna")
    expect(wrapper.text()).toContain("ariosfury")

    const search = wrapper.find("input")
    await search.setValue("Emma emma.filter@test.com")

    expect(wrapper.text()).toContain("lyndisluna")
    expect(wrapper.text()).not.toContain("ariosfury")
  })
})
