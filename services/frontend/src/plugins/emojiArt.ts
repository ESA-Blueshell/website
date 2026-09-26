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
