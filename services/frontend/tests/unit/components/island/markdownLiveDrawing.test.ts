import {describe, expect, it} from "vitest"
import {markdown, markdownLanguage} from "@codemirror/lang-markdown"
import {EditorSelection, EditorState} from "@codemirror/state"
import {EditorView} from "@codemirror/view"
import {markdownLive} from "@/components/island/markdownLive"

const drawnOn = (doc: string, at = 0, focused = false) => {
  const view = new EditorView({
    parent: document.body,
    state: EditorState.create({
      doc,
      selection: EditorSelection.cursor(at),
      extensions: [markdown({base: markdownLanguage}), markdownLive],
    }),
  })
  if (focused) view.focus()
  const said = view.contentDOM.innerText ?? view.contentDOM.textContent ?? ""
  view.destroy()
  return said
}

describe("what the editor draws", () => {
  it("hides the marks on every line while nobody is writing in it", () => {
    const said = drawnOn("## Heading\n\nSome **bold** and *italic* and `code`.")

    expect(said).toContain("Heading")
    expect(said).not.toContain("##")
    expect(said).not.toContain("**")
    expect(said).not.toContain("`")
  })

  it("swallows the space a heading and a quote are written with", () => {
    expect(drawnOn("## Heading")).toContain("Heading")
    expect(drawnOn("## Heading").startsWith(" ")).toBe(false)
    expect(drawnOn("> Quoted").startsWith(" ")).toBe(false)
  })

  it("draws a dash, a star and a plus all as one bullet", () => {
    for (const mark of ["-", "*", "+"]) {
      expect(drawnOn(`${mark} one\n${mark} two`)).toContain("•")
    }
  })

  it("leaves a numbered list the number it is read by", () => {
    expect(drawnOn("1. first\n2. second")).toContain("1.")
  })

  it("draws a shortcode as the emoji it names, and leaves an unknown one alone", () => {
    expect(drawnOn("we are :fire: about it")).toContain("🔥")
    expect(drawnOn("we are :notanemoji: about it")).toContain(":notanemoji:")
  })

  it("hides a link's address but keeps what it says", () => {
    const said = drawnOn("Ask in [the Discord](https://discord.gg/x) about it")

    expect(said).toContain("the Discord")
    expect(said).not.toContain("https://discord.gg/x")
  })

  it("leaves a bare address alone, since it is the text as well as the target", () => {
    expect(drawnOn("Go to <https://example.com> for it")).toContain("https://example.com")
  })
})
