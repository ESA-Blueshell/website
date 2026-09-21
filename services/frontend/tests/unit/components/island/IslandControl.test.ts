import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import IslandControl from "@/components/island/IslandControl.vue"

const control = (props: Record<string, unknown> = {}, attrs: Record<string, unknown> = {}) =>
  mount(IslandControl, {props: {modelValue: "", ...props}, attrs})

describe("IslandControl", () => {
  it("draws the control the form asked for by kind", () => {
    expect(control().findComponent({name: "IslandInput"}).exists()).toBe(true)
    expect(control({kind: "textarea"}).findComponent({name: "IslandTextarea"}).exists()).toBe(true)
    expect(control({kind: "markdown"}).findComponent({name: "IslandMarkdown"}).exists()).toBe(true)
    expect(control({kind: "phone"}).findComponent({name: "IslandPhone"}).exists()).toBe(true)
    expect(control({kind: "country"}).findComponent({name: "IslandCountry"}).exists()).toBe(true)
  })

  it("reads a nationality as the nationality of a country", () => {
    expect(control({kind: "nationality"}).findComponent({name: "IslandCountry"}).props("reading"))
      .toBe("nationality")
    expect(control({kind: "country"}).findComponent({name: "IslandCountry"}).props("reading"))
      .toBe("country")
  })

  it("reads the star off a label rather than printing it twice", () => {
    const starred = control({label: "Phone Number*"})
    const plain = control({label: "Nickname"})

    expect(starred.find("label").text()).toBe("Phone Number*")
    expect(starred.find(".island-field__must").exists()).toBe(true)
    expect(plain.find(".island-field__must").exists()).toBe(false)
  })

  it("says the first thing wrong with it, given one sentence or several", () => {
    expect(control({errorMessages: "No @ in it."}).find(".island-field__said").text())
      .toBe("No @ in it.")
    expect(control({errorMessages: ["First.", "Second."]}).find(".island-field__said").text())
      .toBe("First.")
    expect(control({hint: "We will not share it."}).find(".island-field__said").text())
      .toBe("We will not share it.")
  })

  it("marks a field that holds something, whichever kind it is", () => {
    expect(control({modelValue: "typed"}).classes()).toContain("island-field--filled")
    expect(control({modelValue: ""}).classes()).not.toContain("island-field--filled")
    expect(control({modelValue: null}).classes()).not.toContain("island-field--filled")
  })

  it("hands a picker null rather than an empty string, which is a value", () => {
    expect(control({kind: "country", modelValue: ""}).findComponent({name: "IslandCountry"})
      .props("modelValue")).toBeNull()
    expect(control({kind: "country", modelValue: "NL"}).findComponent({name: "IslandCountry"})
      .props("modelValue")).toBe("NL")
  })

  it("puts a name the form hands down on the field, not on the input inside it", () => {
    const named = control({testid: "user-form-email-field"})
    const attributed = control({}, {"data-testid": "email-confirm-address-field"})

    expect(named.attributes("data-testid")).toBe("user-form-email-field")
    expect(attributed.attributes("data-testid")).toBe("email-confirm-address-field")
    expect(attributed.find("input").attributes("data-testid")).toBeUndefined()
  })

  it("passes everything else the form set straight to the input", () => {
    const wrapper = control({}, {type: "date", maxlength: "8"})

    expect(wrapper.find("input").attributes("maxlength")).toBe("8")
  })

  it("starts a phone field's label where the number is written", () => {
    expect(control({kind: "phone"}).attributes("style")).toContain("--field-label-left")
    expect(control({kind: "country"}).attributes("style")).toContain("--field-label-left")
    expect(control({kind: "text"}).attributes("style") ?? "").not.toContain("--field-label-left")
  })

  it("reports what is typed, and passes on the country a number is read as", async () => {
    const wrapper = control({kind: "phone"})

    wrapper.findComponent({name: "IslandPhone"}).vm.$emit("update:country", "DE")
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted("update:country")?.at(-1)?.[0]).toBe("DE")
  })

  it("says when it is left, which is when a form judges a field", async () => {
    const wrapper = control()

    await wrapper.find("input").trigger("blur")

    expect(wrapper.emitted("blur")).toHaveLength(1)
  })

  it("is off in every kind when the form is", () => {
    for (const kind of ["text", "textarea", "phone", "country"] as const) {
      expect(control({kind, disabled: true}).html()).toContain("disabled")
    }
    // The editor has no disabled attribute of its own: what it has is a document nobody may write in.
    expect(control({kind: "markdown", disabled: true}).find(".cm-content").attributes("contenteditable"))
      .toBe("false")
  })
})
