import {computed, reactive, toValue, type MaybeRefOrGetter} from "vue"

/**
 * One filter, defined in a single place: where it starts, what "off" looks like,
 * and how it matches.
 */
export interface Filter<T, V> {
  /** Value the filter holds on first render. */
  initial: V
  /**
   * The value meaning "not filtering", or a test for it. A plain value is compared
   * with `Object.is`, so array and object values need the test form — `[] === []` is
   * false and such a filter would read as permanently active.
   */
  unset: V | ((value: V) => boolean)
  /**
   * Returns the row test for a given value. Called once per value rather than per
   * row, so per-value work is hoisted, and reactive reads here are tracked even
   * when no row reaches the returned test.
   */
  match: (value: V) => (row: T) => boolean
}

/** Pins the row type so each filter only states its own value type. */
export function filtersFor<T>() {
  return <V>(filter: Filter<T, V>): Filter<T, V> => filter
}

// `any` is confined to the constraint; `infer V` recovers each real value type below.
/* eslint-disable-next-line @typescript-eslint/no-explicit-any */
type AnyFilter<T> = Filter<T, any>

type ValueOf<T, F> = F extends Filter<T, infer V> ? V : never
type StateOf<T, F> = {[K in keyof F]: ValueOf<T, F[K]>}

/**
 * Filters rows through a record of named filters. A filter sitting at its unset
 * value is skipped rather than run, so an untouched filter costs nothing per row.
 * Filters run in declaration order, so declare cheap ones before expensive ones.
 */
export function useRowFilters<T, F extends Record<string, AnyFilter<T>>>(
  rows: MaybeRefOrGetter<readonly T[] | undefined>,
  filters: F,
) {
  const entries = Object.entries(filters) as [string, Filter<T, unknown>][]
  const values = reactive({} as Record<string, unknown>)

  for (const [key, filter] of entries) {
    values[key] = filter.initial
  }

  function isUnset(key: string, filter: Filter<T, unknown>): boolean {
    const {unset} = filter
    return typeof unset === "function"
      ? (unset as (value: unknown) => boolean)(values[key])
      : Object.is(values[key], unset)
  }

  const activeKeys = computed(() => entries.filter(([k, f]) => !isUnset(k, f)).map(([k]) => k))

  const filteredRows = computed<T[]>(() => {
    const source = toValue(rows) ?? []

    // Built before the row loop, so reactive reads inside `match` are tracked
    // whether or not any row reaches the returned test.
    const tests: ((row: T) => boolean)[] = []
    for (const [key, filter] of entries) {
      if (isUnset(key, filter)) continue
      try {
        tests.push(filter.match(values[key]))
      } catch (error) {
        console.error(`[useRowFilters] filter "${key}" could not be built; ignoring it`, error)
      }
    }

    if (tests.length === 0) return [...source]
    try {
      return source.filter((row) => tests.every((test) => test(row)))
    } catch (error) {
      // This runs during render, so a throw would escape to the app error handler
      // and blank the table. Degrade to unfiltered, loudly, instead.
      console.error("[useRowFilters] a filter threw while matching; showing all rows", error)
      return [...source]
    }
  })

  /** Sets filters to their unset value. Filters that define unset as a test are skipped. */
  function clear(key?: keyof F & string) {
    for (const [k, filter] of entries) {
      if (key !== undefined && k !== key) continue
      if (typeof filter.unset !== "function") values[k] = filter.unset
    }
  }

  /** Sets filters back to the value they started with, which may itself be filtering. */
  function reset() {
    for (const [key, filter] of entries) values[key] = filter.initial
  }

  return {
    state: values as StateOf<T, F>,
    filteredRows,
    activeKeys: activeKeys as import("vue").ComputedRef<(keyof F & string)[]>,
    clear,
    reset,
  }
}
