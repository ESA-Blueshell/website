import EMOJI_SOURCE from "emojibase-regex/emoji"

/**
 * A standard emoji as the site draws it: one Noto picture per emoji, served beside the page.
 *
 * Only emoji presentation counts, so `©`, `™` and `↔` in running text stay text. The pattern
 * is written in surrogate pairs, which a `u` flag would read apart.
 */
export const EMOJI = new RegExp(EMOJI_SOURCE.source, "g")

const VARIATION = 0xFE0F

/**
 * The file an emoji is drawn from: its code points in hex, four digits at least, without the
 * variation selector. vite.config.mjs names the files it writes by the same rule; change one,
 * change the other.
 */
export const emojiFile = (emoji: string): string =>
  [...emoji].map(one => one.codePointAt(0) as number)
    .filter(point => point !== VARIATION)
    .map(point => point.toString(16).padStart(4, "0"))
    .join("-")

export const emojiSrc = (emoji: string): string => `/emoji/${emojiFile(emoji)}.svg`

const escapeAttribute = (text: string): string => text.replaceAll("&", "&amp;").replaceAll("\"", "&quot;")

export const emojiImg = (emoji: string): string =>
  `<img class="emoji" src="${emojiSrc(emoji)}" alt="${escapeAttribute(emoji)}" draggable="false">`

/** A server emoji as Discord writes it, `<:name:id>` or, moving, `<a:name:id>`. */
export const SERVER_EMOJI = /<(a?):(\w{2,32}):(\d{15,21})>/g

/* Any server's emoji is on Discord's CDN by its ID alone, so one from elsewhere is drawn too. */
export const serverEmojiSrc = (id: string, moving: boolean): string =>
  `https://cdn.discordapp.com/emojis/${id}.${moving ? "gif" : "webp"}?size=48`

/** Stands for `:name:` where the picture will not load, as Discord's own text does. */
export const serverEmojiImg = (name: string, id: string, moving: boolean): string =>
  `<img class="emoji" src="${serverEmojiSrc(id, moving)}" alt=":${name}:" draggable="false">`

/** The picture of an emoji as a description writes it: a character, or a server's `<:name:id>`. */
export const pictureOf = (written: string): string => {
  const server = new RegExp(`^${SERVER_EMOJI.source}$`).exec(written)
  return server ? serverEmojiSrc(server[3] as string, server[1] === "a") : emojiSrc(written)
}

/** Every emoji in escaped html text drawn as its picture. */
export const withEmojiArt = (html: string): string => html.replace(EMOJI, emojiImg)

/**
 * Puts an emoji picture that failed to load back as the character it stands for, which the
 * device then draws as best it can. Listened for in the capture phase, since `error` does not
 * bubble.
 */
export const fallBackToCharacter = (event: Event): void => {
  const broken = event.target
  if (!(broken instanceof HTMLImageElement) || !broken.classList.contains("emoji")) return
  broken.replaceWith(document.createTextNode(broken.alt))
}
