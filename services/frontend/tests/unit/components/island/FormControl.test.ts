import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import FormControl from "@/components/island/FormControl.vue"

const control = (props: Record<string, unknown> = {}, attrs: Record<string, unknown> = {}) =>
  mount(FormControl, {props: {modelValue: "", ...props}, attrs})

describe("FormControl", () => {
  it("draws the control the form asked for by kind", () => {
    expect(control().findComponent({name: "TextInput"}).exists()).toBe(true)
    expect(control({kind: "textarea"}).findComponent({name: "TextArea"}).exists()).toBe(true)
    expect(control({kind: "markdown"}).findComponent({name: "MarkdownEditor"}).exists()).toBe(true)
    expect(control({kind: "phone"}).findComponent({name: "PhoneInput"}).exists()).toBe(true)
    expect(control({kind: "country"}).findComponent({name: "CountryPicker"}).exists()).toBe(true)
  })

  it("reads a nationality as the nationality of a country", () => {
    expect(control({kind: "nationality"}).findComponent({name: "CountryPicker"}).props("reading"))
      .toBe("nationality")
    expect(control({kind: "country"}).findComponent({name: "CountryPicker"}).props("reading"))
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
    expect(control({kind: "country", modelValue: ""}).findComponent({name: "CountryPicker"})
      .props("modelValue")).toBeNull()
    expect(control({kind: "country", modelValue: "NL"}).findComponent({name: "CountryPicker"})
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
    const wrapper = control({}, {maxlength: "8"})

    expect(wrapper.find("input").attributes("maxlength")).toBe("8")
  })

  it("draws the island's own calendar wherever a form asked for a date", () => {
    const byKind = control({kind: "date"})
    const byType = control({}, {type: "date", min: "2026-01-01", max: "2026-12-31"})

    expect(byKind.findComponent({name: "DateInput"}).exists()).toBe(true)
    expect(byType.findComponent({name: "DateInput"}).exists()).toBe(true)
    expect(byType.findComponent({name: "DateInput"}).props("min")).toBe("2026-01-01")
    expect(byType.findComponent({name: "DateInput"}).props("max")).toBe("2026-12-31")
    // The browser's own date control is not asked for, so the type never reaches the input.
    expect(byType.find("input").attributes("type")).toBe("text")
  })

  it("draws one box for a day and a time wherever a form asked for a moment", () => {
    const byKind = mount(FormControl, {props: {kind: "datetime", modelValue: ""}})
    const byType = mount(FormControl, {
      props: {modelValue: "2026-10-03T19:30", label: "Start time"},
      attrs: {type: "datetime-local", min: "2026-10-01T00:00", "data-testid": "starts"},
    })

    expect(byKind.findComponent({name: "DateTimeInput"}).exists()).toBe(true)
    expect(byType.findComponent({name: "DateTimeInput"}).props("min")).toBe("2026-10-01T00:00")
    expect(byType.findComponent({name: "DateTimeInput"}).props("testid")).toBe("starts-when")
    // Its placeholders show whether or not it holds a moment, so the label rises either way.
    expect(byKind.find(".island-field").classes()).toContain("island-field--filled")
  })

  it("passes on the moment and the count it was given", async () => {
    const moment = mount(FormControl, {props: {kind: "datetime", modelValue: ""}})
    moment.findComponent({name: "DateTimeInput"}).vm.$emit("update:modelValue", "2026-10-03T19:30")
    expect(moment.emitted("update:modelValue")?.at(-1)).toEqual(["2026-10-03T19:30"])

    const count = mount(FormControl, {props: {kind: "count", modelValue: ""}, attrs: {"data-testid": "limit"}})
    expect(count.findComponent({name: "CountInput"}).props("testid")).toBe("limit-count")
    count.findComponent({name: "CountInput"}).vm.$emit("update:modelValue", "24")
    count.findComponent({name: "CountInput"}).vm.$emit("blur")
    await count.vm.$nextTick()
    expect(count.emitted("update:modelValue")?.at(-1)).toEqual(["24"])
    expect(count.emitted("blur")).toHaveLength(1)
  })

  it("names neither a moment nor a count where the form names the field nothing", () => {
    expect(mount(FormControl, {props: {kind: "datetime"}}).findComponent({name: "DateTimeInput"}).props("testid")).toBeUndefined()
    expect(mount(FormControl, {props: {kind: "count"}}).findComponent({name: "CountInput"}).props("testid")).toBeUndefined()
  })

  it("starts a phone field's label where the number is written", () => {
    expect(control({kind: "phone"}).attributes("style")).toContain("--field-label-left")
    expect(control({kind: "country"}).attributes("style")).toContain("--field-label-left")
    expect(control({kind: "text"}).attributes("style") ?? "").not.toContain("--field-label-left")
  })

  it("reports what is typed, and passes on the country a number is read as", async () => {
    const wrapper = control({kind: "phone"})

    wrapper.findComponent({name: "PhoneInput"}).vm.$emit("update:country", "DE")
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

describe("what a date field reports", () => {
  it("passes on the day it was given, and the moment it is left", async () => {
    const wrapper = mount(FormControl, {props: {kind: "date", modelValue: ""}})
    const date = wrapper.findComponent({name: "DateInput"})

    date.vm.$emit("update:modelValue", "2026-03-07")
    date.vm.$emit("blur")
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted("update:modelValue")?.at(0)).toEqual(["2026-03-07"])
    expect(wrapper.emitted("blur")).toBeTruthy()
  })
})

describe("what a time and a price field report", () => {
  it("passes on what they were given, and the moment they are left", async () => {
    const clock = mount(FormControl, {props: {kind: "time", modelValue: ""}})
    clock.findComponent({name: "TimeInput"}).vm.$emit("update:modelValue", "19:30")
    clock.findComponent({name: "TimeInput"}).vm.$emit("blur")
    await clock.vm.$nextTick()

    expect(clock.emitted("update:modelValue")?.at(0)).toEqual(["19:30"])
    expect(clock.emitted("blur")).toBeTruthy()

    const price = mount(FormControl, {props: {kind: "money", modelValue: ""}})
    price.findComponent({name: "MoneyInput"}).vm.$emit("update:modelValue", "30.00")
    price.findComponent({name: "MoneyInput"}).vm.$emit("blur")
    await price.vm.$nextTick()

    expect(price.emitted("update:modelValue")?.at(0)).toEqual(["30.00"])
    expect(price.emitted("blur")).toBeTruthy()
  })

  it("names the time and the price for a test, and leaves them unnamed where the field is", () => {
    const named = mount(FormControl, {props: {kind: "time", modelValue: "", testid: "starts"}})
    const bare = mount(FormControl, {props: {kind: "money", modelValue: ""}})

    const pricedByName = mount(FormControl, {props: {kind: "money", modelValue: "", testid: "fee"}})

    expect(named.findComponent({name: "TimeInput"}).props("testid")).toBe("starts-time")
    expect(pricedByName.findComponent({name: "MoneyInput"}).props("testid")).toBe("fee-money")
    expect(bare.findComponent({name: "MoneyInput"}).props("testid")).toBeUndefined()
  })

  it("gives a price field the same lead cell a phone number has", () => {
    expect(mount(FormControl, {props: {kind: "money", modelValue: ""}}).attributes("style"))
      .toContain("--field-label-left")
  })
})
