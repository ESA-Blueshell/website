/**
 * How the cohort pages count what they list.
 *
 * One place, so a template cannot put `1 cohort · 1 members` on a row beside a correctly
 * pluralised one. Pure functions over the numbers rather than computeds over a subject, so they
 * are worth a test.
 */

/**
 * The noun on its own, for the places that bold the number and so cannot take a whole phrase.
 * `category` is why the plural is overridable rather than always the singular plus an `s`.
 */
export function nounFor(count: number, singular: string, plural = `${singular}s`): string {
  return count === 1 ? singular : plural
}

/** `1 member`, `2 members`, `0 members`. */
export function countLabel(count: number, singular: string, plural?: string): string {
  return `${count} ${nounFor(count, singular, plural)}`
}
