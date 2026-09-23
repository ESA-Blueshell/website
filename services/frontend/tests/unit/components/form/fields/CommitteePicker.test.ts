import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import CommitteePicker from "@/components/form/fields/CommitteePicker.vue"

const committees = [{id: 2, name: "4FunCie"}, {id: 3, name: "SiteCie"}]

describe("CommitteePicker", () => {
  it("offers the committees it was handed, by name, and passes on the one picked", () => {
    const wrapper = mount(CommitteePicker, {props: {committees, modelValue: 3, testid: "committee"}})
    const picker = wrapper.getComponent({name: "SearchPicker"})

    expect(picker.props("options")).toEqual([{key: "2", label: "4FunCie"}, {key: "3", label: "SiteCie"}])
    expect(picker.props("selectedKey")).toBe("3")
    picker.vm.$emit("pick", "2")
    expect(wrapper.emitted("update:modelValue")).toEqual([[2]])
  })

  it("is off while there is nothing to pick, and says what is wrong", () => {
    const wrapper = mount(CommitteePicker, {props: {committees: [], errorMessages: ["Pick a committee"]}})

    expect(wrapper.getComponent({name: "SearchPicker"}).props("disabled")).toBe(true)
    expect(wrapper.getComponent({name: "SearchPicker"}).props("selectedKey")).toBeNull()
    expect(wrapper.text()).toContain("Pick a committee")
  })
})
