import {describe, expect, it} from "vitest"
import {ref} from "vue"
import {useUserSelection} from "@/composables/useUserSelection"

describe("useUserSelection", () => {
  it("starts with nothing selected and an unchecked header", () => {
    const selection = useUserSelection(ref([1, 2, 3]))

    expect(selection.selectedIdsArray.value).toEqual([])
    expect(selection.selectionCount.value).toBe(0)
    expect(selection.hasSelection.value).toBe(false)
    expect(selection.headerState.value).toBe("unchecked")
  })

  it("toggles one member in and back out again", () => {
    const selection = useUserSelection(ref([1, 2]))

    selection.toggle(1)
    expect(selection.isSelected(1)).toBe(true)
    expect(selection.hasSelection.value).toBe(true)

    selection.toggle(1)
    expect(selection.isSelected(1)).toBe(false)
    expect(selection.selectionCount.value).toBe(0)
  })

  it("keeps a member selected after a filter hides them, because the action acts on the set and not the view", () => {
    const displayed = ref([1, 2, 3])
    const selection = useUserSelection(displayed)

    selection.toggle(2)
    displayed.value = [1, 3]

    expect(selection.isSelected(2)).toBe(true)
    expect(selection.selectedIdsArray.value).toEqual([2])
    expect(selection.headerState.value).toBe("unchecked")
  })

  it("reads the header against the displayed rows only", () => {
    const displayed = ref([1, 2])
    const selection = useUserSelection(displayed)

    selection.toggle(1)
    expect(selection.headerState.value).toBe("indeterminate")
    expect(selection.headerIndeterminate.value).toBe(true)
    expect(selection.headerChecked.value).toBe(false)

    selection.toggle(2)
    expect(selection.headerState.value).toBe("checked")
    expect(selection.headerChecked.value).toBe(true)
    expect(selection.headerIndeterminate.value).toBe(false)
  })

  it("shows an unchecked header when a filter leaves nothing to display", () => {
    const displayed = ref<number[]>([])
    const selection = useUserSelection(displayed)

    selection.toggle(9)
    expect(selection.headerState.value).toBe("unchecked")
  })

  it("selects every displayed row from a header the operator half-filled", () => {
    const displayed = ref([1, 2, 3])
    const selection = useUserSelection(displayed)

    selection.toggle(1)
    selection.toggleHeader()

    expect(selection.selectedIdsArray.value).toEqual([1, 2, 3])
    expect(selection.headerState.value).toBe("checked")
  })

  it("clears only the displayed rows from the header, leaving a hidden member selected", () => {
    const displayed = ref([1, 2, 3])
    const selection = useUserSelection(displayed)

    selection.toggleHeader()
    displayed.value = [1, 2]
    selection.toggleHeader()

    expect(selection.selectedIdsArray.value).toEqual([3])
  })

  it("empties the whole set, hidden members included", () => {
    const displayed = ref([1, 2])
    const selection = useUserSelection(displayed)

    selection.toggleHeader()
    displayed.value = [1]
    selection.clear()

    expect(selection.selectedIdsArray.value).toEqual([])
    expect(selection.hasSelection.value).toBe(false)
  })
})
