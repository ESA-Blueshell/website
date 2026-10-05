import {afterEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {h, nextTick} from "vue"
import GoArrow from "@/components/management/GoArrow.vue"
import ManagementHead from "@/components/management/ManagementHead.vue"
import ManagementPanel from "@/components/management/ManagementPanel.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable from "@/components/management/ManagementTable.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import PairList from "@/components/management/PairList.vue"
import PersonLink from "@/components/management/PersonLink.vue"
import RowCheck from "@/components/management/RowCheck.vue"

const {push} = vi.hoisted(() => ({push: vi.fn()}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRouter: () => ({push}),
}))

const stubs = {RouterLink: {props: ["to"], template: '<a :to="to"><slot /></a>'}}

describe("PersonLink", () => {
  it("leads to the person's page, and only names somebody who has no account", () => {
    const known = mount(PersonLink, {props: {userId: 9, name: "Bo Board", testid: "who"}, global: {stubs}})
    const unknown = mount(PersonLink, {props: {userId: null, name: "c@example.com", testid: "who"}, global: {stubs}})

    expect(known.get("[data-testid=who]").attributes("to")).toBe("/management/users/9")
    expect(known.text()).toBe("Bo Board")
    expect(unknown.get("[data-testid=who]").element.tagName).toBe("SPAN")
    expect(unknown.text()).toBe("c@example.com")
  })
})

describe("ManagementHead", () => {
  it("names the group and the page, and says what the page is for and what it can do", () => {
    const full = mount(ManagementHead, {
      props: {eyebrow: "Members", title: "Users", testid: "head"},
      slots: {default: "Everyone with an account.", actions: "<button>Add a user</button>"},
    })
    const bare = mount(ManagementHead, {props: {eyebrow: "Mail", title: "Sent"}})

    expect(full.get("[data-testid=head]").text()).toContain("Members")
    expect(full.get("h1").text()).toBe("Users")
    expect(full.get(".mg-head__body").text()).toBe("Everyone with an account.")
    expect(full.get(".mg-head__actions").text()).toBe("Add a user")
    expect(bare.find(".mg-head__body").exists()).toBe(false)
    expect(bare.find(".mg-head__actions").exists()).toBe(false)
  })
})

describe("MiniButton", () => {
  it("is a button for what the page does, and a link for what has a page", async () => {
    const act = mount(MiniButton, {props: {testid: "act", tone: "danger"}, slots: {default: "Delete"}})
    const link = mount(MiniButton, {props: {to: "/management/jobs/4"}, slots: {default: "Open"}, global: {stubs}})

    expect(act.element.tagName).toBe("BUTTON")
    expect(act.classes()).toContain("mini--danger")
    await act.get("[data-testid=act]").trigger("click")
    expect(act.emitted("click")).toHaveLength(1)
    expect(link.attributes("to")).toBe("/management/jobs/4")
    expect(link.classes()).not.toContain("mini--danger")
  })
})

describe("RowCheck", () => {
  it("says which row it selects and tells the page when it is pressed", async () => {
    const wrapper = mount(RowCheck, {props: {checked: true, label: "Select Sitecie", testid: "check"}})
    const box = wrapper.get("[data-testid=check]")

    expect(box.attributes("aria-label")).toBe("Select Sitecie")
    expect((box.element as HTMLInputElement).checked).toBe(true)
    await box.trigger("change")
    expect(wrapper.emitted("toggle")).toHaveLength(1)
  })
})

describe("RowCheck at the head of a list", () => {
  it("draws a dash while only some of what it stands for is selected", async () => {
    const wrapper = mount(RowCheck, {props: {checked: false, indeterminate: true, label: "Select the 3 shown"}})
    const box = wrapper.get("input").element as HTMLInputElement

    expect(box.indeterminate).toBe(true)
    await wrapper.setProps({indeterminate: false, checked: true})
    expect(box.indeterminate).toBe(false)
  })
})

