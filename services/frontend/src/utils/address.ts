/** The longest address a page answers to, which is the width of every address column. */
export const ADDRESS_LENGTH = 64

/**
 * The address a name makes for a page: lowercase letters and digits, of any alphabet, and
 * everything else one hyphen, none at either end. TWIN: `addressOf` in `shared/model/PageAddress.kt`,
 * which makes the address the api keeps. Change one, change the other.
 */
export function addressOf(text: string): string {
  return text
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{Nd}]+/gu, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, ADDRESS_LENGTH)
}
