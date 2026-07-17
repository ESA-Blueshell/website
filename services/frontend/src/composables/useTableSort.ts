import {computed, ref, type Ref} from "vue"

/**
 * Generic tri-state table sort composable.
 *
 * Clicking a column header cycles: ascending → descending → no sort (original order).
 * Null values in string fields sort last consistently.
 *
 * @template T The row item type
 * @template K The sort key type
 *
 * @param items - Reactive reference to the items array
 * @param comparators - Map of sort key to comparator function (a, b) => number
 * @returns Object with sortedItems, sortKey, sortDir, toggleSort, sortIcon, ariaSort
 */
export function useTableSort<T, K extends string>(
  items: Ref<T[]>,
  comparators: Record<K, (a: T, b: T) => number>,
) {
  // sortKey is null when no column sort is active (natural/default order).
  const sortKey = ref<K | null>(null)
  // sortDir: "asc" | "desc" | "none" (none = no sorting, using original order)
  const sortDir = ref<"asc" | "desc" | "none">("none")

  const sortedItems = computed<T[]>(() => {
    const result = [...items.value]

    // If no sort key is set, return in original order.
    if (sortKey.value === null || sortDir.value === "none") {
      return result
    }

    const comparator = comparators[sortKey.value as K]
    if (!comparator) {
      return result
    }

    result.sort((a, b) => {
      let cmp = comparator(a, b)
      return sortDir.value === "asc" ? cmp : -cmp
    })

    return result
  })

  /**
   * Toggle sort state for a given key: cycles ascending → descending → none.
   * If the key is different, switch to the new key in ascending order.
   */
  function toggleSort(key: K) {
    if (sortKey.value !== key) {
      sortKey.value = key
      sortDir.value = "asc"
    } else if (sortDir.value === "asc") {
      sortDir.value = "desc"
    } else {
      sortKey.value = null
      sortDir.value = "none"
    }
  }

  /**
   * Return the icon for a column header.
   * - "mdi-unfold-more-horizontal" if not sorting by this column
   * - "mdi-arrow-up" if sorting ascending
   * - "mdi-arrow-down" if sorting descending
   */
  function sortIcon(key: K): string {
    if (sortKey.value !== key) return "mdi-unfold-more-horizontal"
    return sortDir.value === "asc" ? "mdi-arrow-up" : "mdi-arrow-down"
  }

  /**
   * Return the aria-sort value for accessibility.
   * - "none" if not sorting by this column
   * - "ascending" if sorting ascending
   * - "descending" if sorting descending
   */
  function ariaSort(key: K): "none" | "ascending" | "descending" {
    if (sortKey.value !== key) return "none"
    return sortDir.value === "asc" ? "ascending" : "descending"
  }

  return {
    sortedItems,
    sortKey,
    sortDir,
    toggleSort,
    sortIcon,
    ariaSort,
  }
}
