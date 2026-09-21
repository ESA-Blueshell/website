import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import IslandField from "@/components/island/IslandField.vue"

const field = (props: Record<string, unknown> = {}) =>
  mount(IslandField, {
    props: {label: "Full name", ...props},
    slots: {default: '<input data-testid="control">'},
  })

describe("IslandField", () => {
  it("names the control", () => {
    expect(field().find("label").text()).toContain("Full name")
  })

  it("marks what has to be answered", () => {
    expect(field().find(".island-field__must").exists()).toBe(false)
    expect(field({required: true}).find(".island-field__must").text()).toBe("*")
  })

  it("says the hint until there is something wrong to say instead", () => {
    expect(field({hint: "As on your card"}).find(".island-field__said").text()).toBe("As on your card")

    const wrong = field({hint: "As on your card", error: "That name is empty."})

    expect(wrong.find(".island-field__said").text()).toBe("That name is empty.")
    expect(wrong.find(".island-field__said").classes()).toContain("island-field__said--wrong")
  })

  it("hands the control an id and tells it it is wrong", () => {
    const slot = mount(IslandField, {
      props: {label: "Email", error: "No @ in it."},
      slots: {default: '<span :data-invalid="params.invalid" :data-for="params.controlId" />'},
    })

    expect(slot.find("span").attributes("data-invalid")).toBe("true")
    expect(slot.find("span").attributes("data-for")).toBe(slot.find("label").attributes("for"))
  })
})
