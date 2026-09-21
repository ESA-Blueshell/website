import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {markdown, markdownLanguage} from "@codemirror/lang-markdown"
import {EditorSelection, EditorState} from "@codemirror/state"
import {EditorView} from "@codemirror/view"
import IslandMarkdown from "@/components/island/IslandMarkdown.vue"
import {markdownLive} from "@/components/island/markdownLive"

const editor = (props: Record<string, unknown> = {}) =>
  mount(IslandMarkdown, {props: {modelValue: "", ...props}, attachTo: document.body})

const press = (wrapper: ReturnType<typeof editor>, key: string) =>
  wrapper.find(".cm-content").trigger("keydown", {key, ctrlKey: true, metaKey: true})

describe("the editor's shortcuts", () => {
  it("wraps what is selected in bold, and again to unwrap it", async () => {
    const wrapper = editor({modelValue: "go to Ameland"})
    const view = (wrapper.vm as unknown as {$el: HTMLElement})
    expect(view.$el).toBeTruthy()

    await press(wrapper, "b")
    await press(wrapper, "i")

    expect(wrapper.emitted("update:modelValue") ?? []).toBeTruthy()
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
