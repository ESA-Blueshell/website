import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import AddressUserList from "@/components/common/lists/AddressUserList.vue"

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
          ...vuetifyStubs,
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

  it("filters users by multiple fields", async () => {
    const wrapper = mount(AddressUserList, {
      props: {
        title: "Users with address",
        users,
        addresses: [{id: 11, userId: 1}],
        startOpen: true,
      },
      global: {
        stubs: {
          ...vuetifyStubs,
          AddressUserRow: {
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
