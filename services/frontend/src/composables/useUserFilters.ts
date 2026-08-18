import {computed, toRefs, type Ref} from "vue"
import {type MemberRow, type MemberStatus} from "@/composables/useUserRows"
import {useTableSort} from "@/composables/useTableSort"
import {filtersFor, useRowFilters} from "@/composables/useRowFilters"

// ── Types ──────────────────────────────────────────────────────────────────────

export type FilterState = "all" | "yes" | "no"
export type MembershipStatusFilter = "all" | "current" | "former" | "never"
export type SortKey = "name" | "username" | "role" | "status" | "memberSince" | "paid" | "wasMemberInPeriod"

const statusOrder: Record<MemberStatus, number> = {Current: 0, Former: 1, Never: 2}

// Exhaustive, so a new MembershipStatusFilter value fails the typecheck rather than
// silently falling through to "Never". "all" never reaches here; it reads as unset.
const STATUS_BY_FILTER: Record<Exclude<MembershipStatusFilter, "all">, MemberStatus> = {
  current: "Current",
  former: "Former",
  never: "Never",
}

// ── Composable ─────────────────────────────────────────────────────────────────

export function useUserFilters(
  rows: Ref<MemberRow[]>,
  userSearchIndex: Ref<Map<number, string>>,
) {
  const filter = filtersFor<MemberRow>()

  // Declared cheapest-first: the dropdowns are O(1) per row and eliminate most of
  // them before the search filter has to touch the haystack.
  const {state, filteredRows: justFiltered} = useRowFilters(rows, {
    membershipStatusFilter: filter<MembershipStatusFilter>({
      initial: "all",
      unset: "all",
      match: (value) => {
        // Unreachable in practice: "all" reads as unset, so match is not called for it.
        if (value === "all") return () => true
        const wanted = STATUS_BY_FILTER[value]
        return (row) => row.status === wanted
      },
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
      // `clearable` on the search field writes null, not "".
      isUnset: (value) => (value ?? "").trim() === "",
      // Reading the index here rather than per row keeps it a tracked dependency
      // even when the dropdowns above have already excluded every row.
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

  const {search, membershipStatusFilter, paidFilter, incassoFilter, periodMemberFilter} = toRefs(state)
  // The template binds searchInput; tests set search. They are the same ref.
  const searchInput = search

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
  }
}
