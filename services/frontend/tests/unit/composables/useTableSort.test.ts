import {describe, expect, it} from "vitest"
import {ref} from "vue"
import {useTableSort} from "@/composables/useTableSort"

interface TestRow {
  id: number
  name: string
  value: number | null
}

function makeRow(id: number, name: string, value: number | null = null): TestRow {
  return {id, name, value}
}

describe("useTableSort", () => {
  it("returns items in original order when no sort is active", () => {
    const items = ref([makeRow(3, "Charlie"), makeRow(1, "Alice"), makeRow(2, "Bob")])
    const comparators = {
      name: (a: TestRow, b: TestRow) => a.name.localeCompare(b.name),
    }
    const {sortedItems} = useTableSort(items, comparators)

    expect(sortedItems.value).toEqual([
      makeRow(3, "Charlie"),
      makeRow(1, "Alice"),
      makeRow(2, "Bob"),
    ])
  })

  it("sorts items in ascending order when key is set to asc", () => {
    const items = ref([makeRow(3, "Charlie"), makeRow(1, "Alice"), makeRow(2, "Bob")])
    const comparators = {
      name: (a: TestRow, b: TestRow) => a.name.localeCompare(b.name),
    }
    const {sortedItems, sortKey, sortDir} = useTableSort(items, comparators)

    sortKey.value = "name"
    sortDir.value = "asc"

    expect(sortedItems.value).toEqual([
      makeRow(1, "Alice"),
      makeRow(2, "Bob"),
      makeRow(3, "Charlie"),
    ])
  })

  it("sorts items in descending order when key is set to desc", () => {
    const items = ref([makeRow(3, "Charlie"), makeRow(1, "Alice"), makeRow(2, "Bob")])
    const comparators = {
      name: (a: TestRow, b: TestRow) => a.name.localeCompare(b.name),
    }
    const {sortedItems, sortKey, sortDir} = useTableSort(items, comparators)

    sortKey.value = "name"
    sortDir.value = "desc"

    expect(sortedItems.value).toEqual([
      makeRow(3, "Charlie"),
      makeRow(2, "Bob"),
      makeRow(1, "Alice"),
    ])
  })

  it("toggleSort cycles: asc → desc → none on the same key", () => {
    const items = ref([makeRow(1, "A"), makeRow(2, "B")])
    const comparators = {
      name: (a: TestRow, b: TestRow) => a.name.localeCompare(b.name),
    }
    const {sortKey, sortDir, toggleSort} = useTableSort(items, comparators)

    // Initial state
    expect(sortKey.value).toBeNull()

    // First click: switch to this key in asc
    toggleSort("name")
    expect(sortKey.value).toBe("name")
    expect(sortDir.value).toBe("asc")

    // Second click on same key: flip to desc
    toggleSort("name")
    expect(sortKey.value).toBe("name")
    expect(sortDir.value).toBe("desc")

    // Third click on same key: reset to none
    toggleSort("name")
    expect(sortKey.value).toBeNull()
    expect(sortDir.value).toBe("none")
  })

  it("toggleSort on different key resets to asc", () => {
    const items = ref([makeRow(1, "A", 10)])
    const comparators = {
      name: (a: TestRow, b: TestRow) => a.name.localeCompare(b.name),
      value: (a: TestRow, b: TestRow) => (a.value ?? 0) - (b.value ?? 0),
    }
    const {sortKey, sortDir, toggleSort} = useTableSort(items, comparators)

    // Start with name desc
    sortKey.value = "name"
    sortDir.value = "desc"

    // Switch to value: should reset to asc
    toggleSort("value")
    expect(sortKey.value).toBe("value")
    expect(sortDir.value).toBe("asc")
  })

  it("sortIcon returns correct icons", () => {
    const items = ref([makeRow(1, "A")])
    const comparators = {
      name: (a: TestRow, b: TestRow) => a.name.localeCompare(b.name),
      value: (a: TestRow, b: TestRow) => (a.value ?? 0) - (b.value ?? 0),
    }
    const {sortKey, sortDir, sortIcon} = useTableSort(items, comparators)

    // Not sorting: unfold icon
    expect(sortIcon("name")).toBe("mdi-unfold-more-horizontal")
    expect(sortIcon("value")).toBe("mdi-unfold-more-horizontal")

    // Sorting asc: up arrow
    sortKey.value = "name"
    sortDir.value = "asc"
    expect(sortIcon("name")).toBe("mdi-arrow-up")
    expect(sortIcon("value")).toBe("mdi-unfold-more-horizontal")

    // Sorting desc: down arrow
    sortDir.value = "desc"
    expect(sortIcon("name")).toBe("mdi-arrow-down")
    expect(sortIcon("value")).toBe("mdi-unfold-more-horizontal")
  })

  it("ariaSort returns correct aria values", () => {
    const items = ref([makeRow(1, "A")])
    const comparators = {
      name: (a: TestRow, b: TestRow) => a.name.localeCompare(b.name),
    }
    const {sortKey, sortDir, ariaSort} = useTableSort(items, comparators)

    // Not sorting
    expect(ariaSort("name")).toBe("none")

    // Sorting asc
    sortKey.value = "name"
    sortDir.value = "asc"
    expect(ariaSort("name")).toBe("ascending")

    // Sorting desc
    sortDir.value = "desc"
    expect(ariaSort("name")).toBe("descending")
  })

  it("handles null values by sorting them last in asc order", () => {
    const items = ref([
      makeRow(1, "A", null),
      makeRow(2, "B", 10),
      makeRow(3, "C", 5),
      makeRow(4, "D", null),
    ])
    const comparators = {
      value: (a: TestRow, b: TestRow) => {
        const aVal = a.value ?? Number.MAX_SAFE_INTEGER
        const bVal = b.value ?? Number.MAX_SAFE_INTEGER
        return aVal - bVal
      },
    }
    const {sortedItems, sortKey, sortDir} = useTableSort(items, comparators)

    sortKey.value = "value"
    sortDir.value = "asc"

    expect(sortedItems.value).toEqual([
      makeRow(3, "C", 5),
      makeRow(2, "B", 10),
      makeRow(1, "A", null),
      makeRow(4, "D", null),
    ])
  })

  it("sorts multiple columns with the same comparator", () => {
    const items = ref([
      makeRow(3, "C"),
      makeRow(1, "A"),
      makeRow(2, "B"),
    ])
    const comparators = {
      name: (a: TestRow, b: TestRow) => a.name.localeCompare(b.name),
    }
    const {sortedItems, sortKey, sortDir} = useTableSort(items, comparators)

    // Sort by name asc
    sortKey.value = "name"
    sortDir.value = "asc"

    const asc = sortedItems.value.map((r) => r.name)
    expect(asc).toEqual(["A", "B", "C"])

    // Switch to desc
    sortDir.value = "desc"
    const desc = sortedItems.value.map((r) => r.name)
    expect(desc).toEqual(["C", "B", "A"])
  })

  it("reactively updates when items change", () => {
    const items = ref([makeRow(2, "B"), makeRow(1, "A")])
    const comparators = {
      name: (a: TestRow, b: TestRow) => a.name.localeCompare(b.name),
    }
    const {sortedItems, sortKey, sortDir} = useTableSort(items, comparators)

    sortKey.value = "name"
    sortDir.value = "asc"

    expect(sortedItems.value).toEqual([makeRow(1, "A"), makeRow(2, "B")])

    // Update items
    items.value = [makeRow(3, "C"), makeRow(1, "A")]
    expect(sortedItems.value).toEqual([makeRow(1, "A"), makeRow(3, "C")])
  })
})
