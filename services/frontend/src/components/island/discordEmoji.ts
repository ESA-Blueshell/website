/**
 * Standard emoji by the names Discord types them with, which are JoyPixels': `thumbsup`,
 * `slight_smile`, `flag_nl`. Read only by the editor, so the data is fetched when one opens.
 */
export interface NamedEmoji {
  name: string
  emoji: string
}

interface Listed {
  hexcode: string
  unicode: string
  skins?: Listed[]
}

let known = new Map<string, string>()
let loading: Promise<Map<string, string>> | undefined

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

/** Starts reading the names, and answers once they are there. */
export const loadDiscordEmoji = (): Promise<Map<string, string>> => (loading ??= read())

/** The emoji a name stands for, once the names have been read. */
export const emojiNamed = (name: string): string | undefined => known.get(name)

const MOST = 12

/** The names that start with what was typed, shortest first, then those that contain it. */
export const emojiMatching = (typed: string): NamedEmoji[] => {
  const found: NamedEmoji[] = []
  for (const [name, emoji] of known) {
    if (name.includes(typed)) found.push({name, emoji})
  }
  return found
    .sort((a, b) => Number(!a.name.startsWith(typed)) - Number(!b.name.startsWith(typed))
      || a.name.length - b.name.length || a.name.localeCompare(b.name))
    .slice(0, MOST)
}
