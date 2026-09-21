import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import IslandCut from "@/components/island/IslandCut.vue"
import IslandControl from "@/components/island/IslandControl.vue"
import IslandField from "@/components/island/IslandField.vue"
import PanChevron from "@/components/island/PanChevron.vue"

/** What a scoped `v-bind` in CSS becomes: a variable set on the element itself. */
const cssVars = (wrapper: {attributes: (name: string) => string | undefined}) =>
  wrapper.attributes("style") ?? ""

describe("what the parts time their movement by", () => {
  it("gives the cut button's sweep a length", () => {
    expect(cssVars(mount(IslandCut, {props: {}, slots: {default: "Go"}}))).toContain("sweep")
  })

  it("gives the chevron's settle a length", () => {
    expect(cssVars(mount(PanChevron, {props: {way: "on", label: "On"}}))).toContain("settle")
  })

  it("gives the label's rise a length", () => {
    const wrapper = mount(IslandField, {
      props: {label: "Email", variant: "inside"}, slots: {default: "<input>"},
    })

    expect(cssVars(wrapper)).toContain("--rise")
  })
})

describe("the field's slot", () => {
  it("hands out an id, what says it, whether it is wrong, and the label's own id", () => {
    const wrapper = mount(IslandField, {
      props: {label: "Email", error: "No @ in it.", variant: "inside"},
      slots: {
        default: `<span
          :data-control="params.controlId"
          :data-said="params.describedBy"
          :data-wrong="String(params.invalid)"
          :data-label="params.labelId"
        />`,
      },
    })
    const said = wrapper.find("span")

    expect(said.attributes("data-control")).toBe(wrapper.find("label").attributes("for"))
    expect(said.attributes("data-label")).toBe(wrapper.find("label").attributes("id"))
    expect(said.attributes("data-said")).toBe(wrapper.find("p").attributes("id"))
    expect(said.attributes("data-wrong")).toBe("true")
  })
})

describe("what a control is named", () => {
  const control = (props: Record<string, unknown>) =>
    mount(IslandControl, {props: {modelValue: "", ...props}})

  it("names the parts under it after the field, where the field has a name", () => {
    expect(control({kind: "phone", testid: "user-form-phone"})
      .findComponent({name: "IslandPhone"}).props("testidPrefix")).toBe("user-form-phone-phone")
    expect(control({kind: "country", testid: "user-form-country"})
      .findComponent({name: "IslandCountry"}).props("testidPrefix")).toBe("user-form-country-pick")
    expect(control({kind: "markdown", testid: "user-form-about"})
      .findComponent({name: "IslandMarkdown"}).props("testid")).toBe("user-form-about-editor")
  })

  it("falls back to a name of its own where the field has none", () => {
    expect(control({kind: "phone"}).findComponent({name: "IslandPhone"}).props("testidPrefix"))
      .toBe("phone")
    expect(control({kind: "country"}).findComponent({name: "IslandCountry"}).props("testidPrefix"))
      .toBe("pick")
    expect(control({kind: "markdown"}).findComponent({name: "IslandMarkdown"}).props("testid"))
      .toBeUndefined()
  })

  it("passes the country a number is read as on to the form", async () => {
    const wrapper = control({kind: "phone", defaultCountry: "BE"})
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted("update:country")?.at(-1)?.[0]).toBe("BE")
  })

  it("reports what is typed in each kind that writes text", async () => {
    const text = control({})
    await text.find("input").setValue("typed")
    expect(text.emitted("update:modelValue")?.at(-1)?.[0]).toBe("typed")

    const long = control({kind: "textarea"})
    await long.find("textarea").setValue("written")
    expect(long.emitted("update:modelValue")?.at(-1)?.[0]).toBe("written")
  })

  it("reports the code a picker chose", async () => {
    const wrapper = control({kind: "country"})

    wrapper.findComponent({name: "IslandCountry"}).vm.$emit("update:modelValue", "FR")
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe("FR")
  })

  it("says it was left whichever kind of box it is", async () => {
    const long = control({kind: "textarea"})
    await long.find("textarea").trigger("blur")
    expect(long.emitted("blur")).toHaveLength(1)

    const phone = control({kind: "phone"})
    await phone.find(".island-phone").trigger("focusout")
    expect(phone.emitted("blur")).toHaveLength(1)
  })
})
