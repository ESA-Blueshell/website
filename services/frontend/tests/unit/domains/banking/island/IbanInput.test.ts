import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import IbanInput from "@/domains/banking/island/IbanInput.vue"
import BicInput from "@/domains/banking/island/BicInput.vue"
import {settle} from "../../../helpers/testUtils"

describe("the IBAN input", () => {
  const typeInto = async (wrapper: ReturnType<typeof mount>, value: string, inputType = "insertText") => {
    const input = wrapper.get("input")
    const element = input.element as HTMLInputElement
    element.value = value
    element.setSelectionRange(value.length, value.length)
    await input.trigger("input", {inputType})
    await settle()
  }

  it("keeps only what fits each position, groups it in fours and draws the rest of the template", async () => {
    const wrapper = mount(IbanInput, {props: {modelValue: "", testid: "iban"}, attachTo: document.body})

    await typeInto(wrapper, "nl91 abna-0417")
    expect(wrapper.emitted("update:modelValue")!.at(-1)).toEqual(["NL91ABNA0417"])
    await wrapper.setProps({modelValue: "NL91ABNA0417"})
    expect((wrapper.get("input").element as HTMLInputElement).value).toBe("NL91 ABNA 0417 ")
    expect(wrapper.get('[data-testid="iban-template"]').text()).toBe("NL91 ABNA 0417 4567 89")

    await typeInto(wrapper, "NL91 ABNA 0417 ", "deleteContentBackward")
    expect(wrapper.emitted("update:modelValue")!.at(-1)).toEqual(["NL91ABNA041"])
    wrapper.unmount()
  })

  it("keeps the caret after the same characters when typing in the middle, and says when it is left", async () => {
    const left = vi.fn()
    const wrapper = mount(IbanInput, {props: {modelValue: "NL91ABNA0417", testid: "iban"}, attrs: {onBlur: left}, attachTo: document.body})
    const input = wrapper.get("input")
    const element = input.element as HTMLInputElement
    element.value = "NL91 AXBNA 0417 "
    element.setSelectionRange(7, 7)
    await input.trigger("input", {inputType: "insertText"})
    await settle()
    expect(wrapper.emitted("update:modelValue")!.at(-1)).toEqual(["NL91AXBN0417"])

    await input.trigger("blur")
    expect(left).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it("shows the country's flag once its letters are in", async () => {
    const wrapper = mount(IbanInput, {props: {modelValue: "DE89"}})
    expect(wrapper.findComponent({name: "CountryFlag"}).exists()).toBe(true)
    await wrapper.setProps({modelValue: ""})
    expect(wrapper.text()).toContain("IBAN")
  })
})

describe("the BIC input", () => {
  it("groups a BIC as it is built", async () => {
    const wrapper = mount(BicInput, {props: {modelValue: "COBADEFF", country: "DE", testid: "bic"}})
    expect((wrapper.get("input").element as HTMLInputElement).value).toBe("COBA DE FF ")
  })
})
