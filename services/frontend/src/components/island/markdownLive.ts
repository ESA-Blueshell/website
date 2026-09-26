import {syntaxTree} from "@codemirror/language"
import {EditorSelection, type EditorState, type Range, type SelectionRange} from "@codemirror/state"
import {Decoration, type DecorationSet, EditorView, ViewPlugin, type ViewUpdate, WidgetType}
  from "@codemirror/view"
import * as emoji from "node-emoji"

/* Read off the tree rather than imported, since @lezer/common is not a dependency of its own. */
type SyntaxNode = ReturnType<ReturnType<typeof syntaxTree>["resolveInner"]>

/* The marks are hidden wherever the cursor is not; the document is untouched. */

/** What a list item is written with, and what it is drawn as once it is being read. */
const BULLETS = new Set(["-", "*", "+"])
const BULLET = "•"

/** A span whose marks show only while the cursor touches it, and the nodes that are its marks. */
const SPANS: Record<string, Set<string>> = {
  Emphasis: new Set(["EmphasisMark"]),
  StrongEmphasis: new Set(["EmphasisMark"]),
  Strikethrough: new Set(["StrikethroughMark"]),
  InlineCode: new Set(["CodeMark"]),
}

/** A link or a picture, of which only the words in its brackets are drawn. */
const LINKS = new Set(["Link", "Image"])

/** A mark that opens a line, shown on the whole line the cursor is on. */
const LINE_MARKS = new Set(["HeaderMark", "QuoteMark"])

/** Where a shortcode is only characters: code, and an address. */
const LITERAL = new Set(["InlineCode", "FencedCode", "CodeBlock", "URL", "Autolink", "HTMLTag"])

/* Only while the editor is written in: a cursor rests at the start whether or not anybody
   is there. */
const selectionIn = (view: EditorView): readonly SelectionRange[] =>
  (view.hasFocus ? view.state.selection.ranges : [])

const touches = (ranges: readonly SelectionRange[], from: number, to: number): boolean =>
  ranges.some(range => range.from <= to && range.to >= from)

const linesOf = (state: EditorState, ranges: readonly SelectionRange[]): Set<number> => {
  const lines = new Set<number>()
  for (const range of ranges) {
    const to = state.doc.lineAt(range.to).number
    for (let line = state.doc.lineAt(range.from).number; line <= to; line++) lines.add(line)
  }
  return lines
}

const hidden = Decoration.replace({})

/* The space after a `##` or a `>` belongs to the mark, or the line starts one space in. */
const endOf = (state: EditorState, to: number): number => {
  let end = to
  while (end < state.doc.length && state.sliceDoc(end, end + 1) === " ") end++
  return end
}

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
const SHORTCODE = /:([a-z0-9_+-]+):/g

const literalAt = (state: EditorState, at: number): boolean => {
  for (let node: SyntaxNode | null = syntaxTree(state).resolveInner(at, 1); node; node = node.parent) {
    if (LITERAL.has(node.name)) return true
  }
  return false
}

const emojiIn = (view: EditorView, ranges: readonly SelectionRange[]): Range<Decoration>[] => {
  const drawn: Range<Decoration>[] = []
  for (const {from, to} of view.visibleRanges) {
    for (const found of view.state.sliceDoc(from, to).matchAll(SHORTCODE)) {
      const at = from + found.index
      const end = at + found[0].length
      if (touches(ranges, at, end) || literalAt(view.state, at)) continue
      const said = emoji.get(found[1] as string)
      if (!said) continue
      drawn.push(Decoration.replace({widget: new Character(said)}).range(at, end))
    }
  }
  return drawn
}

/** The fenced block a node sits in, whose fences and language show while it is written. */
const fenceOf = (node: SyntaxNode): SyntaxNode | null =>
  (node.parent?.name === "FencedCode" ? node.parent : null)

const decorate = (view: EditorView): DecorationSet => {
  const {state} = view
  const ranges = selectionIn(view)
  const openLines = linesOf(state, ranges)
  const marks: Range<Decoration>[] = []

  for (const {from, to} of view.visibleRanges) {
    syntaxTree(state).iterate({
      from,
      to,
      enter: (ref) => {
        const {node} = ref

        // A bullet even on the line being written: nobody needs reminding they typed a dash.
        if (node.name === "ListMark") {
          if (!BULLETS.has(state.sliceDoc(node.from, node.to))) return
          marks.push(Decoration.replace({widget: new Character(BULLET, "cm-bullet")})
            .range(node.from, node.to))
          return
        }

        if (LINE_MARKS.has(node.name)) {
          if (openLines.has(state.doc.lineAt(node.from).number)) return
          marks.push(hidden.range(node.from, endOf(state, node.to)))
          return
        }

        const fence = node.name === "CodeMark" || node.name === "CodeInfo" ? fenceOf(node) : null
        if (fence) {
          if (!touches(ranges, fence.from, fence.to)) marks.push(hidden.range(node.from, node.to))
          return
        }

        if (LINKS.has(node.name)) {
          if (touches(ranges, node.from, node.to)) return
          // The parser makes a link only of brackets that close, so `[` and `]` are always there.
          const [open, close] = node.getChildren("LinkMark") as [SyntaxNode, SyntaxNode]
          marks.push(hidden.range(open.from, open.to), hidden.range(close.from, node.to))
          return
        }

        const span = node.parent
        if (!span || !SPANS[span.name]?.has(node.name)) return
        if (touches(ranges, span.from, span.to)) return
        marks.push(hidden.range(node.from, node.to))
      },
    })
  }
  return Decoration.set([...marks, ...emojiIn(view, ranges)], true)
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

/** The span a mark writes, so `*` never mistakes the stars of a bold for its own. */
const SPAN_OF: Record<string, string> = {"**": "StrongEmphasis", "*": "Emphasis"}

const spanAround = (state: EditorState, range: SelectionRange, mark: string): SyntaxNode | null => {
  const name = SPAN_OF[mark]
  for (let node: SyntaxNode | null = syntaxTree(state).resolveInner(range.from, 1); node; node = node.parent) {
    if (node.name === name && node.from <= range.from && node.to >= range.to) return node
  }
  return null
}

/** Wraps what is selected in a pair of marks, or unwraps the span it is already in. */
export const wrapWith = (view: EditorView, mark: string): boolean => {
  const {state} = view
  const width = mark.length
  const changes = state.changeByRange((range) => {
    const span = spanAround(state, range, mark)
    if (span) {
      const inside = (at: number) => Math.min(Math.max(at, span.from + width), span.to - width) - width
      return {
        changes: [
          {from: span.from, to: span.from + width},
          {from: span.to - width, to: span.to},
        ],
        range: EditorSelection.range(inside(range.anchor), inside(range.head)),
      }
    }
    const inside = state.sliceDoc(range.from, range.to)
    return {
      changes: {from: range.from, to: range.to, insert: `${mark}${inside}${mark}`},
      range: EditorSelection.range(range.from + width, range.to + width),
    }
  })
  view.dispatch(changes, {scrollIntoView: true, userEvent: "input.wrap"})
  return true
}
