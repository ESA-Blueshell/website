import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import SignUpRoster from "@/domains/events/island/SignUpRoster.vue"

const rows = [
  {signUp: {id: 11, kind: "MEMBER"} as never, person: {name: "Alice", discord: "alice#1", email: "a@x", phoneNumber: "1"}, answers: new Map()},
  {signUp: {id: 12, kind: "GUEST", guest: {name: "Bob"}} as never, person: {name: "Bob", discord: "", email: "b@x", phoneNumber: ""}, answers: new Map()},
  {signUp: {id: 13, kind: "NON_MEMBER"} as never, person: {name: "Cara", discord: "", email: "c@x", phoneNumber: ""}, answers: new Map()},
]

describe("the roster", () => {
  it("numbers each person, names their kind in its own tone and offers the board edit and remove", async () => {
    const wrapper = mount(SignUpRoster, {props: {rows, mayManage: true, sortSaid: "as signed up", sortOn: false}})

    expect(wrapper.findAll("tbody tr .roster__number").map(one => one.text())).toEqual(["1", "2", "3"])
    expect(wrapper.get("[data-testid=signup-kind-12]").classes()).toContain("roster__kind--guest")
    expect(wrapper.get("[data-testid=signup-kind-13]").classes()).toContain("roster__kind--quiet")
    // An account's sign-up to an event that asks nothing has nothing to edit; a guest has details.
    expect((wrapper.get("[data-testid=signup-edit-btn-11]").element as HTMLButtonElement).disabled).toBe(true)
    expect((wrapper.get("[data-testid=signup-edit-btn-12]").element as HTMLButtonElement).disabled).toBe(false)

    await wrapper.get("[data-testid=signup-edit-btn-12]").trigger("click")
    await wrapper.get("[data-testid=signup-remove-btn-11]").trigger("click")
    await wrapper.get("[data-testid=signups-kind-sort]").trigger("click")
    expect(wrapper.emitted("edit")?.[0]?.[0]).toMatchObject({signUp: {id: 12}})
    expect(wrapper.emitted("remove")?.[0]?.[0]).toMatchObject({signUp: {id: 11}})
    expect(wrapper.emitted("sort")).toHaveLength(1)
  })

  it("lets the board edit an account's sign-up where the event asks questions", () => {
    const wrapper = mount(SignUpRoster, {props: {rows, mayManage: true, hasForm: true, sortSaid: "", sortOn: true}})

    expect((wrapper.get("[data-testid=signup-edit-btn-11]").element as HTMLButtonElement).disabled).toBe(false)
    expect(wrapper.get("[data-testid=signups-kind-sort]").classes()).toContain("roster__sort--on")
  })

  it("draws no actions for a reader who is not the board", () => {
    const wrapper = mount(SignUpRoster, {props: {rows, sortSaid: "", sortOn: false}})

    expect(wrapper.find("[data-testid=signup-edit-btn-11]").exists()).toBe(false)
    expect(wrapper.findAll("thead th")).toHaveLength(6)
  })
})
