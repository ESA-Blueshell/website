import {describe, expect, it} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {markdown, markdownLanguage} from "@codemirror/lang-markdown"
import {EditorSelection, EditorState} from "@codemirror/state"
import {EditorView} from "@codemirror/view"
import MarkdownEditor from "@/components/island/MarkdownEditor.vue"
import {markdownLive} from "@/components/island/markdownLive"

const editor = (props: Record<string, unknown> = {}) =>
  mount(MarkdownEditor, {props: {modelValue: "", ...props}, attachTo: document.body})

const press = (wrapper: ReturnType<typeof editor>, key: string) =>
  wrapper.find(".cm-content").trigger("keydown", {key, ctrlKey: true})

describe("the editor's shortcuts", () => {
  it("writes bold marks where the cursor is, for what is typed next", async () => {
    const wrapper = editor({modelValue: "go to Ameland"})

    await press(wrapper, "b")

    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["****go to Ameland"])
    wrapper.unmount()
  })
})

describe("the editor inside a form", () => {
  it("says so once the writer leaves it, so the form can judge it", async () => {
    const wrapper = editor()
    const content = wrapper.find(".cm-content").element as HTMLElement

    content.focus()
    content.blur()
    await flushPromises()

    expect(wrapper.emitted("blur")).toHaveLength(1)
    wrapper.unmount()
  })

  it("points at what the form says about it, and says when it is wrong", async () => {
    const wrapper = editor({describedBy: "said-1"})
    const content = () => wrapper.find(".cm-content")

    expect(content().attributes("aria-describedby")).toBe("said-1")
    expect(content().attributes("aria-invalid")).toBeUndefined()
    await wrapper.setProps({invalid: true})
    expect(content().attributes("aria-invalid")).toBe("true")
    wrapper.unmount()
  })

  it("keeps a stored value longer than the cap whole, rather than cutting it", async () => {
    const wrapper = editor({maxLength: 5})

    await wrapper.setProps({modelValue: "longer than five"})
    await flushPromises()

    expect(wrapper.emitted("update:modelValue")).toBeUndefined()
    expect(wrapper.find(".cm-content").text()).toContain("longer than five")
    wrapper.unmount()
  })
})

describe("the editor once it is gone", () => {
  it("does nothing with a value the form sets after it was taken down", async () => {
    const wrapper = editor({modelValue: "first"})

    wrapper.unmount()
    await wrapper.setProps({modelValue: "second"})

    expect(wrapper.emitted("update:modelValue")).toBeUndefined()
  })
})

describe("what the editor draws while it is being written in", () => {
  const written = (doc: string, at: number) => {
    const view = new EditorView({
      parent: document.body,
      state: EditorState.create({
        doc,
        selection: EditorSelection.cursor(at),
        extensions: [markdown({base: markdownLanguage}), markdownLive],
      }),
    })
    view.contentDOM.focus()
    view.dispatch({selection: EditorSelection.cursor(at)})
    const said = view.contentDOM.textContent ?? ""
    view.destroy()
    return said
  }

  it("shows the marks on the line the cursor is on", () => {
    expect(written("## Heading\n\nplain", 3)).toContain("##")
  })

  it("keeps drawing the emoji and the bullet while the line is written", () => {
    expect(written("- one :fire: two", 4)).toContain("•")
  })

  it("shows the marks across a selection that spans lines", () => {
    const view = new EditorView({
      parent: document.body,
      state: EditorState.create({
        doc: "## One\n\n**two**",
        selection: EditorSelection.range(0, 15),
        extensions: [markdown({base: markdownLanguage}), markdownLive],
      }),
    })
    view.contentDOM.focus()
    view.dispatch({selection: EditorSelection.range(0, 15)})

    expect(view.contentDOM.textContent).toContain("**")
    view.destroy()
  })
})

describe("the count of what a description holds", () => {
  it("shows only near the cap, counting what is stored, and says when it is over", async () => {
    const wrapper = editor({maxLength: 10, testid: "note"})
    expect(wrapper.find("[data-testid=note-count]").exists()).toBe(false)

    await wrapper.setProps({modelValue: "123456789"})
    await flushPromises()
    expect(wrapper.get("[data-testid=note-count]").text()).toBe("9/10")
    expect(wrapper.get("[data-testid=note-count]").classes()).not.toContain("island-markdown__count--over")

    await wrapper.setProps({modelValue: "12345678901"})
    await flushPromises()
    expect(wrapper.get("[data-testid=note-count]").classes()).toContain("island-markdown__count--over")
    wrapper.unmount()
  })

  it("holds a description to Discord's 4096 characters where no cap is given, and counts nothing where the field counts itself", async () => {
    const wrapper = editor({modelValue: "a".repeat(4000), counted: false})
    await flushPromises()
    expect(wrapper.find(".island-markdown__count").exists()).toBe(false)
    wrapper.unmount()

    const counted = editor({modelValue: "a".repeat(4000)})
    await flushPromises()
    expect(counted.get(".island-markdown__count").text()).toBe("4000/4096")
    counted.unmount()
  })
})
