import {afterEach, describe, expect, it} from "vitest"
import {syntaxTree} from "@codemirror/language"
import {EditorSelection, EditorState} from "@codemirror/state"
import {EditorView} from "@codemirror/view"
import {markdownEditing} from "@/components/island/markdownEditing"
import $markdownToHtml from "@/plugins/markdownToHtml"

const views: EditorView[] = []

afterEach(() => {
  for (const view of views.splice(0)) view.destroy()
})

const open = (doc: string, at = 0) => {
  const view = new EditorView({
    parent: document.body,
    state: EditorState.create({doc, selection: EditorSelection.cursor(at), extensions: markdownEditing}),
  })
  views.push(view)
  view.contentDOM.focus()
  view.dispatch({selection: EditorSelection.cursor(at)})
  return view
}

const nodesOf = (doc: string): string[] => {
  const names: string[] = []
  syntaxTree(EditorState.create({doc, extensions: markdownEditing})).iterate({enter: node => {
    names.push(node.name)
  }})
  return names
}

/* The editor and the page read each rule alike: what the tree calls a span, the page draws as
   its element. */
const ALIKE: [string, string, string][] = [
  ["**done! **", "StrongEmphasis", "<strong>"],
  ["**bold**", "StrongEmphasis", "<strong>"],
  ["***both***", "StrongEmphasis", "<strong>"],
  ["*slanted*", "Emphasis", "<em>"],
  ["_slanted_", "Emphasis", "<em>"],
  ["__under__", "Underline", "<u>"],
  ["~~struck~~", "Strikethrough", "<del>"],
  ["||hidden||", "Spoiler", "class=\"spoiler\""],
  ["-# small", "Subtext", "class=\"subtext\""],
]

describe("the editor and the page read Discord's rules alike", () => {
  it.each(ALIKE)("%s", (source, node, element) => {
    expect(nodesOf(source)).toContain(node)
    expect($markdownToHtml(source)).toContain(element)
  })

  it.each([["~kept~", "Strikethrough", "<del>"], ["snake_case_name", "Emphasis", "<em>"],
    ["a ^b^", "Superscript", "<sup>"]])("reads %s as plain text", (source, node, element) => {
    expect(nodesOf(source)).not.toContain(node)
    expect($markdownToHtml(source)).not.toContain(element)
  })
})

describe("what the editor draws of the dialect", () => {
  it("hides an underline's and a spoiler's marks while the cursor is elsewhere", () => {
    const view = open("__under__ and ||hidden|| end", 27)

    expect(view.contentDOM.textContent).toBe("under and hidden end")
  })

  it("shows a spoiler's marks while the cursor is in it", () => {
    expect(open("say ||hidden|| end", 7).contentDOM.textContent).toBe("say ||hidden|| end")
  })

  it("keeps subtext's mark on the line the cursor is on, and hides it elsewhere", () => {
    const view = open("-# small\n\nbelow", 4)

    expect(view.contentDOM.querySelector(".cm-line")?.textContent).toBe("-# small")
    view.dispatch({selection: EditorSelection.cursor(15)})
    expect(view.contentDOM.querySelector(".cm-line")?.textContent).toBe("small")
  })

  it("ends a paragraph at a line of subtext", () => {
    expect(nodesOf("above\n-# small")).toContain("Subtext")
  })

  it("draws a standard emoji as its Noto picture, and the character where that will not load", () => {
    const view = open("hot 🔥 take", 0)
    const drawn = view.contentDOM.querySelector<HTMLImageElement>("img.cm-emoji")

    expect(drawn?.getAttribute("src")).toBe("/emoji/1f525.svg")
    drawn?.dispatchEvent(new Event("error"))
    expect(view.contentDOM.textContent).toBe("hot 🔥 take")
  })

  it("leaves an emoji in code as the character", () => {
    expect(open("`🔥`", 0).contentDOM.querySelector("img.cm-emoji")).toBeNull()
  })
})
