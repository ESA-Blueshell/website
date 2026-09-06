import {computed, onBeforeUnmount, ref, watch, type ComputedRef, type Ref} from "vue"

/**
 * The state a management table keeps: which page it is on, what was typed into its search box,
 * and which rows are open.
 *
 * The manager pages ask the api for one page at a time and let the reader open a row for its
 * detail, so all three answer the same questions and were written twice. Filters stay with the
 * caller — they are the page's own vocabulary — and reach the api through the closure it hands
 * in, which is why this knows nothing about a status or a category.
 */

/** What one page of rows is asked for. The caller adds its own filters when it builds the query. */
export interface PageQuery {
  /** Zero-based, as the api counts pages. `page` below is one-based, as a reader counts them. */
  page: number
  size: number
  /** Trimmed and dropped when empty, so a caller never has to decide what a blank search means. */
  search: string | undefined
}

/**
 * One page as it comes back. A read that could not be answered reports an empty page rather than
 * a refusal, so a table renders "no rows" instead of stale ones.
 */
export interface PageOf<T> {
  rows: T[]
  totalElements: number
  totalPages: number
}

/** A row a table can open: identity is the id, so the two managers expand rows the same way. */
export interface Expandable {
  id?: number | null
}

export interface PagedTable<T> {
  rows: Ref<T[]>
  loading: Ref<boolean>
  /** One-based, as a `v-pagination` reads it. */
  page: Ref<number>
  totalPages: Ref<number>
  totalElements: Ref<number>
  /** Nullable because a clearable `v-text-field` writes null, not "". */
  search: Ref<string | null>
  pageRangeLabel: ComputedRef<string>
  isExpanded: (row: T) => boolean
  toggleExpanded: (row: T) => void
  refresh: () => Promise<void>
  /** Back to the first page and re-read, for a filter change or anything that adds a row. */
  resetToFirstPage: () => void
}

export interface PagedTableOptions {
  pageSize?: number
  /** Long enough that a typed word is one request, short enough to feel immediate. */
  searchDebounceMs?: number
}

export function usePagedTable<T extends Expandable>(
  load: (query: PageQuery) => Promise<PageOf<T>>,
  options: PagedTableOptions = {},
): PagedTable<T> {
  const size = options.pageSize ?? 50
  const debounceMs = options.searchDebounceMs ?? 250

  const rows = ref([]) as Ref<T[]>
  const loading = ref(false)
  const page = ref(1)
  const totalPages = ref(1)
  const totalElements = ref(0)
  const search = ref<string | null>("")
  const expanded = ref<number[]>([])
  let debounceHandle: ReturnType<typeof setTimeout> | undefined

  const clearDebounce = () => {
    if (debounceHandle === undefined) return
    clearTimeout(debounceHandle)
    debounceHandle = undefined
  }

  const pageRangeLabel = computed(() => {
    if (totalElements.value === 0 || rows.value.length === 0) return `0 of ${totalElements.value}`
    const start = (page.value - 1) * size + 1
    const end = Math.min(start + rows.value.length - 1, totalElements.value)
    return `${start}-${end} of ${totalElements.value}`
  })

  const isExpanded = (row: T): boolean =>
    row.id != null && expanded.value.includes(row.id)

  const toggleExpanded = (row: T) => {
    if (row.id == null) return
    expanded.value = isExpanded(row)
      ? expanded.value.filter(one => one !== row.id)
      : [...expanded.value, row.id]
  }

  const refresh = async (): Promise<void> => {
    loading.value = true
    try {
      const typed = (search.value ?? "").trim()
      const answered = await load({page: Math.max(0, page.value - 1), size, search: typed || undefined})
      totalElements.value = answered.totalElements
      totalPages.value = Math.max(1, answered.totalPages)
      // A page past the end reads the last one instead: the watch below re-reads, so the rows
      // this answer carries are the wrong ones to keep.
      if (page.value > totalPages.value) {
        page.value = totalPages.value
        return
      }
      rows.value = answered.rows
    } finally {
      loading.value = false
    }
  }

  const resetToFirstPage = () => {
    expanded.value = []
    // Moving the page re-reads through the watch; already being on it does not.
    if (page.value !== 1) {
      page.value = 1
      return
    }
    void refresh()
  }

  watch(page, () => {
    expanded.value = []
    void refresh()
  })

  watch(search, () => {
    clearDebounce()
    debounceHandle = setTimeout(() => {
      debounceHandle = undefined
      resetToFirstPage()
    }, debounceMs)
  })

  onBeforeUnmount(clearDebounce)

  return {
    rows,
    loading,
    page,
    totalPages,
    totalElements,
    search,
    pageRangeLabel,
    isExpanded,
    toggleExpanded,
    refresh,
    resetToFirstPage,
  }
}
