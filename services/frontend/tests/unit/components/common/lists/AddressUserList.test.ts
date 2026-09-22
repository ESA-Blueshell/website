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

const listWith = (props: Record<string, unknown>, rowStub: unknown = true) =>
  mount(AddressUserList, {
    props: {
      title: "Users with address",
      users,
      addresses: [{id: 11, userId: 1}],
      startOpen: true,
      ...props,
    },
    global: {stubs: {...vuetifyStubs, AddressUserRow: rowStub}},
  })

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
    expect((wrapper.vm as any).countLabel).toBe("1 / 2")
  })

  it("says so when the search matches nobody", async () => {
    const wrapper = listWith({})

    await wrapper.find("input").setValue("nobody-by-that-name")

    expect(wrapper.get("[data-testid='address-user-list-empty-users-with-address']").text())
      .toContain("No")
  })

  it.each([
    ["a click", "click"],
    ["the enter key", "keydown.enter"],
    ["the space bar", "keydown.space"],
  ])("opens and shuts on %s", async (_name, event) => {
    const wrapper = listWith({startOpen: false})
    const toggle = wrapper.get("[data-testid='address-user-list-toggle-users-with-address']")

    await toggle.trigger(event)
    expect(toggle.attributes("aria-expanded")).toBe("true")

    await toggle.trigger(event)
    expect(toggle.attributes("aria-expanded")).toBe("false")
  })

  it("is named by the key it was given rather than by its title", () => {
    const wrapper = listWith({panelKey: "with-address"})

    expect(wrapper.find("[data-testid='address-user-list-toggle-with-address']").exists()).toBe(true)
  })

  it("passes the row's expanding and removing up to the manager", async () => {
    const wrapper = listWith({}, {
      props: ["user"],
      template: "<div><button data-test='expand' @click=\"$emit('update:expanded', 1)\" /><button data-test='remove' @click=\"$emit('delete:address', 11)\" /></div>",
    })

    await wrapper.get("[data-test='expand']").trigger("click")
    await wrapper.get("[data-test='remove']").trigger("click")

    expect(wrapper.emitted("update:expanded")?.[0]).toEqual([1])
    expect(wrapper.emitted("delete:address")?.[0]).toEqual([11])
  })

  it("keys an account the api answered without a number by its username", () => {
    const wrapper = listWith({users: [{username: "ariosfury", fullName: "Viktor", roles: []}]})

    expect(wrapper.text()).toContain("Users with address")
  })
})
