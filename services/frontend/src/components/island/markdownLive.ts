import {syntaxTree} from "@codemirror/language"
import {EditorSelection, type EditorState, type Range, type SelectionRange} from "@codemirror/state"
import {Decoration, type DecorationSet, EditorView, ViewPlugin, type ViewUpdate, WidgetType}
  from "@codemirror/view"
import {type MentionKind, nameMentions} from "@/domains/discord"
import {TIMESTAMP, timestampText, type TimeStyle} from "@/plugins/discordTime"
import {EMOJI, emojiSrc, SERVER_EMOJI, serverEmojiSrc} from "@/plugins/emojiArt"

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
  Underline: new Set(["UnderlineMark"]),
  Spoiler: new Set(["SpoilerMark"]),
}

/** A link or a picture, of which only the words in its brackets are drawn. */
const LINKS = new Set(["Link", "Image"])

/** A mark that opens a line, shown on the whole line the cursor is on. */
const LINE_MARKS = new Set(["HeaderMark", "QuoteMark", "SubtextMark"])

/** Discord's code face, which the site has none of its own for. */
export const CODE_FONT = "ui-monospace, SFMono-Regular, Consolas, monospace"

/** Blocks drawn line by line, as Discord draws a quote with its bar and code in its box. */
const BLOCK_LINES: Record<string, Decoration> = {
  Blockquote: Decoration.line({class: "cm-quote"}),
  FencedCode: Decoration.line({class: "cm-codeblock"}),
  CodeBlock: Decoration.line({class: "cm-codeblock"}),
}

const inlineCode = Decoration.mark({class: "cm-code"})

/** Where text is only characters: code, and an address. */
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

/* The space after a `##`, a `>` or a `-#` belongs to the mark, or the line starts one space in. */
const endOf = (state: EditorState, to: number): number => {
  let end = to
  while (end < state.doc.length && state.sliceDoc(end, end + 1) === " ") end++
  return end
}

class Character extends WidgetType {
  constructor(private readonly said: string, private readonly as: string) {
    super()
  }

  eq(other: Character): boolean {
    return other.said === this.said && other.as === this.as
  }

  toDOM(): HTMLElement {
    const drawn = document.createElement("span")
    drawn.textContent = this.said
    drawn.className = this.as
    return drawn
  }

  ignoreEvent(): boolean {
    return false
  }
}

/**
 * An emoji drawn as the page draws it, or as what it says, the character or a server emoji's
 * `:name:`, where its picture will not load.
 */
class EmojiArt extends WidgetType {
  constructor(private readonly src: string, private readonly says: string) {
    super()
  }

  eq(other: EmojiArt): boolean {
    return other.src === this.src && other.says === this.says
  }

  toDOM(): HTMLElement {
    const drawn = document.createElement("img")
    drawn.className = "cm-emoji"
    drawn.src = this.src
    drawn.alt = this.says
    drawn.addEventListener("error", () => drawn.replaceWith(document.createTextNode(this.says)))
    return drawn
  }

  ignoreEvent(): boolean {
    return false
  }
}

/** Whether the text at a position is code or an address; `side` -1 reads what ends there. */
export const literalAt = (state: EditorState, at: number, side: -1 | 1 = 1): boolean => {
  for (let node: SyntaxNode | null = syntaxTree(state).resolveInner(at, side); node; node = node.parent) {
    if (LITERAL.has(node.name)) return true
  }
  return false
}

/** A mention as the page draws it, named once the api has said who or what it is. */
class MentionPill extends WidgetType {
  constructor(private readonly kind: MentionKind, private readonly id: string) {
    super()
  }

  eq(other: MentionPill): boolean {
    return other.kind === this.kind && other.id === this.id
  }

  toDOM(): HTMLElement {
    const drawn = document.createElement("span")
    drawn.className = "cm-mention"
    drawn.textContent = this.kind === "channel" ? "#…" : "@…"
    const ids = {users: [] as string[], roles: [] as string[], channels: [] as string[]}
    ids[`${this.kind}s`].push(this.id)
    void nameMentions(ids).then((nameOf) => {
      const {said, colour} = nameOf(this.kind, this.id)
      drawn.textContent = said
      if (colour) drawn.style.setProperty("--mention", colour)
    })
    return drawn
  }

