import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import IslandCountry from "@/components/island/IslandCountry.vue"

const country = (props: Record<string, unknown> = {}) =>
  mount(IslandCountry, {props: {modelValue: null, testidPrefix: "country", ...props}})

const picker = (wrapper: ReturnType<typeof country>) =>
  wrapper.findComponent({name: "IslandPicker"})

const rows = (wrapper: ReturnType<typeof country>) =>
  picker(wrapper).props("options") as Array<{key: string; label: string; terms: string[]}>

describe("IslandCountry", () => {
  it("opens on the Netherlands, and writes that down rather than assuming it", () => {
    const wrapper = country()

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe("NL")
  })

  it("leaves an answer the form already holds alone", () => {
    const wrapper = country({modelValue: "DE"})

    expect(wrapper.emitted("update:modelValue")).toBeUndefined()
    expect(picker(wrapper).props("selectedKey")).toBe("DE")
  })

  it("treats an empty string as no answer at all", () => {
    expect(country({modelValue: ""}).emitted("update:modelValue")?.at(-1)?.[0]).toBe("NL")
  })

  it("says a place one way and a person another", () => {
    const place = rows(country({modelValue: "NL"})).find(one => one.key === "NL")
    const person = rows(country({modelValue: "NL", reading: "nationality"}))
      .find(one => one.key === "NL")

    expect(place?.label).toBe("Netherlands")
    expect(person?.label).toBe("Dutch")
  })

  it("is found by either name, either demonym and every code it has", () => {
    const dutch = rows(country({modelValue: "NL"})).find(one => one.key === "NL")

    expect(dutch?.terms).toEqual(expect.arrayContaining(["netherlands", "dutch", "nl", "nld"]))
  })

  it("shows what was chosen in the box, in the reading it was asked for", () => {
    expect(picker(country({modelValue: "DE"})).props("placeholder")).toBe("Germany")
    expect(picker(country({modelValue: "DE", reading: "nationality"})).props("placeholder"))
      .toBe("German")
  })

  it("asks for what it wants while it holds nothing, in its own words or the form's", () => {
    // The form holds the answer, so a value the test does not feed back leaves the field empty.
    expect(picker(country()).props("placeholder")).toBe("Search countries")
    expect(picker(country({reading: "nationality"})).props("placeholder"))
      .toBe("Search nationalities")
    expect(picker(country({placeholder: "Where you were born"})).props("placeholder"))
      .toBe("Where you were born")
  })

  it("shows a code it does not know as itself, rather than as nothing", () => {
    expect(picker(country({modelValue: "ZZ"})).props("placeholder")).toBe("ZZ")
  })

  it("reports the code that was chosen, and is off when the field is", () => {
    const wrapper = country({modelValue: "NL", disabled: true})

    expect(picker(wrapper).props("disabled")).toBe(true)

    picker(wrapper).vm.$emit("pick", "BE")

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe("BE")
  })

  it("draws the flag of whatever row the picker is showing, and of its own answer otherwise", () => {
    const wrapper = country({modelValue: "NL"})
    const drawn = picker(wrapper).vm.$slots.lead?.({option: {key: "DE"}})

    expect(drawn).toBeTruthy()
  })
})
