import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import MoneyInput from "@/components/island/MoneyInput.vue"

const field = (props: Record<string, unknown> = {}) =>
  mount(MoneyInput, {props: {testid: "fee", ...props}})

describe("an amount in euros", () => {
  it("shows what it was handed, under the euro sign", () => {
    const wrapper = field({modelValue: "30.00"})

    expect((wrapper.find("input").element).value).toBe("30.00")
    expect(wrapper.find(".island-money__sign").text()).toBe("€")
  })

  it("takes a comma for the decimal point, and drops anything that is not a number", async () => {
    const wrapper = field()

    await wrapper.find("input").setValue("12,5")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["12.5"])

    await wrapper.find("input").setValue("-9a8")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["98"])
  })

  it("keeps two places at most, however many are typed", async () => {
    const wrapper = field()

    await wrapper.find("input").setValue("10.999")

    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["10.99"])
  })

  it("writes the amount out in full once the field is left", async () => {
    const wrapper = field()

    await wrapper.find("input").setValue("7.5")
    await wrapper.find("input").trigger("blur")

    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["7.50"])
    expect((wrapper.find("input").element).value).toBe("7.50")
  })

  it("leaves an empty field empty rather than writing nothing down as zero", async () => {
    const wrapper = field({modelValue: "5.00"})

    await wrapper.find("input").setValue("")
    await wrapper.find("input").trigger("blur")

    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([""])
  })

  it("takes nothing as an amount in its own right", async () => {
    const wrapper = field()

    await wrapper.find("input").setValue("0")
    await wrapper.find("input").trigger("blur")

    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["0.00"])
  })

  it("says it is wrong where the form says so, and carries no name of its own where it has none",
    () => {
      expect(field({invalid: true}).find(".island-money").classes()).toContain("island-money--wrong")
      expect(mount(MoneyInput).find("input").attributes("data-testid")).toBeUndefined()
    })
})
