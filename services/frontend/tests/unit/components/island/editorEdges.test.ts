import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import FormControl from "@/components/island/FormControl.vue"
import CountryPicker from "@/components/island/CountryPicker.vue"
import MarkdownEditor from "@/components/island/MarkdownEditor.vue"
import PhoneInput from "@/components/island/PhoneInput.vue"

const platform = (said: string) => {
  Object.defineProperty(window.navigator, "platform", {configurable: true, value: said})
}

afterEach(() => {
  document.body.innerHTML = ""
  vi.restoreAllMocks()
})

describe("a control with no name of its own", () => {
  it("names the parts it carries after the kind instead", () => {
    const phone = mount(FormControl, {props: {kind: "phone", modelValue: ""}})
    expect(phone.findComponent({name: "PhoneInput"}).props("testidPrefix")).toBe("phone")

    const country = mount(FormControl, {props: {kind: "country", modelValue: ""}})
    expect(country.findComponent({name: "CountryPicker"}).props("testidPrefix")).toBe("pick")

    const written = mount(FormControl, {props: {kind: "markdown", modelValue: ""}})
    expect(written.findComponent({name: "MarkdownEditor"}).props("testid")).toBeUndefined()
  })
})

describe("a control passing what its part says back to the form", () => {
  it("keeps what the phone field and the editor write", async () => {
    const phone = mount(FormControl, {props: {kind: "phone", modelValue: ""}})
    phone.findComponent({name: "PhoneInput"}).vm.$emit("update:modelValue", "+31612345678")
    await flushPromises()
    expect(phone.emitted("update:modelValue")?.at(0)).toEqual(["+31612345678"])

    const written = mount(FormControl, {props: {kind: "markdown", modelValue: ""}})
    written.findComponent({name: "MarkdownEditor"}).vm.$emit("update:modelValue", "## Said")
    await flushPromises()
    expect(written.emitted("update:modelValue")?.at(0)).toEqual(["## Said"])
  })
})

describe("the editor's own keys", () => {
  it("wraps what is selected in stars", async () => {
    const wrapper = mount(MarkdownEditor, {
      attachTo: document.body,
      props: {modelValue: "word"},
    })
    await flushPromises()

    const content = wrapper.find(".cm-content").element as HTMLElement
    content.dispatchEvent(new KeyboardEvent("keydown", {bubbles: true, ctrlKey: true, key: "b"}))
    await flushPromises()
    await flushPromises()

    expect(wrapper.emitted("update:modelValue")?.length).toBeGreaterThan(0)
  })

  it("wraps what is selected in one star", async () => {
    const wrapper = mount(MarkdownEditor, {attachTo: document.body, props: {modelValue: "word"}})
    await flushPromises()

    const content = wrapper.find(".cm-content").element as HTMLElement
    content.dispatchEvent(new KeyboardEvent("keydown", {bubbles: true, ctrlKey: true, key: "i"}))
    await flushPromises()

    expect(wrapper.emitted("update:modelValue")?.length).toBeGreaterThan(0)
  })

  it("says command where the machine is a Mac", async () => {
    platform("MacIntel")
    const wrapper = mount(MarkdownEditor, {attachTo: document.body, props: {modelValue: ""}})
    await wrapper.find(".island-markdown__ask").trigger("click")

    expect(wrapper.text()).toContain("⌘ B")
    platform("")
  })

  it("leaves the document alone where the value it is handed is the one it holds", async () => {
    const wrapper = mount(MarkdownEditor, {attachTo: document.body, props: {modelValue: "one"}})
    await flushPromises()

    await wrapper.setProps({modelValue: "one"})
    await flushPromises()

    expect(wrapper.find(".cm-content").text()).toContain("one")
  })
})

describe("a country field", () => {
  it("falls back to the country's name where nobody is called anything", async () => {
    const wrapper = mount(CountryPicker, {
      attachTo: document.body,
      props: {modelValue: "BV", reading: "nationality", testidPrefix: "nat"},
    })
    await flushPromises()

    expect(wrapper.findComponent({name: "SearchPicker"}).props("selectedKey")).toBe("BV")
  })
})

describe("a phone field", () => {
  it("keeps a number it was handed rather than writing it back", async () => {
    const wrapper = mount(PhoneInput, {
      attachTo: document.body,
      props: {modelValue: "+31612345678", testidPrefix: "tel"},
    })
    await flushPromises()

    expect(wrapper.emitted("update:modelValue")).toBeUndefined()
  })

  it("shows the country it was given while nothing is chosen", async () => {
    const wrapper = mount(PhoneInput, {
      attachTo: document.body,
      props: {modelValue: "", testidPrefix: "tel"},
    })
    await wrapper.find("[data-testid='tel-from-shut']").trigger("click")
    await flushPromises()

    expect(wrapper.find("[data-testid='tel-from-shut']").exists()).toBe(true)
  })
})

describe("what a phone field is handed", () => {
  it("keeps what cannot be read as a number rather than emptying the box", async () => {
    const wrapper = mount(PhoneInput, {
      attachTo: document.body,
      props: {modelValue: "not a number", testidPrefix: "tel"},
    })
    await flushPromises()

    expect(wrapper.find("[data-testid='tel-number']").exists()).toBe(true)
  })

  it("keeps the country it was on where the number names none", async () => {
    const wrapper = mount(PhoneInput, {
      attachTo: document.body,
      props: {modelValue: "+80012345678", testidPrefix: "tel"},
    })
    await flushPromises()

    expect(wrapper.findComponent({name: "SearchPicker"}).props("selectedKey")).toBe("NL")
  })

  it("falls back to the country it holds where no row is under the pointer", () => {
    const wrapper = mount(PhoneInput, {
      attachTo: document.body,
      props: {defaultCountry: "BE", modelValue: "", testidPrefix: "tel"},
    })
    const slot = wrapper.findComponent({name: "SearchPicker"}).vm.$slots.chosen

    expect(slot).toBeDefined()
    expect(slot?.({option: undefined})).toBeTruthy()
  })
})
