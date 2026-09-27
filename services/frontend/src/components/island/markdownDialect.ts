import {Tag, tags} from "@lezer/highlight"
import type {BlockContext, DelimiterType, InlineContext, Line, MarkdownConfig} from "@lezer/markdown"

/*
 * Discord's rules as the editor parses them (architecture ADR-010). discordMarkdown.ts renders
 * the same rules for the site; change one, change the other.
 */

export const underlineTag = Tag.define()
export const spoilerTag = Tag.define()
export const subtextTag = Tag.define()

const STAR = 42
const UNDERSCORE = 95
const PIPE = 124
const DASH = 45

const UNDERLINE: DelimiterType = {resolve: "Underline", mark: "UnderlineMark"}
const SPOILER: DelimiterType = {resolve: "Spoiler", mark: "SpoilerMark"}
// Discord's bold, which closes on `**` however it is flanked: `**done! **` is bold.
const BOLD: DelimiterType = {resolve: "StrongEmphasis", mark: "EmphasisMark"}

/** A run of exactly two of a character, so `***` and `___` are left to the usual rules. */
const pairAt = (cx: InlineContext, next: number, at: number, char: number): boolean =>
  next === char && cx.char(at + 1) === char && cx.char(at + 2) !== char && cx.char(at - 1) !== char

const SUBTEXT = /^-# /

export const discordDialect: MarkdownConfig = {
  remove: ["Subscript", "Superscript", "Emoji"],
  defineNodes: [
    {name: "Underline", style: underlineTag},
    {name: "UnderlineMark", style: tags.processingInstruction},
    {name: "Spoiler", style: spoilerTag},
    {name: "SpoilerMark", style: tags.processingInstruction},
    {name: "Subtext", block: true, style: subtextTag},
    {name: "SubtextMark", style: tags.processingInstruction},
  ],
  parseInline: [{
    name: "Underline",
    before: "Emphasis",
    parse: (cx, next, at) =>
      (pairAt(cx, next, at, UNDERSCORE) ? cx.addDelimiter(UNDERLINE, at, at + 2, true, true) : -1),
  }, {
    name: "Bold",
    before: "Emphasis",
    parse: (cx, next, at) =>
      (pairAt(cx, next, at, STAR) ? cx.addDelimiter(BOLD, at, at + 2, true, true) : -1),
  }, {
    name: "Spoiler",
    parse: (cx, next, at) =>
      (next === PIPE && cx.char(at + 1) === PIPE ? cx.addDelimiter(SPOILER, at, at + 2, true, true) : -1),
  }],
  parseBlock: [{
    name: "Subtext",
    before: "HorizontalRule",
    parse: (cx: BlockContext, line: Line) => {
      if (line.next !== DASH || !SUBTEXT.test(line.text.slice(line.pos))) return false
      const from = cx.lineStart + line.pos
      const to = cx.lineStart + line.text.length
      const said = line.text.slice(line.pos + 3)
      cx.nextLine()
      cx.addElement(cx.elt("Subtext", from, to, [
        cx.elt("SubtextMark", from, from + 2),
        ...cx.parser.parseInline(said, from + 3),
      ]))
      return true
    },
    endLeaf: (_cx, line) => line.next === DASH && SUBTEXT.test(line.text.slice(line.pos)),
  }],
}