describe("ManagementRow", () => {
  it("opens its record by its name and by its arrow", () => {
    const wrapper = mount(ManagementRow, {
      props: {name: "Sitecie", to: "/management/committees/4", meta: "9 seats", testid: "row"},
      slots: {default: "In step", check: "<input type=checkbox>"},
      global: {stubs},
    })

    expect(wrapper.classes()).toContain("mg-row--check")
    expect(wrapper.get("[data-testid=row-open]").attributes("to")).toBe("/management/committees/4")
    expect(wrapper.get(".mg-row__meta").text()).toBe("9 seats")
    expect(wrapper.get(".mg-row__state").text()).toBe("In step")
    expect(wrapper.get(".mg-row__go").attributes("aria-label")).toBe("Open Sitecie")
    expect(wrapper.findComponent(GoArrow).exists()).toBe(true)
  })

  it("stands as plain words where it opens nothing, with its own acts at the end", () => {
    const wrapper = mount(ManagementRow, {props: {name: "Kandi"}, slots: {acts: "<button>Create</button>"}, global: {stubs}})

    expect(wrapper.get(".mg-row__name").element.tagName).toBe("P")
    expect(wrapper.find(".mg-row__meta").exists()).toBe(false)
    expect(wrapper.find(".mg-row__state").exists()).toBe(false)
    expect(wrapper.get(".mg-row__acts").text()).toBe("Create")
    expect(mount(ManagementRow, {props: {name: "Kandi"}, global: {stubs}}).find(".mg-row__acts").exists()).toBe(false)
  })
})

describe("ManagementPanel", () => {
  it("names an area and links to its page", () => {
    const linked = mount(ManagementPanel, {props: {title: "Events", to: "/management/events", link: "Events to approve", testid: "panel"}, slots: {default: "<p>2 awaiting</p>"}, global: {stubs}})
    const plain = mount(ManagementPanel, {props: {title: "Mail"}, global: {stubs}})

    expect(linked.get("h2").text()).toBe("Events")
    expect(linked.get("[data-testid=panel-link]").attributes("to")).toBe("/management/events")
    expect(linked.text()).toContain("2 awaiting")
    expect(plain.find("a").exists()).toBe(false)
  })
})

describe("PairList", () => {
  it("reads each fact against how it stands, links what has a page and marks what only an admin reads", () => {
    const wrapper = mount(PairList, {
      props: {
        testid: "pairs",
        pairs: [
          {label: "Brevo", value: "3 lists drifting", to: "/management/platforms/brevo"},
          {label: "Jobs", value: "1 dead", adminOnly: true, testid: "jobs"},
        ],
      },
      global: {stubs},
    })

    expect(wrapper.get("[data-testid=pairs] a").attributes("to")).toBe("/management/platforms/brevo")
    expect(wrapper.get("[data-testid=jobs]").text()).toContain("@Admin")
    expect(wrapper.get("[data-testid=jobs]").text()).toContain("1 dead")
    expect(wrapper.findAll("li")).toHaveLength(2)
  })
})

