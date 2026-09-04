/**
 * The letters that stand in for a picture nobody has.
 *
 * A display rule rather than knowledge about a person: it is here because the row that draws
 * the plate is here, and because a name is the whole input (frontend ADR-001).
 */

/**
 * A name's initials, at most two, taken from the first and last of the names it is written with.
 *
 * A name in double quotes is skipped, the history publishing a nickname inside the name —
 * `Roos "SkyeWolf" Kruk` — and the plate carrying the person's letters. Only double quotes:
 * `'t Hooft` is a name, not a nickname. Anything that is not a letter or digit is read past, so
 * a hyphenated or accented name still yields a letter. One word gives one letter, and a name
 * that yields none gives nothing — an empty plate is honest, a punctuation mark set large is not.
 */
export function monogramOf(name: string): string {
  const parts = name
    .split(/\s+/)
    .filter(part => part.length > 0 && !part.startsWith('"'))
    .map(part => part.match(/[\p{L}\p{N}]/u)?.[0] ?? "")
    .filter(letter => letter !== "")
  if (parts.length === 0) return ""
  const first = parts[0]!
  const last = parts[parts.length - 1]!
  return (parts.length === 1 ? first : `${first}${last}`).toUpperCase()
}
