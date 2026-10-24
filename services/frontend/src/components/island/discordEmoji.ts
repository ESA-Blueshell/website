import {DiscordEmojiKind, listServerEmoji} from "@/domains/discord"

/**
 * Emoji by the names Discord types them with: the server's own, then the bot's application emoji,
 * then the emoji seen used in the server, then the standard ones by JoyPixels' names (`thumbsup`,
 * `slight_smile`, `flag_nl`), which are Discord's. Read only by the editor, so all are fetched
 * when one opens.
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
/* Where each kind ranks; a kind the api adds fails to compile until it is placed. */
const RANK: Record<DiscordEmojiKind, number> = {
  [DiscordEmojiKind.SERVER]: 0,
  [DiscordEmojiKind.APPLICATION]: 1,
  [DiscordEmojiKind.SEEN]: 2,
}
type Custom = NamedEmoji & {uses: number}
/* Each kind by the name lowercased, in rank order: Discord finds them whatever case they are typed in. */
let custom: Map<string, Custom>[] = []
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
  custom = Object.values(RANK).map(() => new Map())
  for (const one of listed) {
    const emoji = `<${one.animated ? "a" : ""}:${one.name}:${one.id}>`
    custom[RANK[one.kind]]?.set(one.name.toLowerCase(), {name: one.name, emoji, uses: one.uses ?? 0})
  }
}

/** Starts reading the names, and answers once they are there. */
export const loadDiscordEmoji = (): Promise<Map<string, string>> => (loading ??= read())

/** Starts asking the api for the server's and the application's emoji; a later ask asks again. */
export const loadServerEmoji = (): Promise<void> => (asking ??= ask().finally(() => {
  asking = undefined
}))

/** The emoji a name stands for, by kind, once the names have been read. */
export const emojiNamed = (name: string): string | undefined =>
  custom.map(kind => kind.get(name.toLowerCase())?.emoji).find(Boolean) ?? known.get(name.toLowerCase())

const MOST = 12

/**
 * The emoji that answer to what was typed, the server's own first, then the application's, then
 * the seen ones, then the standard ones: in each, names that start with it before names that only
 * contain it, then the most used, then the shortest.
 */
export const emojiMatching = (typed: string): NamedEmoji[] => {
  const startsFirst = (a: NamedEmoji, b: NamedEmoji) =>
    Number(!a.name.toLowerCase().startsWith(typed)) - Number(!b.name.toLowerCase().startsWith(typed))
  const shortest = (a: NamedEmoji, b: NamedEmoji) => a.name.length - b.name.length || a.name.localeCompare(b.name)
  const own = custom.flatMap(kind => [...kind.values()]
    .filter(one => one.name.toLowerCase().includes(typed))
    .sort((a, b) => startsFirst(a, b) || b.uses - a.uses || shortest(a, b)))
  const found: NamedEmoji[] = []
  for (const [name, emoji] of known) {
    if (name.includes(typed)) found.push({name, emoji})
  }
  return [...own, ...found.sort((a, b) => startsFirst(a, b) || shortest(a, b))].slice(0, MOST)
}

