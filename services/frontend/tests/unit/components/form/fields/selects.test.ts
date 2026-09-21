import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import CountrySelect from "@/components/form/fields/CountrySelect.vue"
import EnumPicker from "@/components/form/fields/EnumPicker.vue"
import MemberTypeSelect from "@/components/form/fields/MemberTypeSelect.vue"

const stubs = {FormField: {template: "<div><slot /></div>"}}
const control = (wrapper: ReturnType<typeof mount>) => wrapper.findComponent({name: "FormControl"})
const picker = (wrapper: ReturnType<typeof mount>) => wrapper.findComponent({name: "SearchPicker"})

describe("CountrySelect", () => {
  it("keeps a code, matches a name, and holds nothing where there is nothing", () => {
    expect(control(mount(CountrySelect, {props: {modelValue: "de"}, global: {stubs}}))
      .props("modelValue")).toBe("DE")
    expect(control(mount(CountrySelect, {props: {modelValue: "Netherlands"}, global: {stubs}}))
      .props("modelValue")).toBe("NL")
    expect(control(mount(CountrySelect, {props: {modelValue: "  "}, global: {stubs}}))
      .props("modelValue")).toBeNull()
    expect(control(mount(CountrySelect, {props: {modelValue: "Atlantis"}, global: {stubs}}))
      .props("modelValue")).toBeNull()
  })

  it("reports the code chosen, and takes the label it is given", () => {
    const wrapper = mount(CountrySelect, {
      props: {modelValue: null, label: "Where you live", testId: "home"},
      global: {stubs},
    })

    expect(control(wrapper).props("label")).toBe("Where you live")
    expect(control(wrapper).props("testid")).toBe("home")

    control(wrapper).vm.$emit("update:modelValue", "BE")

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe("BE")
  })

  it("falls back to its own label when the form names none", () => {
    expect(control(mount(CountrySelect, {props: {modelValue: "NL"}, global: {stubs}}))
      .props("label")).toBe("Country")
  })
})

describe("EnumPicker", () => {
  it("says a value the way a person would, and is found by the api's own spelling", () => {
    const wrapper = mount(EnumPicker, {
      props: {values: ["CONTRIBUTION_PAID", "member.ended"], modelValue: "member.ended"},
      global: {stubs},
    })

    expect(picker(wrapper).props("options")).toEqual([
      {key: "CONTRIBUTION_PAID", label: "Contribution paid", terms: ["CONTRIBUTION_PAID"]},
      {key: "member.ended", label: "Member ended", terms: ["member.ended"]},
    ])
    expect(picker(wrapper).props("selectedKey")).toBe("member.ended")
  })

  it("reports what was picked, and holds nothing until something is", () => {
    const wrapper = mount(EnumPicker, {props: {values: ["A"], modelValue: undefined}, global: {stubs}})

    expect(picker(wrapper).props("selectedKey")).toBeNull()

    picker(wrapper).vm.$emit("pick", "A")

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe("A")
  })

  it("takes a label, a name and a refusal from the form", () => {
    const wrapper = mount(EnumPicker, {
      props: {values: [], modelValue: undefined, label: "Status", required: true,
        disabled: true, testid: "status", errorMessages: ["Say which."]},
    })

    expect(wrapper.find(".island-field__said").text()).toBe("Say which.")
    expect(picker(wrapper).props("disabled")).toBe(true)
    expect(picker(wrapper).props("testidPrefix")).toBe("status")
  })

  it("names its rows itself when the form gives it no name", () => {
    const wrapper = mount(EnumPicker, {props: {values: ["A"], modelValue: "A"}, global: {stubs}})

    expect(picker(wrapper).props("testidPrefix")).toBe("enum-picker")
  })
})

describe("MemberTypeSelect", () => {
  it("offers every kind of member the api knows, said the way a person would", () => {
    const wrapper = mount(MemberTypeSelect, {props: {modelValue: "ALUMNI"}, global: {stubs}})
    const rows = picker(wrapper).props("options") as Array<{key: string; label: string}>

    expect(rows.length).toBeGreaterThan(1)
    expect(rows.every(one => one.label === `${one.key.charAt(0)}${one.key.slice(1).toLowerCase()}`))
      .toBe(true)
    expect(picker(wrapper).props("selectedKey")).toBe("ALUMNI")
  })

  it("reports the kind that was chosen", () => {
    const wrapper = mount(MemberTypeSelect, {props: {modelValue: "ALUMNI"}, global: {stubs}})

    picker(wrapper).vm.$emit("pick", "MEMBER")

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe("MEMBER")
  })

  it("shows a refusal, and is off when the form is", () => {
    const wrapper = mount(MemberTypeSelect, {
      props: {modelValue: "ALUMNI", disabled: true, testid: "kind", errorMessages: "Pick one."},
    })

    expect(wrapper.find(".island-field__said").text()).toBe("Pick one.")
    expect(picker(wrapper).props("disabled")).toBe(true)
    expect(picker(wrapper).props("testidPrefix")).toBe("kind")
  })

  it("opens on alumni and names its rows itself when the form says nothing", () => {
    const wrapper = mount(MemberTypeSelect, {props: {}, global: {stubs}})

    expect(picker(wrapper).props("selectedKey")).toBe("ALUMNI")
    expect(picker(wrapper).props("testidPrefix")).toBe("member-type")
  })
})
