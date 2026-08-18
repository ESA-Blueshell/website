import {computed, reactive, toValue, type MaybeRefOrGetter} from "vue"

/**
 * One filter, defined in a single place: where it starts, what "off" looks like,
 * and how it matches.
 */
export interface Filter<T, V> {
  /** Value the filter holds on first render. */
  initial: V
  /** The value meaning "not filtering". `clear()` writes this. */
  unset: V
  /**
   * Whether a value counts as not filtering. Defaults to `Object.is(value, unset)`,
   * which is wrong for arrays and objects — `[] === []` is false, so such a filter
   * would read as permanently active and reject every row on first render.
   */
  isUnset?: (value: V) => boolean
  /**
   * Returns the row test for a given value. Called once per value rather than per
   * row, so per-value work is hoisted, and reactive reads here are tracked even
   * when no row reaches the returned test — with a per-row predicate such a read is
   * only tracked in runs where some row gets that far, so results can go stale.
   */
  match: (value: V) => (row: T) => boolean
}

/** Pins the row type so each filter states only its own value type. Identity at runtime. */
export function filtersFor<T>() {
  return <V>(filter: Filter<T, V>): Filter<T, V> => filter
}

// `any` is confined to the constraint; `infer V` recovers each real value type below.
/* eslint-disable-next-line @typescript-eslint/no-explicit-any */
type AnyFilter<T> = Filter<T, any>

type ValueOf<T, F> = F extends Filter<T, infer V> ? V : never
type StateOf<T, F> = {[K in keyof F]: ValueOf<T, F[K]>}

/** Copied so a filter holding an array or object cannot alias its own `initial`. */
function seed(value: unknown): unknown {
  if (Array.isArray(value)) return [...value]
  if (value !== null && typeof value === "object") return {...value}
  return value
}

/**
 * Filters rows through a record of named filters. A filter sitting at its unset value
 * is skipped rather than run, so an untouched filter costs nothing per row. Filters
 * run in declaration order, so declare cheap ones before expensive ones.
 *
 * A filter that throws yields no rows rather than all of them: showing more rows than
 * were asked for is the more dangerous failure when the result drives a bulk action.
 */
export function useRowFilters<T, F extends Record<string, AnyFilter<T>>>(
  rows: MaybeRefOrGetter<readonly T[] | undefined>,
  filters: F,
) {
  const entries = Object.entries(filters) as [string, Filter<T, unknown>][]
  const values = reactive({} as Record<string, unknown>)

  for (const [key, filter] of entries) values[key] = seed(filter.initial)

  function isActive(key: string, filter: Filter<T, unknown>): boolean {
    const value = values[key]
    try {
      return filter.isUnset ? !filter.isUnset(value) : !Object.is(value, filter.unset)
    } catch (error) {
      // Treat an unreadable value as filtering, so the match below decides the
      // outcome rather than the row silently passing.
      console.error(`[useRowFilters] filter "${key}" could not test its value`, error)
      return true
    }
  }

  const activeKeys = computed(() => entries.filter(([k, f]) => isActive(k, f)).map(([k]) => k))

  const filteredRows = computed<T[]>(() => {
    const source = toValue(rows) ?? []

    // Built before the row loop, so reactive reads inside `match` are tracked
    // whether or not any row reaches the returned test.
    const tests: ((row: T) => boolean)[] = []
    for (const [key, filter] of entries) {
      if (!isActive(key, filter)) continue
      try {
        tests.push(filter.match(values[key]))
      } catch (error) {
        console.error(`[useRowFilters] filter "${key}" could not be built`, error)
        return []
      }
    }

    if (tests.length === 0) return [...source]
    try {
      return source.filter((row) => tests.every((test) => test(row)))
    } catch (error) {
      console.error("[useRowFilters] a filter threw while matching; showing no rows", error)
      return []
    }
  })

  /** Sets filters to their unset value. */
  function clear(key?: keyof F & string) {
    for (const [k, filter] of entries) {
      if (key !== undefined && k !== key) continue
      values[k] = seed(filter.unset)
    }
  }

  /** Sets filters back to the value they started with, which may itself be filtering. */
  function reset() {
    for (const [key, filter] of entries) values[key] = seed(filter.initial)
  }

  return {
    state: values as StateOf<T, F>,
    filteredRows,
    activeKeys: activeKeys as import("vue").ComputedRef<(keyof F & string)[]>,
    clear,
    reset,
  }
}
