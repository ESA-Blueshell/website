import {computed, onBeforeUnmount, ref, watch, type Ref} from "vue"
import {type MemberRow, type MemberStatus} from "@/composables/useUserRows"
import {useTableSort} from "@/composables/useTableSort"

// ── Types ──────────────────────────────────────────────────────────────────────

export type FilterState = "all" | "yes" | "no"
export type MembershipStatusFilter = "all" | "current" | "former" | "never"
export type SortKey = "name" | "username" | "role" | "status" | "memberSince" | "paid" | "wasMemberInPeriod"

const statusOrder: Record<MemberStatus, number> = {Current: 0, Former: 1, Never: 2}

// ── Composable ─────────────────────────────────────────────────────────────────

export function useUserFilters(
  rows: Ref<MemberRow[]>,
  userSearchIndex: Ref<Map<number, string>>,
) {
  // searchInput is bound to the v-text-field (instant typing feedback).
  // search is the debounced value that filteredRows depends on — unit tests set it directly.
  const searchInput = ref("")
  const search = ref("")

  // Membership status filter: all | current | former | never
  const membershipStatusFilter = ref<MembershipStatusFilter>("all")
  // Tri-state filters
  const paidFilter = ref<FilterState>("all")
  const incassoFilter = ref<FilterState>("all")
  const periodMemberFilter = ref<FilterState>("all")

  // Debounce search: copies searchInput → search after 200ms idle
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
    }, 200)
  })

  onBeforeUnmount(() => {
    clearSearchDebounce()
  })

  // Compute filtered rows (before sorting)
  const justFiltered = computed<MemberRow[]>(() => {
    // Search against precomputed per-user haystacks — cheap on every keystroke.
    const q = search.value.trim().toLowerCase()
    const terms = q ? q.split(/\s+/) : []

    return rows.value.filter((r) => {
      // Search filter
      if (terms.length > 0) {
        const haystack = userSearchIndex.value.get(r.id) ?? ""
        if (!terms.every((t) => haystack.includes(t))) return false
      }
      // Membership status filter: current | former | never
      if (membershipStatusFilter.value === "current" && r.status !== "Current") return false
      if (membershipStatusFilter.value === "former" && r.status !== "Former") return false
      if (membershipStatusFilter.value === "never" && r.status !== "Never") return false
      // Paid-in-period filter
      if (paidFilter.value === "yes" && !r.paid) return false
      if (paidFilter.value === "no" && r.paid) return false
      // Incasso filter
      if (incassoFilter.value === "yes" && !r.latestIncasso) return false
      if (incassoFilter.value === "no" && r.latestIncasso) return false
      // Selected contribution period membership filter
      if (periodMemberFilter.value === "yes" && !r.wasMemberInPeriod) return false
      if (periodMemberFilter.value === "no" && r.wasMemberInPeriod) return false
      return true
    })
  })

  // Define sort comparators
  const memberRowComparators: Record<SortKey, (a: MemberRow, b: MemberRow) => number> = {
    name: (a, b) => a.fullName.localeCompare(b.fullName),
    username: (a, b) => a.username.localeCompare(b.username),
    role: (a, b) => a.role.localeCompare(b.role),
    status: (a, b) => statusOrder[a.status] - statusOrder[b.status],
    memberSince: (a, b) => {
      const aVal = a.memberSince ?? ""
      const bVal = b.memberSince ?? ""
      return aVal.localeCompare(bVal)
    },
    paid: (a, b) => Number(a.paid) - Number(b.paid),
    wasMemberInPeriod: (a, b) => Number(a.wasMemberInPeriod) - Number(b.wasMemberInPeriod),
  }

  // Use the useTableSort composable
  const {
    sortedItems: filteredRows,
    sortKey,
    sortDir,
  } = useTableSort(justFiltered, memberRowComparators)

  // No default sort — rows load in natural API-returned order.
  // Users can click column headers to sort manually.
  // The tri-state cycle (ascending → descending → unsorted) is preserved.

  // Expose sortAsc for backward compatibility with tests/templates
  const sortAsc = computed({
    get: () => sortDir.value === "asc",
    set: (val) => {
      sortDir.value = val ? "asc" : "desc"
    },
  })

  // Wrap toggleSort to maintain the three-state cycle pattern
  function toggleSort(key: SortKey) {
    // If switching to a new key, reset to ascending
    if (sortKey.value !== key) {
      sortKey.value = key
      sortDir.value = "asc"
    } else if (sortDir.value === "asc") {
      sortDir.value = "desc"
    } else {
      // Third click: reset to no sort (sortKey null, sortDir none)
      // The template will then show the original unfiltered order.
      sortKey.value = null
      sortDir.value = "none"
    }
  }

  function sortIcon(key: SortKey): string {
    if (sortKey.value !== key) return "mdi-unfold-more-horizontal"
    return sortDir.value === "asc" ? "mdi-arrow-up" : "mdi-arrow-down"
  }

  return {
    searchInput,
    search,
    sortKey,
    sortAsc,
    membershipStatusFilter,
    paidFilter,
    incassoFilter,
    periodMemberFilter,
    filteredRows,
    toggleSort,
    sortIcon,
    clearSearchDebounce,
  }
}
