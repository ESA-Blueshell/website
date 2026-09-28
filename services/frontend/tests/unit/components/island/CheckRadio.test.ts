import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import CheckBox from "@/components/island/CheckBox.vue"
import RadioGroup from "@/components/island/RadioGroup.vue"

describe("CheckBox", () => {
  it("is a real checkbox, so the label and the keyboard work", async () => {
    const wrapper = mount(CheckBox, {props: {modelValue: false, label: "Send me the news"}})
    const box = wrapper.find('input[type="checkbox"]')

    expect(wrapper.find("label").attributes("for")).toBe(box.attributes("id"))

    await box.setValue(true)

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe(true)
  })
})

describe("RadioGroup", () => {
  const options = [{key: "yes", label: "Coming"}, {key: "no", label: "Not coming"}]

  it("reports which one was picked", async () => {
    const wrapper = mount(RadioGroup, {props: {modelValue: null, options, testid: "coming"}})

    await wrapper.find('[data-testid="coming-no"]').setValue(true)

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe("no")
  })

  it("groups them, so picking one lets the other go", () => {
    const wrapper = mount(RadioGroup, {props: {modelValue: "yes", options, name: "coming"}})
    const names = wrapper.findAll("input").map(one => one.attributes("name"))

    expect(new Set(names).size).toBe(1)
  })
})
