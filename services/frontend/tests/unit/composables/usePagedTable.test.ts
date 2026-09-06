/**
 * The paging, expansion and search both manager pages used to hand-roll.
 *
 * Driven through a host component rather than called bare, because it registers an unmount hook:
 * a debounce left running past teardown is exactly the fault this file has to be able to see.
 */
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"
import {mount, type VueWrapper} from "@vue/test-utils"
import {usePagedTable, type PageOf, type PageQuery, type PagedTable} from "@/composables/usePagedTable"
import {settle, unmountAll} from "../helpers/testUtils"

interface Row {
  id?: number | null
}

const rowsNumbered = (from: number, count: number): Row[] =>
  Array.from({length: count}, (_, index) => ({id: from + index}))

describe("usePagedTable", () => {
  const wrappers: VueWrapper[] = []

  const host = (
    load: (query: PageQuery) => Promise<PageOf<Row>>,
    options?: Parameters<typeof usePagedTable>[1],
  ): PagedTable<Row> => {
    let table!: PagedTable<Row>
    const wrapper = mount(defineComponent({
      setup() {
        table = usePagedTable<Row>(load, options)
        return () => h("div")
      },
    }))
    wrappers.push(wrapper)
    return table
  }

  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    unmountAll(wrappers, "usePagedTable")
  })

  it("asks for the page the reader is on, counted from zero", async () => {
    const load = vi.fn().mockResolvedValue({rows: rowsNumbered(1, 10), totalElements: 60, totalPages: 3})
    const table = host(load, {pageSize: 10})

    await table.refresh()

    expect(load).toHaveBeenCalledWith({page: 0, size: 10, search: undefined})

    table.page.value = 3
    await settle()

    expect(load).toHaveBeenLastCalledWith({page: 2, size: 10, search: undefined})
  })

  it("names the range on screen out of the rows it actually got", async () => {
    const load = vi.fn()
      .mockResolvedValueOnce({rows: rowsNumbered(1, 50), totalElements: 51, totalPages: 2})
      .mockResolvedValueOnce({rows: rowsNumbered(51, 1), totalElements: 51, totalPages: 2})
    const table = host(load)

    await table.refresh()
    expect(table.pageRangeLabel.value).toBe("1-50 of 51")

    table.page.value = 2
    await settle()
    expect(table.pageRangeLabel.value).toBe("51-51 of 51")
  })

  it("says nothing is on screen when nothing came back", async () => {
    const table = host(vi.fn().mockResolvedValue({rows: [], totalElements: 0, totalPages: 1}))

    await table.refresh()

    expect(table.pageRangeLabel.value).toBe("0 of 0")
  })

  it("falls back to the last page when the one it is on no longer exists", async () => {
    const load = vi.fn()
      .mockResolvedValueOnce({rows: [], totalElements: 10, totalPages: 1})
      .mockResolvedValueOnce({rows: rowsNumbered(1, 10), totalElements: 10, totalPages: 1})
    const table = host(load, {pageSize: 10})

    table.page.value = 4
    await settle()

    expect(table.page.value).toBe(1)
    // The rows from the out-of-range answer are never shown: the clamp re-reads instead.
    expect(table.rows.value).toHaveLength(10)
    expect(load).toHaveBeenCalledTimes(2)
  })

  it("waits for typing to stop before asking again", async () => {
    const load = vi.fn().mockResolvedValue({rows: [], totalElements: 0, totalPages: 1})
    const table = host(load, {searchDebounceMs: 250})
    await settle()
    load.mockClear()

    table.search.value = "j"
    await settle()
    table.search.value = "jo"
    await settle()
    table.search.value = "jos"
    await settle()

    expect(load).not.toHaveBeenCalled()

    vi.advanceTimersByTime(250)
    await settle()

    expect(load).toHaveBeenCalledTimes(1)
    expect(load).toHaveBeenCalledWith({page: 0, size: 50, search: "jos"})
  })

  it("treats a cleared or blank search as no search at all", async () => {
    const load = vi.fn().mockResolvedValue({rows: [], totalElements: 0, totalPages: 1})
    const table = host(load)

    // A clearable v-text-field writes null, which used to reach `.trim()` as a crash.
    table.search.value = null
    await settle()
    vi.advanceTimersByTime(250)
    await settle()
    expect(load).toHaveBeenLastCalledWith({page: 0, size: 50, search: undefined})

    table.search.value = "   "
    await settle()
    vi.advanceTimersByTime(250)
    await settle()
    expect(load).toHaveBeenLastCalledWith({page: 0, size: 50, search: undefined})
  })

  it("does not fire a debounced search after the page is gone", async () => {
    const load = vi.fn().mockResolvedValue({rows: [], totalElements: 0, totalPages: 1})
    const table = host(load)
    await settle()
    load.mockClear()

    table.search.value = "typed"
    await settle()
    unmountAll(wrappers, "usePagedTable")
    vi.advanceTimersByTime(1000)
    await settle()

    expect(load).not.toHaveBeenCalled()
  })

  it("opens and closes a row, and forgets what was open on the next page", async () => {
    const load = vi.fn().mockResolvedValue({rows: rowsNumbered(1, 2), totalElements: 4, totalPages: 2})
    const table = host(load, {pageSize: 2})
    await table.refresh()

    const row = {id: 1}
    expect(table.isExpanded(row)).toBe(false)
    table.toggleExpanded(row)
    expect(table.isExpanded(row)).toBe(true)
    table.toggleExpanded(row)
    expect(table.isExpanded(row)).toBe(false)

    table.toggleExpanded(row)
    table.page.value = 2
    await settle()
    expect(table.isExpanded(row)).toBe(false)
  })

  it("never opens a row with no id to open it by", async () => {
    const table = host(vi.fn().mockResolvedValue({rows: [], totalElements: 0, totalPages: 1}))

    table.toggleExpanded({id: null})

    expect(table.isExpanded({id: null})).toBe(false)
  })

  it("goes back to the first page on a filter change, re-reading either way", async () => {
    const load = vi.fn().mockResolvedValue({rows: rowsNumbered(1, 2), totalElements: 4, totalPages: 2})
    const table = host(load, {pageSize: 2})
    table.page.value = 2
    await settle()
    load.mockClear()

    table.resetToFirstPage()
    await settle()
    expect(table.page.value).toBe(1)
    expect(load).toHaveBeenCalledTimes(1)

    // Already on the first page, so nothing moves and the re-read has to be asked for directly.
    load.mockClear()
    table.resetToFirstPage()
    await settle()
    expect(load).toHaveBeenCalledTimes(1)
  })

  it("stops loading even when the read throws", async () => {
    const table = host(vi.fn().mockRejectedValue(new Error("network")))

    await expect(table.refresh()).rejects.toThrow("network")

    expect(table.loading.value).toBe(false)
  })
})
