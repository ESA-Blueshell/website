import type {AdvancedUser} from "@/services/api"

export interface FilterOptions {
  keys?: (keyof AdvancedUser)[]
  caseSensitive?: boolean
}

function valuesFromUser(user: AdvancedUser, keys?: (keyof AdvancedUser)[]): string[] {
  const src: Record<string, unknown> = user ?? {}
  const raw = keys?.length ? keys.map((k) => src[k]) : Object.values(src)
  return raw.filter((v) => v != null).map((v) => String(v))
}

export function matchUser(user: AdvancedUser, query: string, opts: FilterOptions = {}): boolean {
  const q = (opts.caseSensitive ? query : query.toLowerCase()).trim()
  if (!q) return true
  const terms = q.split(/\s+/).filter(Boolean)
  const haystack = valuesFromUser(user, opts.keys)
    .map((v) => (opts.caseSensitive ? v : v.toLowerCase()))
  return terms.every((term) => haystack.some((value) => value.includes(term)))
}

export function filterUsers(list: AdvancedUser[], query: string, opts: FilterOptions = {}): AdvancedUser[] {
  if (!Array.isArray(list)) return []
  if (!query) return list.slice()
  return list.filter((u) => matchUser(u, query, opts))
}
