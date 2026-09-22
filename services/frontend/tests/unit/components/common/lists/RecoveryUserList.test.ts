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

const listWith = (props: Record<string, unknown>, rowStub: unknown = true) =>
  mount(RecoveryUserList, {
    props: {title: "Inactive accounts", users, actionType: "activation", startOpen: true, ...props},
    global: {stubs: {...vuetifyStubs, RecoveryUserRow: rowStub}},
  })

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
    expect((wrapper.vm as any).countLabel).toBe("1 / 2")
  })

  it("says so when the search matches nobody", async () => {
    const wrapper = listWith({})

    await wrapper.find("input").setValue("nobody-by-that-name")

    expect(wrapper.get("[data-testid='recovery-user-list-empty-inactive-accounts']").text()).toContain("No")
  })

  it.each([
    ["a click", "click"],
    ["the enter key", "keydown.enter"],
    ["the space bar", "keydown.space"],
  ])("opens and shuts on %s", async (_name, event) => {
    const wrapper = listWith({startOpen: false})
    const toggle = wrapper.get("[data-testid='recovery-user-list-toggle-inactive-accounts']")

    await toggle.trigger(event)
    expect(toggle.attributes("aria-expanded")).toBe("true")

    await toggle.trigger(event)
    expect(toggle.attributes("aria-expanded")).toBe("false")
  })

  it("is named by the key it was given rather than by its title", () => {
    const wrapper = listWith({panelKey: "inactive"})

    expect(wrapper.find("[data-testid='recovery-user-list-toggle-inactive']").exists()).toBe(true)
  })

  // Each row takes the one activation its account is waiting for, and none where there is none.
  it("hands each row the activation its account is waiting for", () => {
    const wrapper = listWith({pendingActivations: {1: "MEMBER_ACTIVATION"}}, {
      props: ["user", "pendingActivation"],
      template: "<div class='row'>{{ user.username }}:{{ pendingActivation }}</div>",
    })

    const rows = wrapper.findAll(".row").map(one => one.text())
    expect(rows).toEqual(["lyndisluna:MEMBER_ACTIVATION", "ariosfury:"])
  })

  it("passes a row's finished action up to the manager", async () => {
    const wrapper = listWith({}, {
      template: "<button @click=\"$emit('action:done')\" />",
    })

    await wrapper.find("button").trigger("click")

    expect(wrapper.emitted("action:done")).toHaveLength(1)
  })

  it("keys an account the api answered without a number by its username", () => {
    const wrapper = listWith({users: [{username: "ariosfury", fullName: "Viktor", roles: []}]})

    expect(wrapper.text()).toContain("Inactive accounts")
  })
})
