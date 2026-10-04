import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import FilterBar from "@/components/island/FilterBar.vue"
import FilterPicker from "@/components/island/FilterPicker.vue"
import FoldOut from "@/components/island/FoldOut.vue"
import FullList from "@/components/island/FullList.vue"
import RoleMark from "@/components/island/RoleMark.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import SelectionBar from "@/components/island/SelectionBar.vue"
import SortHeader from "@/components/island/SortHeader.vue"
import StateMark from "@/components/island/StateMark.vue"

describe("SearchBox", () => {
  it("says what it searches and hands back what is typed", async () => {
    const wrapper = mount(SearchBox, {props: {label: "Search jobs", testid: "jobs-search"}})
    const input = wrapper.get("[data-testid=jobs-search]")

    expect(input.attributes("placeholder")).toBe("Search jobs")
    expect(input.attributes("aria-label")).toBe("Search jobs")
    await input.setValue("brevo")
    expect(wrapper.emitted("update:modelValue")?.[0]).toEqual(["brevo"])
  })
})

describe("FilterPicker", () => {
  const options = [{key: "FAILED", label: "Failed"}, {key: "DEAD", label: "Dead"}]

  it("offers Any first, and picking it clears the filter", async () => {
    const wrapper = mount(FilterPicker, {props: {label: "Status", options, testid: "status", modelValue: "FAILED"}})
    const picker = wrapper.findComponent(SearchPicker)

    expect(picker.props("options").map((row: {label: string}) => row.label)).toEqual(["Any", "Failed", "Dead"])
    expect(picker.props("placeholder")).toBe("Failed")
    picker.vm.$emit("pick", "__any__")
    picker.vm.$emit("pick", "DEAD")
    expect(wrapper.emitted("update:modelValue")).toEqual([[null], ["DEAD"]])
  })

  it("reads Any, or its own word for it, when nothing is chosen", () => {
    const any = mount(FilterPicker, {props: {label: "Kind", options, testid: "kind"}})
    const all = mount(FilterPicker, {props: {label: "Kind", options, testid: "kind", anyLabel: "Every kind", modelValue: "GONE"}})

    expect(any.findComponent(SearchPicker).props("placeholder")).toBe("Any")
    expect(any.findComponent(SearchPicker).props("selectedKey")).toBe("__any__")
    expect(all.findComponent(SearchPicker).props("placeholder")).toBe("Every kind")
  })
})

describe("FilterBar", () => {
  it("offers to clear only once a filter is set", async () => {
    const idle = mount(FilterBar, {props: {testid: "bar"}, slots: {default: "<span>search</span>"}})
    const busy = mount(FilterBar, {props: {active: true, testid: "bar"}})
    const plain = mount(FilterBar, {props: {active: true}})

    expect(idle.find("[data-testid=bar-clear]").exists()).toBe(false)
    expect(idle.text()).toContain("search")
    await busy.get("[data-testid=bar-clear]").trigger("click")
    expect(busy.emitted("clear")).toHaveLength(1)
    expect(plain.get("button").attributes("data-testid")).toBeUndefined()
  })
})

describe("SortHeader", () => {
  it("shows the chevron only on the column sorted by, and says which way", async () => {
    const off = mount(SortHeader, {props: {label: "Name"}})
    const up = mount(SortHeader, {props: {label: "Name", direction: "asc", testid: "sort"}})
    const down = mount(SortHeader, {props: {label: "Joined", direction: "desc"}})

    expect(off.find("svg").exists()).toBe(false)
    expect(off.attributes("aria-label")).toBe("Sort by Name")
    expect(up.attributes("aria-label")).toBe("Name, sorted ascending")
    expect(up.get("path").attributes("d")).toBe("m6 14.5 6-6 6 6")
    expect(down.attributes("aria-label")).toBe("Joined, sorted descending")
    expect(down.get("path").attributes("d")).toBe("m6 9.5 6 6 6-6")
    await up.trigger("click")
    expect(up.emitted("sort")).toHaveLength(1)
  })
})

