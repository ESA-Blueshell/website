import countries, {type Country} from "world-countries"
import type {InternalItem} from "vuetify"

export const allCountriesSorted = [...countries].sort((a, b) => {
  const ta = (a.name?.common || a.name.official).toLowerCase()
  const tb = (b.name?.common || b.name.official).toLowerCase()
  return ta.localeCompare(tb)
})

export const countriesWithFlagSorted = [...countries]
  .filter((c) => c.flag)
  .sort((a, b) => {
    const ta = (a.demonyms?.eng?.m || a.name.common).toLowerCase()
    const tb = (b.demonyms?.eng?.m || b.name.common).toLowerCase()
    return ta.localeCompare(tb)
  })

export const cca2Map = new Map<string, Country>(countries.map((c) => [c.cca2.toUpperCase(), c]))

export const deburrLower = (s: string) =>
  s.normalize("NFD").replaceAll(/\p{M}/gu, "").toLowerCase()

export const partsFor = (c: Country): string[] =>
  [
    c.demonyms?.eng?.m,
    c.demonyms?.eng?.f,
    c.name?.common,
    c.name?.official,
    c.cca2,
    c.cca3,
    c.cioc,
    ...(c.altSpellings ?? []),
  ]
    .filter(Boolean)
    .map((s) => deburrLower(String(s)!))

export const isValidCca2 = (v?: string | null) => !!v && v.length === 2 && cca2Map.has(v.toUpperCase())

export function findTopMatch(query: string, source: Country[]): Country | null {
  const q = deburrLower(query.trim())
  if (!q) return null
  const exact = source.find((c) =>
    [c.cca2, c.cca3, c.cioc].filter(Boolean).some((code) => deburrLower(String(code)) === q) ||
    partsFor(c).includes(q),
  )
  if (exact) return exact
  const starts = source.find((c) => partsFor(c).some((p) => p.startsWith(q)))
  if (starts) return starts
  return source.find((c) => partsFor(c).some((p) => p.includes(q))) ?? null
}

export function customFilterForCountry(_itemText: string, queryText: string, item: InternalItem<Country>) {
  const c = item.raw
  const q = deburrLower(queryText.trim())
  if (!q) return true
  return partsFor(c).some((p) => p.includes(q))
}

export const displayCountry = (c: Country) => `${c.flag ?? ""} ${c.name?.common || c.name?.official}`
export const displayNationality = (c: Country) => `${c.flag ?? ""} ${c.demonyms?.eng?.m || c.name?.common || c.name?.official}`
