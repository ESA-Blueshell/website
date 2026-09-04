import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import UserSelect from "@/components/form/fields/UserSelect.vue"

const alice = {id: 7, fullName: "Alice", roles: ["MEMBER"]}

function mountSelect(users: unknown[], modelValue?: number) {
  return shallowMount(UserSelect, {props: {users, modelValue}})
}

function selected(wrapper: ReturnType<typeof mountSelect>) {
  return wrapper.findComponent({name: "VAutocomplete"}).props("modelValue")
}

describe("UserSelect", () => {
  it("shows the picked user once the list arrives after mount", async () => {
    const wrapper = mountSelect([], alice.id)
    expect(selected(wrapper)).toBeFalsy()

    await wrapper.setProps({users: [alice]})

    expect(selected(wrapper)).toMatchObject({id: alice.id, fullName: "Alice"})
  })

  it("shows the picked user when the list is already there", () => {
    expect(selected(mountSelect([alice], alice.id))).toMatchObject({id: alice.id})
  })

  it("stays empty when nothing was picked", async () => {
    const wrapper = mountSelect([], undefined)

    await wrapper.setProps({users: [alice]})

    expect(selected(wrapper)).toBeFalsy()
  })
})
