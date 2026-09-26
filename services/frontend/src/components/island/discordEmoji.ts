import {listServerEmoji} from "@/domains/discord"

/**
 * Emoji by the names Discord types them with: the server's own, then the standard ones by
 * JoyPixels' names (`thumbsup`, `slight_smile`, `flag_nl`), which are Discord's. Read only by
 * the editor, so both are fetched when one opens.
 */
export interface NamedEmoji {
  name: string
  /** As a description writes it: the character, or `<:name:id>`. */
  emoji: string
}

interface Listed {
  hexcode: string
  unicode: string
  skins?: Listed[]
}

let known = new Map<string, string>()
let loading: Promise<Map<string, string>> | undefined
/* By the name lowercased: Discord finds a server's emoji whatever case it is typed in. */
let server = new Map<string, NamedEmoji>()
let asking: Promise<void> | undefined

const read = async (): Promise<Map<string, string>> => {
  const [{default: listed}, {default: shortcodes}] = await Promise.all([
    import("emojibase-data/en/compact.json"),
    import("emojibase-data/en/shortcodes/joypixels.json"),
  ])
  // The list says how each emoji is written, variation selector included, which a hexcode drops.
  const written = new Map<string, string>()
  for (const one of (listed as Listed[]).flatMap(one => [one, ...(one.skins ?? [])])) {
    written.set(one.hexcode, one.unicode)
  }
  const names = new Map<string, string>()
  for (const [hexcode, named] of Object.entries(shortcodes as Record<string, string | string[]>)) {
    // Every JoyPixels hexcode is in the list; markdownEmoji.test.ts holds it to that.
    for (const name of [named].flat()) names.set(name, written.get(hexcode) as string)
  }
  known = names
  return names
}

const ask = async (): Promise<void> => {
  const listed = await listServerEmoji() ?? []
  server = new Map(listed.map(one => [one.name.toLowerCase(), {
    name: one.name,
    emoji: `<${one.animated ? "a" : ""}:${one.name}:${one.id}>`,
  }]))
}

/** Starts reading the names, and answers once they are there. */
export const loadDiscordEmoji = (): Promise<Map<string, string>> => (loading ??= read())

/** Starts asking the api for the server's own emoji; a later ask asks again. */
export const loadServerEmoji = (): Promise<void> => (asking ??= ask().finally(() => {
  asking = undefined
}))

/** The emoji a name stands for, the server's own first, once the names have been read. */
export const emojiNamed = (name: string): string | undefined =>
  server.get(name.toLowerCase())?.emoji ?? known.get(name.toLowerCase())

const MOST = 12

/**
 * The server's own emoji that answer to what was typed, then the standard ones: in each, names
 * that start with it before names that only contain it, shortest first.
 */
export const emojiMatching = (typed: string): NamedEmoji[] => {
  const byStart = (a: NamedEmoji, b: NamedEmoji) =>
    Number(!a.name.toLowerCase().startsWith(typed)) - Number(!b.name.toLowerCase().startsWith(typed))
      || a.name.length - b.name.length || a.name.localeCompare(b.name)
  const own = [...server.values()].filter(one => one.name.toLowerCase().includes(typed)).sort(byStart)
  const found: NamedEmoji[] = []
  for (const [name, emoji] of known) {
    if (name.includes(typed)) found.push({name, emoji})
  }
  return [...own, ...found.sort(byStart)].slice(0, MOST)
}

