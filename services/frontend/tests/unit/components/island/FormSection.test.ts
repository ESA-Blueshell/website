import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import FormSection from "@/components/island/FormSection.vue"
import FormFields from "@/components/island/FormFields.vue"

describe("FormSection", () => {
  it("heads its fields with the section's title", () => {
    const wrapper = mount(FormSection, {
      props: {title: "The event", testid: "event-section"},
      slots: {default: "<p class=\"inside\">Fields</p>"},
    })

    expect(wrapper.attributes("data-testid")).toBe("event-section")
    expect(wrapper.find("h2").text()).toBe("The event")
    expect(wrapper.find(".inside").text()).toBe("Fields")
  })

  it("carries no testid when it is given none", () => {
    expect(mount(FormSection, {props: {title: "Discord"}}).attributes("data-testid")).toBeUndefined()
  })
})

describe("FormFields", () => {
  it("lays out what it holds, a full-row field among them", () => {
    const wrapper = mount(FormFields, {
      slots: {default: "<div class=\"one\" /><div class=\"form-span two\" />"},
    })

    expect(wrapper.classes()).toContain("form-fields")
    expect(wrapper.findAll(":scope > div")).toHaveLength(2)
    expect(wrapper.find(".form-span").exists()).toBe(true)
  })
})