  ignoreEvent(): boolean {
    return false
  }
}

class TimePill extends WidgetType {
  constructor(private readonly unix: number, private readonly style: TimeStyle) {
    super()
  }

  eq(other: TimePill): boolean {
    return other.unix === this.unix && other.style === this.style
  }

  toDOM(): HTMLElement {
    const drawn = document.createElement("span")
    drawn.className = "cm-timestamp"
    drawn.textContent = timestampText(this.unix, this.style)
    return drawn
  }

  ignoreEvent(): boolean {
    return false
  }
}

const MENTION = /<(@!?|@&|#)(\d{15,21})>/g
const KINDS: Record<string, MentionKind> = {"@": "user", "@!": "user", "@&": "role", "#": "channel"}

/* Shown as written while the cursor touches one, like a mark, so it can be read and mended. */
const mentionsIn = (view: EditorView, ranges: readonly SelectionRange[]): Range<Decoration>[] => {
  const drawn: Range<Decoration>[] = []
  for (const {from, to} of view.visibleRanges) {
    const text = view.state.sliceDoc(from, to)
    const found = [
      ...[...text.matchAll(MENTION)].map(one => ({one, widget: new MentionPill(KINDS[one[1] as string] as MentionKind, one[2] as string)})),
      ...[...text.matchAll(TIMESTAMP)].map(one => ({one, widget: new TimePill(Number(one[1]), (one[2] ?? "f") as TimeStyle)})),
    ]
    for (const {one, widget} of found) {
      const at = from + one.index
      const end = at + one[0].length
      if (touches(ranges, at, end) || literalAt(view.state, at)) continue
      drawn.push(Decoration.replace({widget}).range(at, end))
    }
  }
  return drawn
}

/* Like a mark, `<:name:id>` shows while the cursor touches it, so it can be read and mended. */
const serverEmojiIn = (view: EditorView, ranges: readonly SelectionRange[]): Range<Decoration>[] => {
  const drawn: Range<Decoration>[] = []
  for (const {from, to} of view.visibleRanges) {
    for (const found of view.state.sliceDoc(from, to).matchAll(SERVER_EMOJI)) {
      const at = from + found.index
      const end = at + found[0].length
      if (touches(ranges, at, end) || literalAt(view.state, at)) continue
      const art = new EmojiArt(serverEmojiSrc(found[3] as string, found[1] === "a"), `:${found[2] as string}:`)
      drawn.push(Decoration.replace({widget: art}).range(at, end))
    }
  }
  return drawn
}

/* Always drawn, cursor or not: the character is the emoji, so there is no mark to show. */
const emojiIn = (view: EditorView): DecorationSet => {
  const drawn: Range<Decoration>[] = []
  for (const {from, to} of view.visibleRanges) {
    for (const found of view.state.sliceDoc(from, to).matchAll(EMOJI)) {
      const at = from + found.index
      if (literalAt(view.state, at)) continue
      drawn.push(Decoration.replace({widget: new EmojiArt(emojiSrc(found[0]), found[0])})
        .range(at, at + found[0].length))
    }
  }
  return Decoration.set(drawn)
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

        const block = BLOCK_LINES[node.name]
        if (block) {
          const last = state.doc.lineAt(node.to).number
          for (let line = state.doc.lineAt(node.from).number; line <= last; line++) {
            marks.push(block.range(state.doc.line(line).from))
          }
        }
        if (node.name === "InlineCode") marks.push(inlineCode.range(node.from, node.to))

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
  return Decoration.set(
    [...marks, ...serverEmojiIn(view, ranges), ...mentionsIn(view, ranges)], true)
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

/** Draws every emoji as its picture. */
export const emojiLive = ViewPlugin.fromClass(
  class {
    emoji: DecorationSet

    constructor(view: EditorView) {
      this.emoji = emojiIn(view)
    }

    update(update: ViewUpdate) {
      if (update.docChanged || update.viewportChanged) this.emoji = emojiIn(update.view)
    }
  },
  {decorations: plugin => plugin.emoji},
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
