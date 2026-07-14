import {computed, onBeforeUnmount, ref, watch, type Ref} from "vue"
import {type MemberRow, type MemberStatus} from "@/composables/useMemberRows"

// ── Types ──────────────────────────────────────────────────────────────────────

export type FilterState = "all" | "yes" | "no"

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
  const sortKey = ref<"name" | "memberSince" | "status">("name")
  const sortAsc = ref(true)

  // Tri-state filters
  const memberFilter = ref<FilterState>("all")
  const paidFilter = ref<FilterState>("all")
  const incassoFilter = ref<FilterState>("all")

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

  const filteredRows = computed<MemberRow[]>(() => {
    // Search against precomputed per-user haystacks — cheap on every keystroke.
    const q = search.value.trim().toLowerCase()
    const terms = q ? q.split(/\s+/) : []

    return [...rows.value.filter((r) => {
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
      return true
    })].sort((a, b) => {
      let cmp = 0
      if (sortKey.value === "name") {
        cmp = a.fullName.localeCompare(b.fullName)
      } else if (sortKey.value === "memberSince") {
        const aVal = a.memberSince ?? ""
        const bVal = b.memberSince ?? ""
        cmp = aVal.localeCompare(bVal)
      } else if (sortKey.value === "status") {
        cmp = statusOrder[a.status] - statusOrder[b.status]
      }
      return sortAsc.value ? cmp : -cmp
    })
  })

  function toggleSort(key: "name" | "memberSince" | "status") {
    if (sortKey.value === key) {
      sortAsc.value = !sortAsc.value
    } else {
      sortKey.value = key
      sortAsc.value = true
    }
  }

  function sortIcon(key: "name" | "memberSince" | "status"): string {
    if (sortKey.value !== key) return "mdi-unfold-more-horizontal"
    return sortAsc.value ? "mdi-arrow-up" : "mdi-arrow-down"
  }

  return {
    searchInput,
    search,
    sortKey,
    sortAsc,
    memberFilter,
    paidFilter,
    incassoFilter,
    filteredRows,
    toggleSort,
    sortIcon,
    clearSearchDebounce,
  }
}
