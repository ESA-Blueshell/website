import {describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import UserSelect from "@/components/form/fields/UserSelect.vue"
import type {UserDetailResponse} from "@/services/api"

vi.mock("@/domains/user", () => ({searchMemberAccounts: vi.fn()}))

const someone = (id: number, name: string) => ({id, firstName: name, lastName: "Jansen"}) as
  UserDetailResponse

const stubs = {IslandField: {template: "<div><slot /></div>"}}

describe("a user field whose list arrives after the value", () => {
  it("resolves the value against the page it is handed", async () => {
    const wrapper = mount(UserSelect, {props: {modelValue: undefined, users: []}, global: {stubs}})

    await wrapper.setProps({modelValue: 7, users: [someone(7, "Joris")]})
    await flushPromises()

    expect(wrapper.findComponent({name: "IslandPicker"}).props("selectedKey")).toBe("7")
  })

  it("keeps nobody chosen where the page holds nobody by that id", async () => {
    const wrapper = mount(UserSelect, {props: {modelValue: undefined, users: []}, global: {stubs}})

    await wrapper.setProps({modelValue: 3, users: [someone(2, "Anne")]})
    await flushPromises()

    expect(wrapper.findComponent({name: "IslandPicker"}).props("selectedKey")).toBeNull()
  })
})
