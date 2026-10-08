import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import CheckBox from "@/components/island/CheckBox.vue"

describe("CheckBox", () => {
  it("says its refusal under the label in place of the hint", () => {
    const wrapper = mount(CheckBox, {props: {label: "I agree", hint: "Read them first", errorMessages: ["Agree to continue."]}})

    expect(wrapper.get("[role=alert]").text()).toBe("Agree to continue.")
    expect(wrapper.text()).not.toContain("Read them first")
  })

  it("shows the hint while nothing is refused, and takes a label with a link from its slot", () => {
    const wrapper = mount(CheckBox, {props: {hint: "Read them first"}, slots: {label: "<a href='/terms'>the terms</a>"}})

    expect(wrapper.find("[role=alert]").exists()).toBe(false)
    expect(wrapper.text()).toContain("Read them first")
    expect(wrapper.get("a").attributes("href")).toBe("/terms")
  })
})
