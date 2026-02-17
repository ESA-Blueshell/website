export type FilterOptions<T extends Record<string, unknown>> = {
  keys?: Array<keyof T>
  caseSensitive?: boolean
}

function valuesFromUser<T extends Record<string, unknown>>(user: T, keys?: Array<keyof T>): string[] {
  const src: Record<string, unknown> = user ?? {}
  const raw = keys?.length ? keys.map((k) => src[String(k)]) : Object.values(src)
  return raw.filter((v) => v != null).map((v) => String(v))
}

export function matchUser<T extends Record<string, unknown>>(user: T, query: string, opts: FilterOptions<T> = {}): boolean {
  const q = (opts.caseSensitive ? query : query.toLowerCase()).trim()
  if (!q) return true
  const terms = q.split(/\s+/).filter(Boolean)
  const haystack = valuesFromUser(user, opts.keys)
    .map((v) => (opts.caseSensitive ? v : v.toLowerCase()))
  return terms.every((term) => haystack.some((value) => value.includes(term)))
}

export function filterUsers<T extends Record<string, unknown>>(list: T[], query: string, opts: FilterOptions<T> = {}): T[] {
  if (!Array.isArray(list)) return []
  if (!query) return list.slice()
  return list.filter((u) => matchUser(u, query, opts))
}
