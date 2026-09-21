import {describe, expect, it} from "vitest"
import {markdown, markdownLanguage} from "@codemirror/lang-markdown"
import {EditorSelection, EditorState} from "@codemirror/state"
import {EditorView} from "@codemirror/view"
import {markdownLive, wrapWith} from "@/components/island/markdownLive"

const editor = (doc: string, at: number) => {
  const view = new EditorView({
    state: EditorState.create({
      doc,
      selection: EditorSelection.cursor(at),
      extensions: [markdown({base: markdownLanguage}), markdownLive],
    }),
  })
  // jsdom lays nothing out, so the view has to be told what it can see.
  return view
}

describe("wrapWith", () => {
  it("wraps what is selected", () => {
    const view = editor("go to Ameland", 0)
    view.dispatch({selection: EditorSelection.range(6, 13)})

    wrapWith(view, "**")

    expect(view.state.doc.toString()).toBe("go to **Ameland**")
    view.destroy()
  })

  it("unwraps what is already wrapped, rather than wrapping it twice", () => {
    const view = editor("go to **Ameland**", 0)
    view.dispatch({selection: EditorSelection.range(8, 15)})

    wrapWith(view, "**")

    expect(view.state.doc.toString()).toBe("go to Ameland")
    view.destroy()
  })

  it("keeps the selection on the words it wrapped", () => {
    const view = editor("go to Ameland", 0)
    view.dispatch({selection: EditorSelection.range(6, 13)})

    wrapWith(view, "*")

    expect(view.state.sliceDoc(view.state.selection.main.from, view.state.selection.main.to))
      .toBe("Ameland")
    view.destroy()
  })
})
