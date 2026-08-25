import {computed, onBeforeUnmount, ref, toRefs, watch, type Ref} from "vue"
import {type MemberRow, type MemberStatus} from "@/composables/useUserRows"
import {filtersFor, useRowFilters} from "@/composables/useRowFilters"
import {useTableSort} from "@/composables/useTableSort"

// ── Types ──────────────────────────────────────────────────────────────────────

export type FilterState = "all" | "yes" | "no"
export type SortKey = "name" | "username" | "role" | "status" | "memberSince" | "paid" | "wasMemberInPeriod"

const statusOrder: Record<MemberStatus, number> = {Current: 0, Former: 1, Never: 2}

const comparators: Record<SortKey, (a: MemberRow, b: MemberRow) => number> = {
  name: (a, b) => a.fullName.localeCompare(b.fullName),
  username: (a, b) => a.username.localeCompare(b.username),
  role: (a, b) => a.role.localeCompare(b.role),
  status: (a, b) => statusOrder[a.status] - statusOrder[b.status],
  memberSince: (a, b) => (a.memberSince ?? "").localeCompare(b.memberSince ?? ""),
  paid: (a, b) => Number(a.paid) - Number(b.paid),
  wasMemberInPeriod: (a, b) => Number(a.wasMemberInPeriod) - Number(b.wasMemberInPeriod),
}

// How long the search field sits idle before the filter reads it.
const SEARCH_DEBOUNCE_MS = 200

// ── Composable ─────────────────────────────────────────────────────────────────

export function useUserFilters(
  rows: Ref<MemberRow[]>,
  userSearchIndex: Ref<Map<number, string>>,
) {
  const filter = filtersFor<MemberRow>()

  // Declared cheapest-first: each dropdown is one comparison per row and rules most of
  // them out before the search filter has to touch a haystack.
  const {state, filteredRows: matchingRows} = useRowFilters(rows, {
    memberFilter: filter<FilterState>({
      initial: "all",
      unset: "all",
      match: (value) => (row) => (row.status === "Current") === (value === "yes"),
    }),
    paidFilter: filter<FilterState>({
      initial: "all",
      unset: "all",
      match: (value) => (row) => row.paid === (value === "yes"),
    }),
    incassoFilter: filter<FilterState>({
      initial: "all",
      unset: "all",
      match: (value) => (row) => row.latestIncasso === (value === "yes"),
    }),
    periodMemberFilter: filter<FilterState>({
      initial: "all",
      unset: "all",
      match: (value) => (row) => row.wasMemberInPeriod === (value === "yes"),
    }),
    search: filter<string | null>({
      initial: "",
      unset: "",
      // The field is `clearable`, and its clear button writes null rather than "".
      isUnset: (value) => (value ?? "").trim() === "",
      // Read once per search term rather than once per row, so the index stays a tracked
      // dependency even when the dropdowns above have already excluded every row.
      match: (value) => {
        const terms = (value ?? "").trim().toLowerCase().split(/\s+/)
        const index = userSearchIndex.value
        return (row) => {
          const haystack = index.get(row.id) ?? ""
          return terms.every((term) => haystack.includes(term))
        }
      },
    }),
  })

  const {search, memberFilter, paidFilter, incassoFilter, periodMemberFilter} = toRefs(state)

  // No column sort until one is asked for, which is where the sort composable starts: the
  // rows arrive in the order the api defines (creation order) and the table keeps it.
  // Opening on "name" also made the tri-state cycle dishonest — unsorted was a state the
  // header could reach but never started in.
  const {sortedItems: filteredRows, sortKey, sortDir, toggleSort, sortIcon} =
    useTableSort<MemberRow, SortKey>(matchingRows, comparators)

  // The template and the sort headers speak of a direction; the tri-state cycle underneath
  // also has an "unsorted" state, which reads here as not ascending.
  const sortAsc = computed({
    get: () => sortDir.value === "asc",
    set: (ascending: boolean) => {
      sortDir.value = ascending ? "asc" : "desc"
    },
  })

  // searchInput is bound to the field for instant typing feedback; the filter reads the
  // debounced copy, so a keystroke does not re-filter every row.
  const searchInput = ref<string | null>("")
  let searchDebounceHandle: ReturnType<typeof setTimeout> | undefined

  const clearSearchDebounce = () => {
    if (searchDebounceHandle) {
      clearTimeout(searchDebounceHandle)
      searchDebounceHandle = undefined
    }
  }

  watch(searchInput, () => {
    clearSearchDebounce()
    searchDebounceHandle = setTimeout(() => {
      searchDebounceHandle = undefined
      search.value = searchInput.value
    }, SEARCH_DEBOUNCE_MS)
  })

  onBeforeUnmount(() => {
    clearSearchDebounce()
  })

  return {
    searchInput,
    search,
    sortKey,
    sortAsc,
    memberFilter,
    paidFilter,
    incassoFilter,
    periodMemberFilter,
    filteredRows,
    toggleSort,
    sortIcon,
    clearSearchDebounce,
  }
}
