import {afterEach, describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import FilterBar from "@/components/island/FilterBar.vue"
import FilterPicker from "@/components/island/FilterPicker.vue"
import FoldOut from "@/components/island/FoldOut.vue"
import RoleMark from "@/components/island/RoleMark.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import SelectionBar from "@/components/island/SelectionBar.vue"
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
  const mounted = (props: Record<string, unknown> = {}) =>
    mount(FilterPicker, {props: {label: "Status", options, testid: "status", ...props}, attachTo: document.body})
  const open = async (wrapper: ReturnType<typeof mounted>) => {
    await wrapper.get("[data-testid=status-search]").trigger("click")
    await new Promise((resolve) => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()
  }
  const rows = () => [...document.querySelectorAll<HTMLButtonElement>("[data-testid=status-list] [role=option]")]
  const press = (label: string) => rows().find((row) => row.textContent.trim() === label)!.click()

  afterEach(() => {
    document.body.innerHTML = ""
  })

  it("is named by its label, which also leads to its box", () => {
    const wrapper = mounted()
    const label = wrapper.get("label")
    const box = wrapper.get("[data-testid=status-search]")

    expect(label.text()).toBe("Status")
    expect(box.attributes("aria-labelledby")).toBe(label.attributes("id"))
    expect(label.attributes("for")).toBe(box.attributes("id"))
  })

  it("offers Any first, and picking it clears the filter", async () => {
    const wrapper = mounted({modelValue: "FAILED"})

    expect((wrapper.get("[data-testid=status-search]").element as HTMLInputElement).value).toBe("Failed")
    await open(wrapper)
    expect(rows().map((row) => row.textContent.trim())).toEqual(["Any", "Failed", "Dead"])
    press("Any")
    await open(wrapper)
    press("Dead")
    expect(wrapper.emitted("update:modelValue")).toEqual([[null], ["DEAD"]])
  })

  it("reads Any, or its own word for it, while nothing is chosen", () => {
    const any = mounted()
    const all = mounted({anyLabel: "Every kind"})

    expect((any.get("[data-testid=status-search]").element as HTMLInputElement).value).toBe("Any")
    expect((all.get("[data-testid=status-search]").element as HTMLInputElement).value).toBe("Every kind")
  })

  it("shows its word for nothing chosen in an empty box where the value it holds is not on offer", () => {
    const box = mounted({modelValue: "GONE", anyLabel: "Every kind"}).get<HTMLInputElement>("[data-testid=status-search]")

    expect(box.element.value).toBe("")
    expect(box.attributes("placeholder")).toBe("Every kind")
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

describe("StateMark and RoleMark", () => {
  it("names each state by default, or in the caller's words", () => {
    const words = (["in-sync", "missing", "extra", "unreachable", "not-created", "not-compared"] as const)
      .map((kind) => mount(StateMark, {props: {kind}}).text())

    expect(words).toEqual(["In sync", "Missing", "Additional", "Unreachable", "Not created yet", "Not compared"])
    expect(mount(StateMark, {props: {kind: "extra", testid: "mark"}, slots: {default: "1 additional"}}).text()).toBe("1 additional")
  })

  it("writes a role as a mention, and sets the admin's and the treasurer's apart", () => {
    const tones = ["Admin", "Treasurer", "Board", "Sitecie"].map((role) => mount(RoleMark, {props: {role, testid: "role"}}).classes()[1])

    expect(tones).toEqual(["role-mark--admin", "role-mark--treasurer", "role-mark--plain", "role-mark--plain"])
    expect(mount(RoleMark, {props: {role: "Sitecie"}}).text()).toBe("@Sitecie")
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

  it("stays in view where asked, with its actions disabled until something is ticked", () => {
    const idle = mount(SelectionBar, {props: {count: 0, always: true, testid: "sel"}, slots: {default: "<button>Archive</button>"}})
    const busy = mount(SelectionBar, {props: {count: 2, always: true, testid: "sel"}, slots: {default: "<button>Archive</button>"}})

    expect(idle.text()).toContain("0 selected")
    expect(idle.get("fieldset").attributes("disabled")).toBeDefined()
    expect(idle.find("[data-testid=sel-clear]").exists()).toBe(false)
    expect(busy.get("fieldset").attributes("disabled")).toBeUndefined()
    expect(busy.text()).toContain("2 selected")
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
    await plain.get("button").trigger("click")
    expect(plain.emitted("update:open")?.[0]).toEqual([false])
  })
})
