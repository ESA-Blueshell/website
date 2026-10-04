import {afterEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {h} from "vue"
import GoArrow from "@/components/management/GoArrow.vue"
import ManagementHead from "@/components/management/ManagementHead.vue"
import ManagementPanel from "@/components/management/ManagementPanel.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable from "@/components/management/ManagementTable.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import PairList from "@/components/management/PairList.vue"
import RowCheck from "@/components/management/RowCheck.vue"

const stubs = {RouterLink: {props: ["to"], template: '<a :to="to"><slot /></a>'}}

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

    expect(wrapper.get("[data-testid=table]").classes()).toContain("mg-table--boxed")
    expect(wrapper.get("[data-testid=table]").attributes("style")).toContain("max-height: 300px")
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
    expect(acting.get(".mg-table").attributes("style")).toBeUndefined()
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
})
