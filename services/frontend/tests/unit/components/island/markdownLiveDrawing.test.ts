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

const linesOn = (doc: string) => {
  const view = new EditorView({
    parent: document.body,
    state: EditorState.create({doc, extensions: [markdown({base: markdownLanguage}), markdownLive]}),
  })
  const lines = [...view.contentDOM.querySelectorAll(".cm-line")].map(line => ({
    classes: line.className,
    code: line.querySelector(".cm-code")?.textContent,
  }))
  view.destroy()
  return lines
}

describe("what the editor draws", () => {
  it("marks a quote's lines and a code block's lines for their bar and box, and boxes inline code", () => {
    const lines = linesOn("> one\n> two\n\n```\ncode\n```\n\nsay `this`\n\n    indented")

    expect(lines.map(line => line.classes.replace("cm-line", "").trim())).toEqual([
      "cm-quote", "cm-quote", "", "cm-codeblock", "cm-codeblock", "cm-codeblock", "", "", "", "cm-codeblock",
    ])
    expect(lines[7]?.code).toBe("this")
  })

  it("ends a quote's bar where its `>` lines end, as Discord does, though markdown runs the quote on", () => {
    const lines = linesOn("> one\n>> two\nlazy")

    expect(lines.map(line => line.classes.replace("cm-line", "").trim())).toEqual(["cm-quote", "cm-quote", ""])
  })

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

  it("leaves a shortcode as the text it is, as Discord does", () => {
    expect(drawnOn("we are :fire: about it")).toContain(":fire:")
  })

  it("hides a link's address but keeps what it says", () => {
    const said = drawnOn("Ask in [the Discord](https://discord.gg/x) about it")

    expect(said).toContain("the Discord")
    expect(said).not.toContain("https://discord.gg/x")
  })

  it("leaves a bare address alone, since it is the text as well as the target", () => {
    expect(drawnOn("Go to <https://example.com> for it")).toContain("https://example.com")
  })

  it("lets a press on a bullet reach the line it stands on", () => {
    const view = new EditorView({
      parent: document.body,
      state: EditorState.create({doc: "- one", extensions: [markdown({base: markdownLanguage}), markdownLive]}),
    })
    const bullet = view.contentDOM.querySelector(".cm-bullet") as HTMLElement
    const press = new MouseEvent("mousedown", {bubbles: true, cancelable: true})

    bullet.dispatchEvent(press)

    expect(view.contentDOM.textContent).toContain("•")
    view.destroy()
  })
})
