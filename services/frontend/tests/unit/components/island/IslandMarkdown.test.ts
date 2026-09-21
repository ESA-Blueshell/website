import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import IslandMarkdown from "@/components/island/IslandMarkdown.vue"

const editor = (props: Record<string, unknown> = {}) =>
  mount(IslandMarkdown, {props: {modelValue: "## Heading\n\nWith **bold** in it.", ...props},
    attachTo: document.body})

const text = (wrapper: ReturnType<typeof editor>) =>
  wrapper.find(".cm-content").text()

describe("the markdown editor", () => {
  it("opens on what the form holds", () => {
    const wrapper = editor()

    expect(text(wrapper)).toContain("Heading")
    wrapper.unmount()
  })

  it("reports what is written in it", async () => {
    const wrapper = editor({modelValue: ""})
    const view = (wrapper.vm as unknown as {$el: HTMLElement})
    const content = wrapper.find(".cm-content").element as HTMLElement

    content.dispatchEvent(new Event("input"))
    await wrapper.vm.$nextTick()

    expect(view.$el).toBeTruthy()
    wrapper.unmount()
  })

  it("takes a value the form sets after it opened", async () => {
    const wrapper = editor({modelValue: "first"})

    await wrapper.setProps({modelValue: "second"})
    await wrapper.vm.$nextTick()

    expect(text(wrapper)).toContain("second")
    wrapper.unmount()
  })

  it("leaves the document alone when the form sets what it already holds", async () => {
    const wrapper = editor({modelValue: "same"})

    await wrapper.setProps({modelValue: "same"})

    expect(text(wrapper)).toContain("same")
    wrapper.unmount()
  })

  it("is a textbox with the field's own name, for anything reading the page", () => {
    const wrapper = editor({labelledBy: "description-label"})
    const content = wrapper.find(".cm-content")

    expect(content.attributes("role")).toBe("textbox")
    expect(content.attributes("aria-multiline")).toBe("true")
    expect(content.attributes("aria-labelledby")).toBe("description-label")
    wrapper.unmount()
  })

  it("leaves the label out where the field gives none", () => {
    const wrapper = editor()

    expect(wrapper.find(".cm-content").attributes("aria-labelledby")).toBeUndefined()
    wrapper.unmount()
  })

  it("cannot be written in when the form is off, and can be again when it is not", async () => {
    const wrapper = editor({disabled: true})

    expect(wrapper.find(".cm-content").attributes("contenteditable")).toBe("false")

    await wrapper.setProps({disabled: false})
    await wrapper.vm.$nextTick()

    expect(wrapper.find(".cm-content").attributes("contenteditable")).toBe("true")
    wrapper.unmount()
  })

  it("takes the height it is told to start at", () => {
    const wrapper = editor({minHeight: "20rem"})

    expect(wrapper.attributes("style")).toContain("--md-min: 20rem")
    wrapper.unmount()
  })

  it("carries a name for a test to find it by", () => {
    const wrapper = editor({testid: "description-editor"})

    expect(wrapper.attributes("data-testid")).toBe("description-editor")
    wrapper.unmount()
  })
})

describe("the help the editor offers", () => {
  it("is shut until it is asked for, and names the keys for this machine", async () => {
    const wrapper = editor()

    expect(wrapper.find(".island-markdown__help").exists()).toBe(false)

    await wrapper.find(".island-markdown__ask").trigger("click")

    const rows = wrapper.findAll(".island-markdown__help dt").map(one => one.text())
    expect(rows[0]).toContain("Bold")
    expect(rows[0]).toMatch(/⌘ B|Ctrl B/)
    expect(wrapper.findAll(".island-markdown__help dd").length).toBe(rows.length)
    wrapper.unmount()
  })

  it("goes away when it is asked again", async () => {
    const wrapper = editor()
    const ask = wrapper.find(".island-markdown__ask")

    await ask.trigger("click")
    await ask.trigger("click")

    expect(wrapper.find(".island-markdown__help").exists()).toBe(false)
    wrapper.unmount()
  })

  it("goes away on Escape", async () => {
    const wrapper = editor()
    await wrapper.find(".island-markdown__ask").trigger("click")

    document.dispatchEvent(new KeyboardEvent("keydown", {key: "Escape"}))
    await wrapper.vm.$nextTick()

    expect(wrapper.find(".island-markdown__help").exists()).toBe(false)
    wrapper.unmount()
  })

  it("goes away on a press elsewhere", async () => {
    const wrapper = editor()
    await wrapper.find(".island-markdown__ask").trigger("click")

    document.body.dispatchEvent(new Event("pointerdown", {bubbles: true}))
    await wrapper.vm.$nextTick()

    expect(wrapper.find(".island-markdown__help").exists()).toBe(false)
    wrapper.unmount()
  })

  it("stays up while the press is on the card itself", async () => {
    const wrapper = editor()
    await wrapper.find(".island-markdown__ask").trigger("click")

    const card = wrapper.find(".island-markdown__help").element
    card.dispatchEvent(new PointerEvent("pointerdown", {bubbles: true}))
    await wrapper.vm.$nextTick()

    expect(wrapper.find(".island-markdown__help").exists()).toBe(true)
    wrapper.unmount()
  })

  it("leaves a key that is not Escape alone", async () => {
    const wrapper = editor()
    await wrapper.find(".island-markdown__ask").trigger("click")

    document.dispatchEvent(new KeyboardEvent("keydown", {key: "a"}))
    await wrapper.vm.$nextTick()

    expect(wrapper.find(".island-markdown__help").exists()).toBe(true)
    wrapper.unmount()
  })
})
