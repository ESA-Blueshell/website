import {describe, expect, it, vi} from "vitest"
import {nextTick, ref} from "vue"
import {filtersFor, useRowFilters} from "@/composables/useRowFilters"

interface Row {
  id: number
  name: string
  state: "open" | "closed"
  tags: string[]
}

const rows: Row[] = [
  {id: 1, name: "alpha", state: "open", tags: ["red"]},
  {id: 2, name: "beta", state: "closed", tags: ["blue"]},
  {id: 3, name: "gamma", state: "open", tags: ["red", "blue"]},
]

const filter = filtersFor<Row>()

const nameFilter = filter<string>({
  initial: "",
  unset: "",
  match: (value) => {
    const terms = value.trim().toLowerCase().split(/\s+/)
    return (row) => terms.every((t) => row.name.includes(t))
  },
})

const stateFilter = filter<"all" | Row["state"]>({
  initial: "all",
  unset: "all",
  match: (value) => (row) => row.state === value,
})

describe("useRowFilters", () => {
  it("returns every row while all filters are unset", () => {
    const {filteredRows} = useRowFilters(rows, {state: stateFilter, name: nameFilter})
    expect(filteredRows.value).toHaveLength(3)
  })

  it("does not hand back the source array identity", () => {
    const {filteredRows} = useRowFilters(rows, {state: stateFilter})
    expect(filteredRows.value).not.toBe(rows)
  })

  it("applies a filter once its value leaves unset", () => {
    const {state, filteredRows} = useRowFilters(rows, {state: stateFilter})
    state.state = "open"
    expect(filteredRows.value.map((r) => r.id)).toEqual([1, 3])
  })

  it("combines filters conjunctively", () => {
    const {state, filteredRows} = useRowFilters(rows, {state: stateFilter, name: nameFilter})
    state.state = "open"
    state.name = "gam"
    expect(filteredRows.value.map((r) => r.id)).toEqual([3])
  })

  it("treats an empty array value as unset when given a test", () => {
    // Object.is([], []) is false, so a value-compared array filter would read as
    // permanently active and reject every row on first render.
    const tagFilter = filter<string[]>({
      initial: [],
      unset: [],
      isUnset: (value) => value.length === 0,
      match: (value) => (row) => value.some((t) => row.tags.includes(t)),
    })
    const {filteredRows, activeKeys} = useRowFilters(rows, {tags: tagFilter})
    expect(activeKeys.value).toEqual([])
    expect(filteredRows.value).toHaveLength(3)
  })

  it("builds each matcher once per value rather than once per row", () => {
    const built = vi.fn()
    const counted = filter<string>({
      initial: "",
      unset: "",
      match: (value) => {
        built()
        return (row) => row.name.includes(value)
      },
    })
    const {state, filteredRows} = useRowFilters(rows, {counted})
    state.counted = "a"
    void filteredRows.value
    expect(built).toHaveBeenCalledTimes(1)
  })

  it("tracks a reactive read made inside match even when no row reaches the test", async () => {
    // The state filter rejects every row, so the returned test never runs. The
    // external ref must still be a dependency, or the result goes stale.
    const external = ref("alpha")
    const dependent = filter<boolean>({
      initial: false,
      unset: false,
      match: (value) => {
        const wanted = external.value
        return (row) => value && row.name === wanted
      },
    })
    const {state, filteredRows} = useRowFilters(rows, {state: stateFilter, dependent})
    state.dependent = true
    state.state = "closed"
    expect(filteredRows.value).toEqual([])

    external.value = "beta"
    await nextTick()
    state.state = "all"
    expect(filteredRows.value.map((r) => r.id)).toEqual([2])
  })

  it("shows no rows and logs when a filter throws, rather than more than asked for", () => {
    const error = vi.spyOn(console, "error").mockImplementation(() => {})
    const broken = filter<boolean>({
      initial: false,
      unset: false,
      match: () => () => {
        throw new Error("boom")
      },
    })
    const {state, filteredRows} = useRowFilters(rows, {broken})
    state.broken = true
    expect(filteredRows.value).toEqual([])
    expect(error).toHaveBeenCalled()
    error.mockRestore()
  })

  it("clear returns filters to unset while reset returns them to their initial value", () => {
    const preset = filter<"all" | Row["state"]>({
      initial: "open",
      unset: "all",
      match: (value) => (row) => row.state === value,
    })
    const {state, clear, reset, activeKeys} = useRowFilters(rows, {preset})
    expect(activeKeys.value).toEqual(["preset"])

    clear()
    expect(state.preset).toBe("all")
    expect(activeKeys.value).toEqual([])

    reset()
    expect(state.preset).toBe("open")
  })

  it("tolerates an undefined row source", () => {
    const source = ref<Row[] | undefined>(undefined)
    const {filteredRows} = useRowFilters(source, {state: stateFilter})
    expect(filteredRows.value).toEqual([])
  })

  it("clear resets a filter whose unset is detected by a test", () => {
    const tagFilter = filter<string[]>({
      initial: [],
      unset: [],
      isUnset: (value) => value.length === 0,
      match: (value) => (row) => value.some((t) => row.tags.includes(t)),
    })
    const {state, clear, activeKeys} = useRowFilters(rows, {tags: tagFilter})
    state.tags = ["red"]
    expect(activeKeys.value).toEqual(["tags"])
    clear()
    expect(state.tags).toEqual([])
    expect(activeKeys.value).toEqual([])
  })

  it("does not let a filter value alias its own initial", () => {
    const tagFilter = filter<string[]>({
      initial: [],
      unset: [],
      isUnset: (value) => value.length === 0,
      match: (value) => (row) => value.some((t) => row.tags.includes(t)),
    })
    const {state, reset, activeKeys} = useRowFilters(rows, {tags: tagFilter})
    state.tags.push("red")
    reset()
    // Would still hold "red" if the ref shared the definition's array.
    expect(state.tags).toEqual([])
    expect(activeKeys.value).toEqual([])
  })

  it("survives a value its own unset test cannot read", () => {
    const error = vi.spyOn(console, "error").mockImplementation(() => {})
    const fragile = filter<string | null>({
      initial: "",
      unset: "",
      isUnset: (value) => (value as string).trim() === "",
      match: (value) => (row) => row.name.includes(String(value)),
    })
    const {state, filteredRows} = useRowFilters(rows, {fragile})
    state.fragile = null
    expect(() => filteredRows.value).not.toThrow()
    expect(error).toHaveBeenCalled()
    error.mockRestore()
  })
})