describe("ManagementTable", () => {
  interface Row {id: number; name: string}
  const rows: Row[] = [{id: 1, name: "Sitecie"}, {id: 2, name: "Kandi"}]
  const columns = [{key: "name", label: "Name", sortable: true, testid: "head-name", wrap: true}, {key: "seats", label: "Seats"}]
  const cells = {
    name: ({row}: {row: Row}) => h("span", row.name),
    seats: () => h("span", "9"),
  }

  afterEach(() => vi.unstubAllGlobals())

  it("draws a row per record, sorts by a head and ends each row with the arrow to its page", async () => {
    const wrapper = mount(ManagementTable<Row>, {
      props: {
        columns, rows, rowKey: (row: Row) => row.id, sortKey: "name", descending: true, height: 300, testid: "table",
        to: (row: Row) => `/management/committees/${row.id}`, rowTestid: (row: Row) => `row-${row.id}`,
      },
      slots: {...cells, check: ({row}: {row: Row}) => h("input", {type: "checkbox", "data-id": row.id})},
      global: {stubs},
    })

    expect(wrapper.get("[data-testid=table] .mg-table__scroll").attributes("style")).toContain("max-height: 300px")
    expect(wrapper.find(".mg-table__bar").exists()).toBe(false)
    expect(wrapper.findAll("tbody tr")).toHaveLength(2)
    expect(wrapper.get("[data-testid=row-2]").text()).toContain("Kandi")
    expect(wrapper.get("th[aria-sort]").attributes("aria-sort")).toBe("descending")
    expect(wrapper.get("[data-testid=head-name]").attributes("aria-label")).toBe("Name, sorted descending")
    expect(wrapper.get("[data-testid=row-1] .mg-table__go a").attributes("to")).toBe("/management/committees/1")
    expect(wrapper.findAll("td.mg-table__check")).toHaveLength(2)
    await wrapper.get("[data-testid=head-name]").trigger("click")
    expect(wrapper.emitted("sort")).toEqual([["name"]])
  })

  it("ends each row with its own acts instead, and says how an unsorted or ascending head sorts", async () => {
    const acting = mount(ManagementTable<Row>, {
      props: {columns, rows, rowKey: (row: Row) => row.id, sortKey: "name"},
      slots: {...cells, acts: () => h("button", "Approve")},
      global: {stubs},
    })
    const unsorted = mount(ManagementTable<Row>, {props: {columns, rows, rowKey: (row: Row) => row.id}, slots: cells, global: {stubs}})

    expect(acting.get("th[aria-sort]").attributes("aria-sort")).toBe("ascending")
    expect(acting.findAll("td.mg-table__acts")).toHaveLength(2)
    expect(acting.find("td.mg-table__go").exists()).toBe(false)
    expect(acting.get(".mg-table__scroll").attributes("style")).toBeUndefined()
    expect(unsorted.get(".mg-table__sort").attributes("aria-label")).toBe("Sort by Name")
    expect(unsorted.find("th[aria-sort]").exists()).toBe(false)
    expect(unsorted.findAll("thead th")).toHaveLength(2)
  })

  it("hands each row to the page on a phone, and keeps the table where the page draws no row", () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const asRows = mount(ManagementTable<Row>, {
      props: {columns, rows, rowKey: (row: Row) => row.id, testid: "table"},
      slots: {...cells, phone: ({row}: {row: Row}) => h("p", {class: "as-row"}, row.name)},
      global: {stubs},
    })
    const asTable = mount(ManagementTable<Row>, {props: {columns, rows, rowKey: (row: Row) => row.id}, slots: cells, global: {stubs}})

    expect(asRows.get("[data-testid=table]").classes()).toContain("mg-rows")
    expect(asRows.findAll(".as-row").map((one) => one.text())).toEqual(["Sitecie", "Kandi"])
    expect(asTable.find("table").exists()).toBe(true)
  })

  it("ticks everything shown from its head, then offers the rest and to let go of it all", async () => {
    const check = {check: () => h("input", {type: "checkbox"})}
    const props = {columns, rows, rowKey: (row: Row) => row.id, testid: "table", total: 5}
    const some = mount(ManagementTable<Row>, {props: {...props, headerState: "indeterminate", selectedCount: 1}, slots: {...cells, ...check}, global: {stubs}})

    expect(some.find(".mg-table__all").exists()).toBe(false)
    await some.get("[data-testid=table-select-shown]").trigger("change")
    expect(some.emitted("toggleShown")).toHaveLength(1)

    await some.setProps({headerState: "checked", selectedCount: 2})
    expect(some.get(".mg-table__all").text()).toContain("All 2 shown are selected.")
    expect(some.get(".mg-table__all th").attributes("colspan")).toBe("3")
    await some.get("[data-testid=table-select-all]").trigger("click")
    expect(some.emitted("selectAll")).toHaveLength(1)

    await some.setProps({selectedCount: 5})
    expect(some.get(".mg-table__all").text()).toContain("All 5 are selected.")
    await some.get("[data-testid=table-select-all]").trigger("click")
    expect(some.emitted("clearSelection")).toHaveLength(1)

    await some.setProps({total: 2, selectedCount: 2})
    expect(some.find(".mg-table__all").exists()).toBe(false)
  })

  it("gives a phone's rows the same head: the tick, what it took and the rest on offer", async () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const wrapper = mount(ManagementTable<Row>, {
      props: {columns, rows, rowKey: (row: Row) => row.id, testid: "table", total: 5, headerState: "unchecked", selectedCount: 0, to: (row: Row) => `/x/${row.id}`},
      slots: {...cells, phone: ({row}: {row: Row}) => h("p", row.name)},
      global: {stubs},
    })

    expect(wrapper.get(".mg-rows__head").text()).toContain("Select the 2 shown")
    await wrapper.get("[data-testid=table-select-shown]").trigger("change")
    expect(wrapper.emitted("toggleShown")).toHaveLength(1)
    await wrapper.setProps({headerState: "checked", selectedCount: 2})
    expect(wrapper.get(".mg-rows__head").text()).toContain("All 2 shown are selected.")
    await wrapper.get("[data-testid=table-select-all]").trigger("click")
    expect(wrapper.emitted("selectAll")).toHaveLength(1)
    await wrapper.setProps({selectedCount: 5})
    await wrapper.get("[data-testid=table-select-all]").trigger("click")
    expect(wrapper.emitted("clearSelection")).toHaveLength(1)
  })

  it("carries how many, the filters and the search on its own top bar, and says so when nothing is left", () => {
    const bar = {count: () => h("b", "0"), filters: () => h("select"), search: () => h("input", {type: "search"}), empty: () => "Nobody matches."}
    const wide = mount(ManagementTable<Row>, {props: {columns, rows: [], rowKey: (row: Row) => row.id}, slots: {...cells, ...bar, check: () => h("input")}, global: {stubs}})

    expect(wide.get(".mg-table__bar .mg-table__count").text()).toBe("0")
    expect(wide.get(".mg-table__bar .mg-table__search input").attributes("type")).toBe("search")
    expect(wide.get("td.mg-table__empty").text()).toBe("Nobody matches.")
    expect(wide.get("td.mg-table__empty").attributes("colspan")).toBe("3")

    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const phone = mount(ManagementTable<Row>, {props: {columns, rows: [], rowKey: (row: Row) => row.id}, slots: {...cells, ...bar, phone: () => h("p")}, global: {stubs}})

    expect(phone.classes()).toContain("mg-rows")
    expect(phone.get(".mg-table__bar .mg-table__count").text()).toBe("0")
    expect(phone.get("p.mg-table__empty").text()).toBe("Nobody matches.")
  })

  it("opens a row's page from anywhere on the row, but leaves a button or a link on it its own press", async () => {
    push.mockClear()
    const wrapper = mount(ManagementTable<Row>, {
      props: {columns, rows, rowKey: (row: Row) => row.id, to: (row: Row) => (row.id === 1 ? "/management/committees/1" : null), rowTestid: (row: Row) => `row-${row.id}`},
      slots: {...cells, acts: () => h("button", {class: "act"}, "Approve")},
      global: {stubs},
    })

    expect(wrapper.get("[data-testid=row-1]").classes()).toContain("mg-table__row--opens")
    expect(wrapper.get("[data-testid=row-2]").classes()).not.toContain("mg-table__row--opens")
    await wrapper.get("[data-testid=row-1] .act").trigger("click")
    await wrapper.get("[data-testid=row-2] td").trigger("click")
    expect(push).not.toHaveBeenCalled()
    await wrapper.get("[data-testid=row-1] td").trigger("click")
    expect(push).toHaveBeenCalledWith("/management/committees/1")
  })

  it("draws only the rows in its window once a list is long, and asks for more near the end", async () => {
    const many = Array.from({length: 300}, (_, index) => ({id: index + 1, name: `Row ${index + 1}`}))
    const wrapper = mount(ManagementTable<Row>, {props: {columns, rows: many, rowKey: (row: Row) => row.id, rowTestid: (row: Row) => `row-${row.id}`}, slots: cells, global: {stubs}, attachTo: document.body})

    const drawn = () => wrapper.findAll("tr[data-row]").length
    expect(drawn()).toBeLessThan(60)
    expect(wrapper.find("[data-testid=row-1]").exists()).toBe(true)
    expect(wrapper.find("[data-testid=row-300]").exists()).toBe(false)
    expect(wrapper.findAll("tr.mg-table__gap")).toHaveLength(1)

    const box = wrapper.get(".mg-table__scroll").element as HTMLElement
    Object.defineProperties(box, {scrollTop: {value: 7000, configurable: true}, clientHeight: {value: 600, configurable: true}, scrollHeight: {value: 15000, configurable: true}})
    await wrapper.get(".mg-table__scroll").trigger("scroll")
    expect(wrapper.find("[data-testid=row-1]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=row-145]").exists()).toBe(true)
    expect(wrapper.findAll("tr.mg-table__gap")).toHaveLength(2)
    expect(wrapper.emitted("more")).toBeUndefined()

    Object.defineProperty(box, "scrollTop", {value: 14300, configurable: true})
    await wrapper.get(".mg-table__scroll").trigger("scroll")
    expect(wrapper.emitted("more")).toHaveLength(1)
    expect(wrapper.find("[data-testid=row-300]").exists()).toBe(true)
    wrapper.unmount()
  })

  it("holds each column's share of the width once measured, so a long list cannot shift as it scrolls", async () => {
    const many = Array.from({length: 300}, (_, index) => ({id: index + 1, name: `Row ${index + 1}`}))
    const measured = vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
      return {width: this.tagName === "TH" ? 150 : 0} as DOMRect
    })
    const wrapper = mount(ManagementTable<Row>, {props: {columns, rows: many, rowKey: (row: Row) => row.id}, slots: cells, global: {stubs}, attachTo: document.body})
    const shares = () => wrapper.findAll("col").map((one) => one.attributes("style"))
    const heads = wrapper.findAll("thead tr:first-child th").length
    await nextTick()

    expect(wrapper.get("table").classes()).toContain("mg-table__locked")
    expect(shares()).toEqual(Array.from({length: heads}, () => `width: ${100 / heads}%;`))

    await wrapper.setProps({columns: columns.slice(0, 1)})
    await nextTick()
    await nextTick()
    expect(shares()).toHaveLength(wrapper.findAll("thead tr:first-child th").length)

    await wrapper.setProps({rows: many.slice(0, 5)})
    await nextTick()
    expect(wrapper.find("colgroup").exists()).toBe(false)
    expect(wrapper.get("table").classes()).not.toContain("mg-table__locked")
    measured.mockRestore()
    wrapper.unmount()
  })

  it("hands a scroll to the page only when it starts with the table already at that end", () => {
    const scrollBy = vi.fn()
    vi.stubGlobal("scrollBy", scrollBy)
    const wrapper = mount(ManagementTable<Row>, {props: {columns, rows: [{id: 1, name: "Row 1"}], rowKey: (row: Row) => row.id}, slots: cells, global: {stubs}, attachTo: document.body})
    const box = wrapper.get(".mg-table__scroll").element
    const at = (scrollTop: number) => Object.defineProperties(box, {scrollTop: {value: scrollTop, configurable: true}, clientHeight: {value: 600, configurable: true}, scrollHeight: {value: 1500, configurable: true}})
    const wheel = (when: number, deltaY: number, deltaMode = 0) => {
      const event = new WheelEvent("wheel", {deltaY, deltaMode})
      Object.defineProperty(event, "timeStamp", {value: when})
      box.dispatchEvent(event)
    }

    // Started in the rows: it stays the rows' scroll, even once they reach their end.
    at(400)
    wheel(1000, 120)
    at(900)
    wheel(1016, 120)
    wheel(1032, 120)
    expect(scrollBy).not.toHaveBeenCalled()

    // A new scroll, with the rows already at their end: the page takes it, drift and all.
    wheel(2000, 120)
    wheel(2016, 60)
    expect(scrollBy).toHaveBeenCalledTimes(2)
    expect(scrollBy).toHaveBeenLastCalledWith(0, 60)

    // The other way from the end goes back into the rows.
    wheel(3000, -120)
    expect(scrollBy).toHaveBeenCalledTimes(2)

    at(0)
    wheel(4000, -3, 1)
    expect(scrollBy).toHaveBeenLastCalledWith(0, -96)
    wheel(5000, -5, 9)
    expect(scrollBy).toHaveBeenLastCalledWith(0, -5)
    wrapper.unmount()
    vi.unstubAllGlobals()
  })

  it("searches its own rows where the page brings no search, and says how many it shows", async () => {
    const people = [{id: 1, name: "Ada"}, {id: 2, name: "Bo"}, {id: 3, name: "Adam"}]
    const props = {columns, rows: people, rowKey: (row: Row) => row.id, rowTestid: (row: Row) => `row-${row.id}`, searchText: (row: Row) => row.name, searchLabel: "Search people", testid: "people"}
    const wrapper = mount(ManagementTable<Row>, {props, slots: {...cells, empty: "Nobody yet."}, global: {stubs}})
    const shown = () => wrapper.findAll("tr[data-row]").map((row) => row.attributes("data-testid"))

    expect(wrapper.get(".mg-table__count").text()).toBe("Showing 3")
    await wrapper.get('[data-testid="people-search"]').setValue(" AD ")
    expect(shown()).toEqual(["row-1", "row-3"])
    expect(wrapper.get(".mg-table__count").text()).toBe("Showing 2 of 3")

    await wrapper.get('[data-testid="people-search"]').setValue("zz")
    expect(shown()).toEqual([])
    expect(wrapper.get(".mg-table__empty").text()).toBe("Nothing matches the search.")

    await wrapper.setProps({rows: []})
    expect(wrapper.get(".mg-table__empty").text()).toBe("Nobody yet.")

    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const phone = mount(ManagementTable<Row>, {props, slots: {...cells, empty: "Nobody yet.", phone: ({row}: {row: Row}) => h("p", {class: "person"}, row.name)}, global: {stubs}})
    await phone.get('[data-testid="people-search"]').setValue("bo")
    expect(phone.findAll(".person").map((one) => one.text())).toEqual(["Bo"])
    await phone.get('[data-testid="people-search"]').setValue("zz")
    expect(phone.get(".mg-table__empty").text()).toBe("Nothing matches the search.")
    await phone.setProps({rows: []})
    expect(phone.get(".mg-table__empty").text()).toBe("Nobody yet.")
    vi.unstubAllGlobals()
  })
})
