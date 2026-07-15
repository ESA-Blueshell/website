import {describe, expect, it, vi} from "vitest"
import {ref} from "vue"
import {useMemberFilters} from "@/composables/useMemberFilters"
import {type MemberRow, type MemberStatus} from "@/composables/useMemberRows"

vi.mock("vue", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue")>()
  return {
    ...(actual as Record<string, unknown>),
    onBeforeUnmount: vi.fn(),
  }
})

function makeRow(id: number, overrides: Partial<MemberRow> = {}): MemberRow {
  return {
    id,
    fullName: `User ${id}`,
    username: `user${id}`,
    role: "",
    status: "Never" as MemberStatus,
    memberSince: null,
    latestType: null,
    latestIncasso: false,
    paid: false,
    wasMemberInPeriod: false,
    ...overrides,
  }
}

describe("useMemberFilters", () => {
  it("returns all rows when no filters active", () => {
    const rows = ref([makeRow(1), makeRow(2), makeRow(3)])
    const index = ref(new Map([[1, "user 1"], [2, "user 2"], [3, "user 3"]]))
    const {filteredRows} = useMemberFilters(rows, index)
    expect(filteredRows.value).toHaveLength(3)
  })

  it("filters by search term using the userSearchIndex haystack", () => {
    const rows = ref([makeRow(1, {fullName: "Alice Smith", username: "alice"}), makeRow(2, {fullName: "Bob Jones", username: "bob"})])
    const index = ref(new Map([[1, "alice smith alice"], [2, "bob jones bob"]]))
    const {filteredRows, search} = useMemberFilters(rows, index)

    search.value = "alice"
    expect(filteredRows.value).toHaveLength(1)
    expect(filteredRows.value[0]!.id).toBe(1)
  })

  it("multi-word search requires all terms to match", () => {
    const rows = ref([makeRow(1, {fullName: "Alice Smith"}), makeRow(2, {fullName: "Alice Jones"})])
    const index = ref(new Map([[1, "alice smith"], [2, "alice jones"]]))
    const {filteredRows, search} = useMemberFilters(rows, index)

    search.value = "alice smith"
    expect(filteredRows.value).toHaveLength(1)
    expect(filteredRows.value[0]!.id).toBe(1)
  })

  it("memberFilter=yes shows only Current members", () => {
    const rows = ref([
      makeRow(1, {status: "Current"}),
      makeRow(2, {status: "Former"}),
      makeRow(3, {status: "Never"}),
    ])
    const index = ref(new Map([[1, "u1"], [2, "u2"], [3, "u3"]]))
    const {filteredRows, memberFilter} = useMemberFilters(rows, index)

    memberFilter.value = "yes"
    expect(filteredRows.value).toHaveLength(1)
    expect(filteredRows.value[0]!.id).toBe(1)
  })

  it("memberFilter=no shows only non-Current members", () => {
    const rows = ref([
      makeRow(1, {status: "Current"}),
      makeRow(2, {status: "Former"}),
    ])
    const index = ref(new Map([[1, "u1"], [2, "u2"]]))
    const {filteredRows, memberFilter} = useMemberFilters(rows, index)

    memberFilter.value = "no"
    expect(filteredRows.value).toHaveLength(1)
    expect(filteredRows.value[0]!.id).toBe(2)
  })

  it("paidFilter=yes shows only paid users", () => {
    const rows = ref([makeRow(1, {paid: true}), makeRow(2, {paid: false})])
    const index = ref(new Map([[1, "u1"], [2, "u2"]]))
    const {filteredRows, paidFilter} = useMemberFilters(rows, index)

    paidFilter.value = "yes"
    expect(filteredRows.value).toHaveLength(1)
    expect(filteredRows.value[0]!.id).toBe(1)
  })

  it("paidFilter=no shows only unpaid users", () => {
    const rows = ref([makeRow(1, {paid: true}), makeRow(2, {paid: false})])
    const index = ref(new Map([[1, "u1"], [2, "u2"]]))
    const {filteredRows, paidFilter} = useMemberFilters(rows, index)

    paidFilter.value = "no"
    expect(filteredRows.value).toHaveLength(1)
    expect(filteredRows.value[0]!.id).toBe(2)
  })

  it("incassoFilter=yes shows only users with incasso", () => {
    const rows = ref([makeRow(1, {latestIncasso: true}), makeRow(2, {latestIncasso: false})])
    const index = ref(new Map([[1, "u1"], [2, "u2"]]))
    const {filteredRows, incassoFilter} = useMemberFilters(rows, index)

    incassoFilter.value = "yes"
    expect(filteredRows.value).toHaveLength(1)
    expect(filteredRows.value[0]!.id).toBe(1)
  })

  it("incassoFilter=no shows only users without incasso", () => {
    const rows = ref([makeRow(1, {latestIncasso: true}), makeRow(2, {latestIncasso: false})])
    const index = ref(new Map([[1, "u1"], [2, "u2"]]))
    const {filteredRows, incassoFilter} = useMemberFilters(rows, index)

    incassoFilter.value = "no"
    expect(filteredRows.value).toHaveLength(1)
    expect(filteredRows.value[0]!.id).toBe(2)
  })

  it("periodMemberFilter=yes shows only members in the selected period", () => {
    const rows = ref([makeRow(1, {wasMemberInPeriod: true}), makeRow(2, {wasMemberInPeriod: false})])
    const index = ref(new Map([[1, "u1"], [2, "u2"]]))
    const {filteredRows, periodMemberFilter} = useMemberFilters(rows, index)

    periodMemberFilter.value = "yes"
    expect(filteredRows.value).toHaveLength(1)
    expect(filteredRows.value[0]!.id).toBe(1)
  })

  it("periodMemberFilter=no shows only users outside the selected period", () => {
    const rows = ref([makeRow(1, {wasMemberInPeriod: true}), makeRow(2, {wasMemberInPeriod: false})])
    const index = ref(new Map([[1, "u1"], [2, "u2"]]))
    const {filteredRows, periodMemberFilter} = useMemberFilters(rows, index)

    periodMemberFilter.value = "no"
    expect(filteredRows.value).toHaveLength(1)
    expect(filteredRows.value[0]!.id).toBe(2)
  })

  it("sorts by name ascending by default", () => {
    const rows = ref([makeRow(1, {fullName: "Zoe Last"}), makeRow(2, {fullName: "Anna First"})])
    const index = ref(new Map([[1, "zoe last"], [2, "anna first"]]))
    const {filteredRows} = useMemberFilters(rows, index)

    expect(filteredRows.value[0]!.id).toBe(2)
    expect(filteredRows.value[1]!.id).toBe(1)
  })

  it("toggleSort changes key and resets to ascending", () => {
    const rows = ref([makeRow(1), makeRow(2)])
    const index = ref(new Map([[1, "u1"], [2, "u2"]]))
    const {sortKey, sortAsc, toggleSort} = useMemberFilters(rows, index)

    expect(sortKey.value).toBe("name")
    toggleSort("status")
    expect(sortKey.value).toBe("status")
    expect(sortAsc.value).toBe(true)
  })

  it("toggleSort on same key flips sortAsc", () => {
    const rows = ref([makeRow(1)])
    const index = ref(new Map([[1, "u1"]]))
    const {sortAsc, toggleSort} = useMemberFilters(rows, index)

    expect(sortAsc.value).toBe(true)
    toggleSort("name")
    expect(sortAsc.value).toBe(false)
    toggleSort("name")
    expect(sortAsc.value).toBe(true)
  })

  it("sortIcon returns correct icons", () => {
    const rows = ref([makeRow(1)])
    const index = ref(new Map([[1, "u1"]]))
    const {sortAsc, sortKey, sortIcon} = useMemberFilters(rows, index)

    // non-active key
    expect(sortIcon("status")).toBe("mdi-unfold-more-horizontal")

    // active key ascending
    sortKey.value = "name"
    sortAsc.value = true
    expect(sortIcon("name")).toBe("mdi-arrow-up")

    // active key descending
    sortAsc.value = false
    expect(sortIcon("name")).toBe("mdi-arrow-down")
  })

  it("sorts by status in correct order: Current < Former < Never", () => {
    const rows = ref([
      makeRow(3, {status: "Never"}),
      makeRow(2, {status: "Former"}),
      makeRow(1, {status: "Current"}),
    ])
    const index = ref(new Map([[1, "u1"], [2, "u2"], [3, "u3"]]))
    const {filteredRows, sortKey, sortAsc} = useMemberFilters(rows, index)

    sortKey.value = "status"
    sortAsc.value = true

    expect(filteredRows.value[0]!.status).toBe("Current")
    expect(filteredRows.value[1]!.status).toBe("Former")
    expect(filteredRows.value[2]!.status).toBe("Never")
  })

  it("sorts by memberSince ascending", () => {
    const rows = ref([
      makeRow(1, {memberSince: "2024-06-01"}),
      makeRow(2, {memberSince: "2020-01-01"}),
    ])
    const index = ref(new Map([[1, "u1"], [2, "u2"]]))
    const {filteredRows, sortKey, sortAsc} = useMemberFilters(rows, index)

    sortKey.value = "memberSince"
    sortAsc.value = true

    expect(filteredRows.value[0]!.id).toBe(2)
    expect(filteredRows.value[1]!.id).toBe(1)
  })

  it("sorts by username, role, paid, and selected-period membership", () => {
    const rows = ref([
      makeRow(1, {username: "zoe", role: "member", paid: true, wasMemberInPeriod: true}),
      makeRow(2, {username: "anna", role: "admin", paid: false, wasMemberInPeriod: false}),
    ])
    const index = ref(new Map([[1, "zoe"], [2, "anna"]]))
    const {filteredRows, sortKey, sortAsc} = useMemberFilters(rows, index)

    sortAsc.value = true
    sortKey.value = "username"
    expect(filteredRows.value.map((row) => row.id)).toEqual([2, 1])

    sortKey.value = "role"
    expect(filteredRows.value.map((row) => row.id)).toEqual([2, 1])

    sortKey.value = "paid"
    expect(filteredRows.value.map((row) => row.id)).toEqual([2, 1])

    sortKey.value = "wasMemberInPeriod"
    expect(filteredRows.value.map((row) => row.id)).toEqual([2, 1])
  })

  it("combined search + filter narrows results", () => {
    const rows = ref([
      makeRow(1, {fullName: "Alice Current", status: "Current"}),
      makeRow(2, {fullName: "Bob Current", status: "Current"}),
      makeRow(3, {fullName: "Charlie Never", status: "Never"}),
    ])
    const index = ref(new Map([[1, "alice current"], [2, "bob current"], [3, "charlie never"]]))
    const {filteredRows, search, memberFilter} = useMemberFilters(rows, index)

    search.value = "alice"
    memberFilter.value = "yes"

    expect(filteredRows.value).toHaveLength(1)
    expect(filteredRows.value[0]!.id).toBe(1)
  })
})
