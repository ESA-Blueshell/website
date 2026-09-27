import {Marked, type Token, type TokenizerAndRendererExtension, type Tokens} from "marked"
import * as emoji from "node-emoji"
import {emojiImg, SERVER_EMOJI, serverEmojiImg, withEmojiArt} from "@/plugins/emojiArt"

/*
 * A description read the way Discord reads a message (architecture ADR-010). Each rule is
 * Discord's own pattern; markdownDialect.ts parses the same rules for the editor, so change
 * one, change the other.
 */

const escapeHtml = (text: string): string => text
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;")
  .replaceAll("\"", "&quot;")
  .replaceAll("'", "&#39;")

const lastCharacterOf = (tokens: Token[]): string => tokens.at(-1)?.raw.at(-1) ?? ""

/** A pair of marks around inline content, drawn as one element. */
const pair = (name: string, pattern: RegExp, open: string, close: string, at: string)
  : TokenizerAndRendererExtension => ({
  name,
  level: "inline",
  start: src => src.indexOf(at),
  tokenizer(src) {
    const found = pattern.exec(src)
    if (!found) return undefined
    return {type: name, raw: found[0], tokens: this.lexer.inlineTokens(found[1] as string)}
  },
  renderer(token) {
    return `${open}${this.parser.parseInline(token.tokens as Token[])}${close}`
  },
})

const spoiler = pair("spoiler", /^\|\|([\s\S]+?)\|\|/,
  "<span class=\"spoiler\" role=\"button\" tabindex=\"0\" aria-expanded=\"false\">", "</span>", "||")
const underline = pair("underline", /^__([\s\S]+?)__(?!_)/, "<u>", "</u>", "__")
const strong = pair("discordStrong", /^\*\*([\s\S]+?)\*\*(?!\*)/, "<strong>", "</strong>", "**")
const strike = pair("discordStrike", /^~~([\s\S]+?)~~/, "<del>", "</del>", "~~")

const STARRED = /^\*(?=\S)((?:\*\*|\\[\s\S]|\s+(?:\\[\s\S]|[^\s*\\]|\*\*)|[^\s*\\])+?)\*(?!\*)/
const UNDERSCORED = /^_((?:__|\\[\s\S]|[^\\_])+?)_\b/

/** `*x*`, and `_x_` only where the underscore does not stand inside a word. */
const emphasis: TokenizerAndRendererExtension = {
  name: "discordEmphasis",
  level: "inline",
  start: src => src.search(/[*_]/),
  tokenizer(src, tokens) {
    const found = STARRED.exec(src)
      ?? (/\w/.test(lastCharacterOf(tokens)) ? null : UNDERSCORED.exec(src))
    if (!found) return undefined
    return {type: "discordEmphasis", raw: found[0], tokens: this.lexer.inlineTokens(found[1] as string)}
  },
  renderer(token) {
    return `<em>${this.parser.parseInline(token.tokens as Token[])}</em>`
  },
}

/** A single `~` is only a tilde; it never strikes anything through. */
const tilde: TokenizerAndRendererExtension = {
  name: "tilde",
  level: "inline",
  start: src => src.indexOf("~"),
  tokenizer(src) {
    if (!src.startsWith("~") || src.startsWith("~~")) return undefined
    return {type: "text", raw: "~", text: "~"}
  },
}

/** A shortcode written before shortcodes were turned into emoji as they were typed. */
const shortcode: TokenizerAndRendererExtension = {
  name: "shortcode",
  level: "inline",
  start: src => src.indexOf(":"),
  tokenizer(src) {
    const found = /^:([a-z0-9_+-]+):/.exec(src)
    const character = found ? emoji.get(found[1] as string) : undefined
    if (!found || !character) return undefined
    return {type: "shortcode", raw: found[0], character}
  },
  renderer(token) {
    return emojiImg(token.character as string)
  },
}

const WRITTEN_SERVER_EMOJI = new RegExp(`^${SERVER_EMOJI.source}`)

/** A server's emoji, from this server or any other. */
const serverEmoji: TokenizerAndRendererExtension = {
  name: "serverEmoji",
  level: "inline",
  start: src => src.search(SERVER_EMOJI),
  tokenizer(src) {
    const found = WRITTEN_SERVER_EMOJI.exec(src)
    if (!found) return undefined
    return {type: "serverEmoji", raw: found[0], moving: found[1] === "a", name: found[2], id: found[3]}
  },
  renderer(token) {
    return serverEmojiImg(token.name as string, token.id as string, token.moving as boolean)
  },
}

const subtext: TokenizerAndRendererExtension = {
  name: "subtext",
  level: "block",
  // Read with a newline before it, so a match at the very start is found as well.
  start: (src) => {
    const at = `\n${src}`.indexOf("\n-# ")
    return at === -1 ? undefined : at
  },
  tokenizer(src) {
    const found = /^-# ([^\n]*)(?:\n|$)/.exec(src)
    if (!found) return undefined
    return {type: "subtext", raw: found[0], tokens: this.lexer.inlineTokens(found[1] as string)}
  },
  renderer(token) {
    return `<p class="subtext">${this.parser.parseInline(token.tokens as Token[])}</p>\n`
  },
}

const FENCE = /^\s{0,3}(?:```|~~~)/
const ITEM = /^\s*(?:[-*+]|\d+[.)])\s/
const QUOTE_REST = /^>>> ?/

/**
 * Two of Discord's line rules marked has no option for: `>>>` quotes everything after it, and a
 * list ends at the first line that is neither an item nor indented under one.
 */
export const discordLines = (source: string): string => {
  const lines = source.split("\n")
  const read: string[] = []
  let fenced = false
  let listed = false
  for (let at = 0; at < lines.length; at++) {
    const line = lines[at] as string
    if (FENCE.test(line)) fenced = !fenced
    if (!fenced && QUOTE_REST.test(line)) {
      read.push(line.replace(QUOTE_REST, "> "), ...lines.slice(at + 1).map(rest => `> ${rest}`))
      break
    }
    if (!fenced && listed && line.trim() !== "" && !/^\s/.test(line) && !ITEM.test(line)) read.push("")
    listed = !fenced && (ITEM.test(line) || (listed && /^\s+\S/.test(line)))
    read.push(line)
  }
  return read.join("\n")
}

export const discordMarked = new Marked({
  gfm: true,
  breaks: true,
  async: false,
  // Tried last to first, so bold is read before italic.
  extensions: [subtext, serverEmoji, shortcode, tilde, strike, emphasis, strong, underline, spoiler],
  hooks: {preprocess: discordLines},
  renderer: {
    text(token: Tokens.Text | Tokens.Escape) {
      if ("tokens" in token && token.tokens) return this.parser.parseInline(token.tokens)
      return withEmojiArt("escaped" in token && token.escaped ? token.text : escapeHtml(token.text))
    },
  },
})
