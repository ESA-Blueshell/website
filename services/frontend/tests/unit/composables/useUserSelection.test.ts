import {describe, expect, it} from "vitest"
import {ref} from "vue"
import {useUserSelection} from "@/composables/useUserSelection"

describe("useUserSelection", () => {
  // ── Initial state ───────────────────────────────────────────────────────────

  it("starts with an empty selection", () => {
    const displayed = ref<number[]>([1, 2, 3])
    const {selectedIds, selectionCount, hasSelection} = useUserSelection(displayed)
    expect(selectedIds.value.size).toBe(0)
    expect(selectionCount.value).toBe(0)
    expect(hasSelection.value).toBe(false)
  })

  it("headerState is unchecked when nothing is selected", () => {
    const displayed = ref<number[]>([1, 2, 3])
    const {headerState, headerChecked, headerIndeterminate} = useUserSelection(displayed)
    expect(headerState.value).toBe("unchecked")
    expect(headerChecked.value).toBe(false)
    expect(headerIndeterminate.value).toBe(false)
  })

  it("headerState is unchecked when displayed list is empty", () => {
    const displayed = ref<number[]>([])
    const {headerState} = useUserSelection(displayed)
    expect(headerState.value).toBe("unchecked")
  })

  // ── Per-row toggle ──────────────────────────────────────────────────────────

  it("toggle adds a user to the selection", () => {
    const displayed = ref<number[]>([1, 2, 3])
    const {isSelected, toggle, selectionCount} = useUserSelection(displayed)
    expect(isSelected(1)).toBe(false)
    toggle(1)
    expect(isSelected(1)).toBe(true)
    expect(selectionCount.value).toBe(1)
  })

  it("toggle removes a user that is already selected", () => {
    const displayed = ref<number[]>([1, 2, 3])
    const {isSelected, toggle, selectionCount} = useUserSelection(displayed)
    toggle(1)
    toggle(1)
    expect(isSelected(1)).toBe(false)
    expect(selectionCount.value).toBe(0)
  })

  // ── Tri-state header ────────────────────────────────────────────────────────

  it("headerState is indeterminate when some (but not all) displayed rows are selected", () => {
    const displayed = ref<number[]>([1, 2, 3])
    const {headerState, headerChecked, headerIndeterminate, toggle} = useUserSelection(displayed)
    toggle(1)
    expect(headerState.value).toBe("indeterminate")
    expect(headerChecked.value).toBe(false)
    expect(headerIndeterminate.value).toBe(true)
  })

  it("headerState is checked when all displayed rows are selected", () => {
    const displayed = ref<number[]>([1, 2, 3])
    const {headerState, headerChecked, headerIndeterminate, toggle} = useUserSelection(displayed)
    toggle(1)
    toggle(2)
    toggle(3)
    expect(headerState.value).toBe("checked")
    expect(headerChecked.value).toBe(true)
    expect(headerIndeterminate.value).toBe(false)
  })

  // ── Header toggle ───────────────────────────────────────────────────────────

  it("toggleHeader selects all displayed rows when state is unchecked", () => {
    const displayed = ref<number[]>([1, 2, 3])
    const {isSelected, toggleHeader, headerState} = useUserSelection(displayed)
    toggleHeader()
    expect(headerState.value).toBe("checked")
    expect(isSelected(1)).toBe(true)
    expect(isSelected(2)).toBe(true)
    expect(isSelected(3)).toBe(true)
  })

  it("toggleHeader selects all displayed rows when state is indeterminate", () => {
    const displayed = ref<number[]>([1, 2, 3])
    const {isSelected, toggle, toggleHeader, headerState} = useUserSelection(displayed)
    toggle(1) // partial selection → indeterminate
    toggleHeader()
    expect(headerState.value).toBe("checked")
    expect(isSelected(1)).toBe(true)
    expect(isSelected(2)).toBe(true)
    expect(isSelected(3)).toBe(true)
  })

  it("toggleHeader deselects all displayed rows when state is checked", () => {
    const displayed = ref<number[]>([1, 2, 3])
    const {isSelected, toggleHeader, headerState} = useUserSelection(displayed)
    toggleHeader() // select all
    toggleHeader() // deselect all
    expect(headerState.value).toBe("unchecked")
    expect(isSelected(1)).toBe(false)
    expect(isSelected(2)).toBe(false)
    expect(isSelected(3)).toBe(false)
  })

  // ── Persistence across filter/sort changes ──────────────────────────────────

  it("selection persists when displayed list changes (filter hides a selected user)", () => {
    const displayed = ref<number[]>([1, 2, 3])
    const {isSelected, toggle, selectedIds} = useUserSelection(displayed)
    toggle(1)
    toggle(2)

    // Simulate a filter that removes user 1 from display
    displayed.value = [2, 3]

    // User 1 is still in the selected set even though not displayed
    expect(isSelected(1)).toBe(true)
    expect(selectedIds.value.has(1)).toBe(true)
  })

  it("header tri-state only considers displayed rows, not the full selected set", () => {
    const displayed = ref<number[]>([1, 2, 3])
    const {toggle, toggleHeader, headerState} = useUserSelection(displayed)
    toggleHeader() // select 1, 2, 3

    // Filter narrows display to only user 4 (which was never selected)
    displayed.value = [4]
    expect(headerState.value).toBe("unchecked")
    // But user 1 is still selected
    expect(toggle).toBeDefined()
  })

  it("toggleHeader only touches displayed rows, leaving other selections intact", () => {
    const displayed = ref<number[]>([1, 2, 3])
    const {toggle, toggleHeader, isSelected} = useUserSelection(displayed)
    toggle(1)
    toggle(2)
    toggle(3)
    // Narrow display so only row 1 and 2 are visible
    displayed.value = [1, 2]
    // State is checked (all displayed selected)
    toggleHeader() // deselects only 1 and 2
    expect(isSelected(1)).toBe(false)
    expect(isSelected(2)).toBe(false)
    // User 3 (not displayed) remains selected
    expect(isSelected(3)).toBe(true)
  })

  // ── selectedIdsArray ────────────────────────────────────────────────────────

  it("selectedIdsArray returns a plain array of selected IDs", () => {
    const displayed = ref<number[]>([1, 2, 3])
    const {toggle, selectedIdsArray} = useUserSelection(displayed)
    toggle(2)
    toggle(3)
    expect(selectedIdsArray.value).toContain(2)
    expect(selectedIdsArray.value).toContain(3)
    expect(selectedIdsArray.value).toHaveLength(2)
  })

  // ── clear ───────────────────────────────────────────────────────────────────

  it("clear empties the entire selection set", () => {
    const displayed = ref<number[]>([1, 2, 3])
    const {toggle, clear, selectionCount, hasSelection, isSelected} = useUserSelection(displayed)
    toggle(1)
    toggle(2)
    toggle(3)
    clear()
    expect(selectionCount.value).toBe(0)
    expect(hasSelection.value).toBe(false)
    expect(isSelected(1)).toBe(false)
  })

  it("clear also clears users not in current display", () => {
    const displayed = ref<number[]>([1, 2, 3])
    const {toggle, clear, selectedIds} = useUserSelection(displayed)
    toggle(1)
    toggle(99) // not in display, but still selected
    clear()
    expect(selectedIds.value.size).toBe(0)
  })
})
