import {computed, ref, type Ref} from "vue"

//
// Tracks the set of selected user IDs for bulk actions.
//
// Key behaviours:
//   • The selected set is PERSISTENT — it survives filter/sort changes. A user
//     that is filtered out of view stays selected; bulk actions act on the
//     entire set regardless of what is currently displayed.
//   • The header checkbox reflects a tri-state computed against the currently
//     DISPLAYED (filtered + sorted) rows only:
//       – all   → every displayed row is selected (header = checked)
//       – some  → at least one displayed row is selected (header = indeterminate)
//       – none  → no displayed row is selected (header = unchecked)
//   • Toggling the header checkbox adds/removes only the currently-displayed
//     rows from the persistent set — rows outside the current view are untouched.
//   • `clear()` empties the entire set (used when leaving the page or after an
//     action has been executed successfully).

export type HeaderCheckboxState = "checked" | "indeterminate" | "unchecked"

export function useUserSelection(displayedIds: Ref<number[]>) {
  /** Persistent selected set — survives filter/sort changes. */
  const selectedIds = ref<Set<number>>(new Set())

  function isSelected(userId: number): boolean {
    return selectedIds.value.has(userId)
  }

  function toggle(userId: number) {
    const next = new Set(selectedIds.value)
    if (next.has(userId)) {
      next.delete(userId)
    } else {
      next.add(userId)
    }
    selectedIds.value = next
  }

  const headerState = computed<HeaderCheckboxState>(() => {
    if (displayedIds.value.length === 0) return "unchecked"
    const selectedCount = displayedIds.value.filter((id) => selectedIds.value.has(id)).length
    if (selectedCount === 0) return "unchecked"
    if (selectedCount === displayedIds.value.length) return "checked"
    return "indeterminate"
  })

  /** True when the header checkbox should show the indeterminate icon. */
  const headerIndeterminate = computed(() => headerState.value === "indeterminate")
  /** Model-value for the header v-checkbox (true = all displayed selected). */
  const headerChecked = computed(() => headerState.value === "checked")

  /** Toggle all currently-displayed rows. */
  function toggleHeader() {
    const next = new Set(selectedIds.value)
    if (headerState.value === "checked") {
      // Deselect all displayed
      for (const id of displayedIds.value) next.delete(id)
    } else {
      // Select all displayed (covers both "some" and "none" states)
      for (const id of displayedIds.value) next.add(id)
    }
    selectedIds.value = next
  }

  const selectionCount = computed(() => selectedIds.value.size)
  const hasSelection = computed(() => selectedIds.value.size > 0)

  /** Snapshot of selected IDs as a plain array (for API calls). */
  const selectedIdsArray = computed(() => Array.from(selectedIds.value))

  function clear() {
    selectedIds.value = new Set()
  }

  return {
    selectedIds,
    selectedIdsArray,
    selectionCount,
    hasSelection,
    isSelected,
    toggle,
    headerState,
    headerChecked,
    headerIndeterminate,
    toggleHeader,
    clear,
  }
}
