/** The one sentence a field shows, out of whatever shape the form handed it. */
export const firstSaid = (said?: string | string[]): string =>
  (Array.isArray(said) ? said[0] : said) ?? ""
