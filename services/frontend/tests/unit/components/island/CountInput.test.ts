import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import CountInput from "@/components/island/CountInput.vue"

const field = (props: Record<string, unknown> = {}) => mount(CountInput, {props: {testid: "limit", ...props}})

describe("a count with steppers", () => {
  it("says what an empty count means, in the box", () => {
    expect(field().find("input").attributes("placeholder")).toBe("No limit")
    expect(field({empty: "Anyone"}).find("input").attributes("placeholder")).toBe("Anyone")
  })

  it("steps up from empty to the smallest count, and back down to empty", async () => {
    const wrapper = field({min: 1})

    await wrapper.get("[data-testid=limit-more]").trigger("click")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["1"])
    await wrapper.get("[data-testid=limit-more]").trigger("click")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["2"])
    await wrapper.get("[data-testid=limit-fewer]").trigger("click")
    await wrapper.get("[data-testid=limit-fewer]").trigger("click")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([""])
  })

  it("offers no fewer than empty", async () => {
    const wrapper = field()

    expect((wrapper.get("[data-testid=limit-fewer]").element as HTMLButtonElement).disabled).toBe(true)
    await wrapper.get("[data-testid=limit-fewer]").trigger("click")
    expect(wrapper.emitted("update:modelValue")).toBeUndefined()
  })

  it("reads a typed number, empties on an empty box, and leaves anything else for the rule", async () => {
    const wrapper = field()

    await wrapper.find("input").setValue("024")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["24"])
    await wrapper.find("input").setValue("")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([""])
    await wrapper.find("input").setValue("twelve")
    expect(wrapper.emitted("update:modelValue")).toHaveLength(2)
  })

  it("says it is wrong where the form says so, and steps nothing while off", () => {
    expect(field({invalid: true}).find(".island-count").classes()).toContain("island-count--wrong")
    const off = field({disabled: true, modelValue: "3"})
    expect(off.findAll("button").every(one => (one.element as HTMLButtonElement).disabled)).toBe(true)
    expect(mount(CountInput).find("[data-testid]").exists()).toBe(false)
  })
})
