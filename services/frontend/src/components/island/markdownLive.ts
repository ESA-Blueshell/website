import {syntaxTree} from "@codemirror/language"
import {EditorSelection, type EditorState, type Range} from "@codemirror/state"
import {Decoration, type DecorationSet, EditorView, ViewPlugin, type ViewUpdate, WidgetType}
  from "@codemirror/view"
import * as emoji from "node-emoji"

/**
 * What markdown looks like while it is written: the marks are hidden on every line the cursor
 * is not on, and the document itself is untouched.
 */

/** The nodes whose text is punctuation: the marks that say what a stretch of text is. */
/** What a list item is written with, and what it is drawn as once it is being read. */
const BULLETS = new Set(["-", "*", "+"])
const BULLET = "\u2022"

const MARKS = new Set([
  "HeaderMark",
  "EmphasisMark",
  "StrongEmphasisMark",
  "StrikethroughMark",
  "CodeMark",
  "QuoteMark",
  "LinkMark",
  "URL",
])

/* Only while the editor is written in: a cursor rests at the start whether or not anybody
   is there. */
const openLines = (state: EditorState, writing: boolean): Set<number> => {
  const lines = new Set<number>()
  if (!writing) return lines
  for (const range of state.selection.ranges) {
    const from = state.doc.lineAt(range.from).number
    const to = state.doc.lineAt(range.to).number
    for (let line = from; line <= to; line++) lines.add(line)
  }
  return lines
}

const hidden = Decoration.replace({})

/* The space after a `##` or a `>` belongs to the mark, or the line starts one space in. */
const SPACED = new Set(["HeaderMark", "QuoteMark"])

const endOf = (state: EditorState, name: string, to: number): number => {
  if (!SPACED.has(name)) return to
  let end = to
  while (end < state.doc.length && state.sliceDoc(end, end + 1) === " ") end++
  return end
}

/* The document keeps `:spaghetti:`, which is what the renderer turns into a character. */
class Character extends WidgetType {
  constructor(private readonly said: string, private readonly as = "") {
    super()
  }

  eq(other: Character): boolean {
    return other.said === this.said && other.as === this.as
  }

  toDOM(): HTMLElement {
    const drawn = document.createElement("span")
    drawn.textContent = this.said
    if (this.as !== "") drawn.className = this.as
    return drawn
  }

  ignoreEvent(): boolean {
    return false
  }
}

/** `:name:`, which is how a description writes an emoji down. */
const SHORTCODE = /:([a-z0-9_+-]+):/gi

const emojiIn = (view: EditorView, open: Set<number>): Range<Decoration>[] => {
  const drawn: Range<Decoration>[] = []
  for (const {from, to} of view.visibleRanges) {
    const text = view.state.sliceDoc(from, to)
    for (const found of text.matchAll(SHORTCODE)) {
      const at = from + found.index
      if (open.has(view.state.doc.lineAt(at).number)) continue
      const said = emoji.get(found[1] as string)
      if (!said) continue
      drawn.push(Decoration.replace({widget: new Character(said)}).range(at, at + found[0].length))
    }
  }
  return drawn
}

const decorate = (view: EditorView): DecorationSet => {
  const open = openLines(view.state, view.hasFocus)
  const marks: Range<Decoration>[] = []

  for (const {from, to} of view.visibleRanges) {
    syntaxTree(view.state).iterate({
      from,
      to,
      enter: (node) => {
        const line = view.state.doc.lineAt(node.from).number

        // A dash is what a list is typed with, a bullet what it looks like, and it stays a
        // bullet on the line being written: nobody needs reminding they typed a dash.
        if (node.name === "ListMark") {
          if (!BULLETS.has(view.state.sliceDoc(node.from, node.to))) return
          marks.push(Decoration.replace({widget: new Character(BULLET, "cm-bullet")})
            .range(node.from, node.to))
          return
        }

        if (!MARKS.has(node.name)) return
        if (open.has(line)) return
        // A link's address is hidden with its brackets, so what is left is the text alone.
        if (node.name === "URL" && node.node.parent?.name !== "Link") return
        marks.push(hidden.range(node.from, endOf(view.state, node.name, node.to)))
      },
    })
  }
  return Decoration.set([...marks, ...emojiIn(view, open)], true)
}

/** Hides the marks, and redraws whenever the document, the view or the cursor moves. */
export const markdownLive = ViewPlugin.fromClass(
  class {
    decorations: DecorationSet

    constructor(view: EditorView) {
      this.decorations = decorate(view)
    }

    update(update: ViewUpdate) {
      if (update.docChanged || update.viewportChanged || update.selectionSet || update.focusChanged) {
        this.decorations = decorate(update.view)
      }
    }
  },
  {decorations: plugin => plugin.decorations},
)

/** Wraps what is selected in a pair of marks, or unwraps it where it is already wrapped. */
export const wrapWith = (view: EditorView, mark: string): boolean => {
  const changes = view.state.changeByRange((range) => {
    const around = view.state.sliceDoc(range.from - mark.length, range.to + mark.length)
    const inside = view.state.sliceDoc(range.from, range.to)

    if (around.startsWith(mark) && around.endsWith(mark) && around.length >= mark.length * 2) {
      return {
        changes: [
          {from: range.from - mark.length, to: range.from},
          {from: range.to, to: range.to + mark.length},
        ],
        range: EditorSelection.range(range.from - mark.length, range.to - mark.length),
      }
    }
    return {
      changes: {from: range.from, to: range.to, insert: `${mark}${inside}${mark}`},
      range: EditorSelection.range(range.from + mark.length, range.to + mark.length),
    }
  })
  view.dispatch(changes, {scrollIntoView: true, userEvent: "input.wrap"})
  return true
}
