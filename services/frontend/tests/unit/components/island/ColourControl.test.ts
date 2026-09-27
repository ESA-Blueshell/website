import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import ColourControl from "@/components/island/ColourControl.vue"

const control = (props: Record<string, unknown> = {}) =>
  mount(ColourControl, {props: {label: "Highlight colour", testid: "accent", modelValue: "", ...props}})

const hex = (wrapper: ReturnType<typeof control>) => wrapper.get("[data-testid=accent-hex] input, input[data-testid=accent-hex]")
const swatch = (wrapper: ReturnType<typeof control>) => wrapper.get("[data-testid=accent-swatch]")

describe("ColourControl", () => {
  it("shows the colour in its swatch and as a hex, kept in step", async () => {
    const wrapper = control({modelValue: "#ff4655"})

    expect((swatch(wrapper).element as HTMLInputElement).value).toBe("#ff4655")
    expect((hex(wrapper).element as HTMLInputElement).value).toBe("#ff4655")

    await swatch(wrapper).setValue("#1183d6")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["#1183d6"])
  })

  it("takes six hex digits written without the hash, and empties to nothing", async () => {
    const wrapper = control()

    await hex(wrapper).setValue("b58863")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["#b58863"])
    await hex(wrapper).setValue("  ")
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([""])
  })

  it("says so when what is written is not a colour, and shows the placeholder in the swatch", () => {
    const wrapper = control({modelValue: "blue"})

    expect(wrapper.text()).toContain("Write a colour as # and six hex digits.")
    expect((swatch(wrapper).element as HTMLInputElement).value).toBe("#1f6feb")
    expect(control({modelValue: "#ff4655"}).text()).not.toContain("six hex digits")
  })

  it("puts the form's own error first, and names the swatch after its label", () => {
    const wrapper = control({modelValue: "#ff4655", errorMessages: ["Refused."]})

    expect(wrapper.text()).toContain("Refused.")
    expect(swatch(wrapper).attributes("aria-label")).toBe("Pick highlight colour")
    expect(control({label: ""}).get("input[type=color]").attributes("aria-label")).toBe("Pick a colour")
  })

  it("passes a blur on, and has no swatch testid without a testid", async () => {
    const wrapper = control({testid: undefined})
    await wrapper.get("input[type=text], input:not([type])").trigger("blur")

    expect(wrapper.emitted("blur")).toHaveLength(1)
    expect(wrapper.find("[data-testid$=-swatch]").exists()).toBe(false)
  })
})
