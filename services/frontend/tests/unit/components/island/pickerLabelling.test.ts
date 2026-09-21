import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import EnumPicker from "@/components/form/fields/EnumPicker.vue"
import FormControl from "@/components/island/FormControl.vue"

vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: vi.fn()}))

describe("a picker inside a field", () => {
  it("is named by the field's own label, and says it is a combobox", async () => {
    const wrapper = mount(EnumPicker, {
      attachTo: document.body,
      props: {label: "Member name", testid: "member", values: ["CONTRIBUTION_PAID"]},
    })

    const input = wrapper.find("input")
    const label = wrapper.find("label")

    expect(input.attributes("role")).toBe("combobox")
    expect(input.attributes("aria-expanded")).toBe("false")
    expect(input.attributes("aria-labelledby")).toBe(label.attributes("id"))
    expect(input.attributes("aria-label")).toBeUndefined()
    expect(label.attributes("for")).toBe(input.attributes("id"))

    await input.trigger("click")
    expect(wrapper.find("input").attributes("aria-expanded")).toBe("true")
  })

  it("names the country field the same way", () => {
    const wrapper = mount(FormControl, {
      attachTo: document.body,
      props: {kind: "country", label: "Country", modelValue: "NL"},
    })

    const input = wrapper.find("input")
    expect(input.attributes("aria-labelledby")).toBe(wrapper.find("label").attributes("id"))
    expect(input.attributes("role")).toBe("combobox")
  })
})
