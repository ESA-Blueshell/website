import {afterEach, describe, expect, it} from "vitest"
import {EditorSelection, EditorState} from "@codemirror/state"
import {EditorView, runScopeHandlers} from "@codemirror/view"
import {undo} from "@codemirror/commands"
import {markdownEditing, replaceFromOutside} from "@/components/island/markdownEditing"
import {wrapWith} from "@/components/island/markdownLive"

const views: EditorView[] = []

afterEach(() => {
  for (const view of views.splice(0)) view.destroy()
})

const open = (doc: string, selection: EditorSelection | number = 0, focused = true) => {
  const at = typeof selection === "number" ? EditorSelection.cursor(selection) : selection
  const view = new EditorView({
    parent: document.body,
    state: EditorState.create({doc, selection: at, extensions: markdownEditing}),
  })
  views.push(view)
  if (focused) {
    view.contentDOM.focus()
    view.dispatch({selection: at})
  }
  return view
}

/* What a reader sees, an emoji picture read as the emoji it draws. */
const drawn = (view: EditorView) => {
  const copy = view.contentDOM.cloneNode(true) as HTMLElement
  for (const art of copy.querySelectorAll("img")) art.replaceWith(art.alt)
  return copy.textContent ?? ""
}
const lineOf = (view: EditorView, n: number) =>
  view.contentDOM.querySelectorAll(".cm-line")[n]?.textContent ?? ""

const press = (view: EditorView, key: string, mod = false) =>
  runScopeHandlers(view, new KeyboardEvent("keydown", {key, ctrlKey: mod}), "editor")

describe("marks shown per span", () => {
  it("hides a bold span's marks while the cursor is elsewhere on its line", () => {
    const view = open("plain **bold** and more", 2)

    expect(drawn(view)).toBe("plain bold and more")
  })

  it("shows a span's marks while the cursor touches it", () => {
    const doc = "plain **bold** and *more*"

    expect(drawn(open(doc, 9))).toBe("plain **bold** and more")
    expect(drawn(open(doc, 6))).toBe("plain **bold** and more")
    expect(drawn(open(doc, doc.length))).toBe("plain bold and *more*")
  })

  it("draws a span as it is closed, while the cursor stands after it", () => {
    const view = open("a **b", 5)
    view.dispatch({changes: {from: 5, insert: "**"}, selection: EditorSelection.cursor(7)})
    view.dispatch({changes: {from: 7, insert: " c"}, selection: EditorSelection.cursor(9)})

    expect(drawn(view)).toBe("a b c")
  })

  it("keeps a heading's marks shown on the whole line the cursor is on", () => {
    const view = open("### Title\n\nbelow", 7)

    expect(lineOf(view, 0)).toBe("### Title")
    view.dispatch({selection: EditorSelection.cursor(15)})
    expect(lineOf(view, 0)).toBe("Title")
  })

  it("shows every mark while nobody is writing, hides them all", () => {
    expect(drawn(open("**a** *b* `c` ~~d~~", 0, false))).toBe("a b c d")
  })

  it("hides a picture's address and keeps what it says", () => {
    const said = drawn(open("see ![the map](https://x.io/map.png) here", 0))

    expect(said).toBe("see the map here")
  })

  it("shows a link whole while the cursor is in it", () => {
    expect(drawn(open("[go](https://x.io) now", 2))).toBe("[go](https://x.io) now")
  })

  it("hides both brackets of a link that points at a reference", () => {
    expect(drawn(open("[go] now\n\n[go]: https://x.io", 7))).toContain("go now")
  })

  it("hides a link's title with its address", () => {
    expect(drawn(open("[go](https://x.io \"a title\") now", 29))).toBe("go now")
  })

  it("hides a fence's language with the fence while the cursor is outside the block", () => {
    const view = open("above\n```js\nlet a\n```", 0)

    expect(drawn(view)).not.toContain("js")
    expect(drawn(view)).toContain("let a")
    view.dispatch({selection: EditorSelection.cursor(14)})
    expect(drawn(view)).toContain("```js")
  })

  it("leaves a shortcode as the text it is wherever the cursor is", () => {
    expect(drawn(open("hot :fire: take", 7))).toBe("hot :fire: take")
    expect(drawn(open("hot :fire: take", 1))).toBe("hot :fire: take")
  })
})

describe("the bold and italic keys", () => {
  it("italicises what is selected, rather than selecting the node around it", () => {
    const view = open("one word", EditorSelection.range(4, 8))

    expect(press(view, "i", true)).toBe(true)
    expect(view.state.doc.toString()).toBe("one *word*")
  })

  it("keeps what was written when italic is pressed with nothing selected", () => {
    const view = open("one word", 8)

    press(view, "i", true)
    view.dispatch(view.state.replaceSelection("x"))

    expect(view.state.doc.toString()).toBe("one word*x*")
  })

  it("unbolds a span the cursor is inside", () => {
    const view = open("go **to** Ameland", 6)

    press(view, "b", true)

    expect(view.state.doc.toString()).toBe("go to Ameland")
    expect(view.state.selection.main.head).toBe(4)
  })

  it("unbolds a selection that holds its own marks, rather than wrapping it twice", () => {
    const view = open("**word**", EditorSelection.range(0, 8))

    press(view, "b", true)

    expect(view.state.doc.toString()).toBe("word")
  })

  it("italicises inside bold, rather than taking a star from the bold", () => {
    const view = open("**bold**", EditorSelection.range(2, 6))

    press(view, "i", true)

    expect(view.state.doc.toString()).toBe("***bold***")
  })

  it("unitalicises bold italic and leaves it bold", () => {
    const view = open("***bold***", EditorSelection.range(3, 7))

    press(view, "i", true)

    expect(view.state.doc.toString()).toBe("**bold**")
  })

  it("wraps at the start of the document without reading before it", () => {
    const view = open("word", EditorSelection.range(0, 4))

    wrapWith(view, "**")

    expect(view.state.doc.toString()).toBe("**word**")
  })
})

describe("lists", () => {
  it("ends the list when Enter is pressed on an empty item", () => {
    const view = open("- item", 6)

    press(view, "Enter")
    press(view, "Enter")
    view.dispatch(view.state.replaceSelection("after"))

    expect(view.state.doc.toString()).toBe("- item\nafter")
  })

  it("continues the list on Enter after an item", () => {
    const view = open("- item", 6)

    press(view, "Enter")

    expect(view.state.doc.toString()).toBe("- item\n- ")
  })
})

describe("a value set from outside", () => {
  it("is not undone by the writer's undo", () => {
    const view = open("typed", 5)
    view.dispatch(view.state.replaceSelection("!"))
    view.dispatch(replaceFromOutside(view.state, "loaded"))

    undo(view)

    expect(view.state.doc.toString()).toBe("loaded")
  })

  it("changes only what differs, so text on either side is left where it was", () => {
    const view = open("one two three", 13)

    view.dispatch(replaceFromOutside(view.state, "one 2 three"))

    expect(view.state.doc.toString()).toBe("one 2 three")
    expect(view.state.selection.main.head).toBe(11)
  })

  it("keeps the cursor where the text around it did not change", () => {
    const view = open("one two three", 4)

    view.dispatch(replaceFromOutside(view.state, "one two three four"))

    expect(view.state.selection.main.head).toBe(4)
  })
})