describe("StateMark and RoleMark", () => {
  it("names each state by default, or in the caller's words", () => {
    const words = (["in-step", "missing", "extra", "unreachable", "not-created", "not-compared"] as const)
      .map((kind) => mount(StateMark, {props: {kind}}).text())

    expect(words).toEqual(["In step", "Missing", "Extra", "Unreachable", "Not created yet", "Not compared"])
    expect(mount(StateMark, {props: {kind: "extra", testid: "mark"}, slots: {default: "1 extra"}}).text()).toBe("1 extra")
  })

  it("writes a role as a mention, toned by what it grants", () => {
    expect(mount(RoleMark, {props: {role: "Admin"}}).classes()).toContain("role-mark--admin")
    expect(mount(RoleMark, {props: {role: "Board", testid: "role"}}).classes()).toContain("role-mark--board")
    const committee = mount(RoleMark, {props: {role: "Sitecie"}})
    expect(committee.text()).toBe("@Sitecie")
    expect(committee.classes()).toContain("role-mark--plain")
  })
})

describe("SelectionBar", () => {
  it("appears once rows are ticked, and clears them", async () => {
    expect(mount(SelectionBar, {props: {count: 0}}).find(".selection-bar").exists()).toBe(false)
    const bar = mount(SelectionBar, {props: {count: 3, testid: "sel"}, slots: {default: "<button>Archive</button>"}})
    const plain = mount(SelectionBar, {props: {count: 1, noun: "list"}})

    expect(bar.text()).toContain("3 selected")
    expect(plain.text()).toContain("1 list")
    expect(plain.get(".island-cut--quiet").attributes("data-testid")).toBeUndefined()
    await bar.get("[data-testid=sel-clear]").trigger("click")
    expect(bar.emitted("clear")).toHaveLength(1)
  })
})

describe("FoldOut", () => {
  it("opens in place and folds back", async () => {
    const wrapper = mount(FoldOut, {props: {label: "Run a job", testid: "fold"}, slots: {default: "<p>form</p>"}})
    const plain = mount(FoldOut, {props: {label: "Run a job", open: true}})

    expect(wrapper.text()).not.toContain("form")
    await wrapper.get("[data-testid=fold-toggle]").trigger("click")
    expect(wrapper.emitted("update:open")?.[0]).toEqual([true])
    expect(plain.get("button").attributes("aria-expanded")).toBe("true")
    expect(plain.get("button").attributes("data-testid")).toBeUndefined()
  })
})

describe("FullList", () => {
  it("draws only the rows in view, and follows the scroll", async () => {
    const rows = Array.from({length: 1000}, (_, index) => ({id: index}))
    const wrapper = mount(FullList, {
      props: {rows, rowKey: (row: {id: number}) => row.id, rowHeight: 40, height: 200, overscan: 2, testid: "list"},
      slots: {row: "<template #row=\"{row}\"><span class=\"cell\">{{ row.id }}</span></template>"},
    })

    expect(wrapper.findAll(".cell").map((cell) => cell.text())).toEqual(["0", "1", "2", "3", "4", "5", "6"])
    const list = wrapper.get("[data-testid=list]")
    Object.defineProperty(list.element, "scrollTop", {value: 4000, configurable: true})
    await list.trigger("scroll")
    expect(wrapper.findAll(".cell")[0]!.text()).toBe("98")
    expect(wrapper.get(".full-list__track").attributes("style")).toContain("40000px")
  })

  it("stops at the last row, and uses its own height and window by default", () => {
    const wrapper = mount(FullList, {props: {rows: [{id: 1}], rowKey: (row: {id: number}) => row.id}})

    expect(wrapper.findAll("[role=listitem]")).toHaveLength(1)
    expect(wrapper.attributes("style")).toContain("480px")
  })
})
