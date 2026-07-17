import {computed, onBeforeUnmount, ref, watch, type Ref} from "vue"
import {type MemberRow, type MemberStatus} from "@/composables/useMemberRows"
import {useTableSort} from "@/composables/useTableSort"

// ── Types ──────────────────────────────────────────────────────────────────────

export type FilterState = "all" | "yes" | "no"
export type SortKey = "name" | "username" | "role" | "status" | "memberSince" | "paid" | "wasMemberInPeriod"

const statusOrder: Record<MemberStatus, number> = {Current: 0, Former: 1, Never: 2}

// ── Composable ─────────────────────────────────────────────────────────────────

export function useMemberFilters(
  rows: Ref<MemberRow[]>,
  userSearchIndex: Ref<Map<number, string>>,
) {
  // searchInput is bound to the v-text-field (instant typing feedback).
  // search is the debounced value that filteredRows depends on — unit tests set it directly.
  const searchInput = ref("")
  const search = ref("")

  // Tri-state filters
  const memberFilter = ref<FilterState>("all")
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
      // Membership filter: yes = status "Current", no = not "Current"
      if (memberFilter.value === "yes" && r.status !== "Current") return false
      if (memberFilter.value === "no" && r.status === "Current") return false
      // Paid filter
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

  // Initialize to sort by name ascending (matches original behavior)
  const initialized = ref(false)
  watch(
    () => justFiltered.value.length,
    () => {
      if (!initialized.value && justFiltered.value.length > 0) {
        sortKey.value = "name"
        sortDir.value = "asc"
        initialized.value = true
      }
    },
    {immediate: true},
  )

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
