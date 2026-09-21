import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import UserSelect from "@/components/form/fields/UserSelect.vue"

const {mockSearch} = vi.hoisted(() => ({mockSearch: vi.fn()}))
vi.mock("@/domains/user", () => ({searchMemberAccounts: mockSearch}))

const ada = {id: 1, fullName: "Ada", email: "ada@example.com", roles: ["MEMBER"]}
const bea = {id: 2, fullName: "Bea", email: "bea@example.com", discord: "bea#1", roles: ["MEMBER"]}

const throughField = {template: "<div><slot /></div>"}
const select = (props: Record<string, unknown> = {}) =>
  mount(UserSelect, {
    props: {users: [ada, bea], ...props},
    global: {stubs: {FormField: throughField}},
  })

const picker = (wrapper: ReturnType<typeof select>) => wrapper.findComponent({name: "SearchPicker"})

describe("UserSelect", () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mockSearch.mockReset()
    mockSearch.mockResolvedValue([])
  })

  it("says a member's handle beside their name, and says only the name without one", () => {
    const rows = picker(select()).props("options") as Array<{label: string}>

    expect(rows.map(one => one.label)).toEqual(["Ada", "Bea (bea#1)"])
  })

  it("drops what was typed when the box is emptied, rather than asking for nothing", async () => {
    const wrapper = select()

    picker(wrapper).vm.$emit("search", "")
    await vi.advanceTimersByTimeAsync(400)

    expect(mockSearch).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it("keeps the newest answer when an older one lands late", async () => {
    let releaseFirst: (rows: unknown[]) => void = () => undefined
    mockSearch
      .mockImplementationOnce(() => new Promise(resolve => {
        releaseFirst = resolve as (rows: unknown[]) => void
      }))
      .mockResolvedValueOnce([bea])
    const wrapper = select({users: []})

    picker(wrapper).vm.$emit("search", "a")
    await vi.advanceTimersByTimeAsync(300)
    picker(wrapper).vm.$emit("search", "b")
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    releaseFirst([ada])
    await flushPromises()

    expect((picker(wrapper).props("options") as Array<{key: string}>).map(one => one.key))
      .toEqual(["2"])
    wrapper.unmount()
  })

  it("stops waiting on a search it started when it is taken down", async () => {
    const wrapper = select()

    picker(wrapper).vm.$emit("search", "ada")
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(400)

    expect(mockSearch).not.toHaveBeenCalled()
  })

  it("resolves a member the page has not loaded yet once the list arrives", async () => {
    const wrapper = select({users: [], modelValue: 2})
    expect(picker(wrapper).props("selectedKey")).toBeNull()

    await wrapper.setProps({users: [ada, bea]})

    expect(picker(wrapper).props("selectedKey")).toBe("2")
    wrapper.unmount()
  })

  it("forgets the member when the form clears the field", async () => {
    const wrapper = select({modelValue: 1})
    expect(picker(wrapper).props("selectedKey")).toBe("1")

    await wrapper.setProps({modelValue: undefined})

    expect(picker(wrapper).props("selectedKey")).toBeNull()
    wrapper.unmount()
  })

  it("keeps the member it holds when a list arrives without them in it", async () => {
    const wrapper = select({modelValue: 1})

    await wrapper.setProps({users: [bea]})

    expect((picker(wrapper).props("options") as Array<{key: string}>).map(one => one.key))
      .toContain("1")
    wrapper.unmount()
  })

  it("says nothing is wrong until the form says so", () => {
    expect(mount(UserSelect, {props: {users: [ada]}}).find(".island-field__said").text()).toBe("")
    expect(mount(UserSelect, {props: {users: [ada], errorMessages: "Pick somebody."}})
      .find(".island-field__said").text()).toBe("Pick somebody.")
    expect(mount(UserSelect, {props: {users: [ada], errorMessages: ["First.", "Second."]}})
      .find(".island-field__said").text()).toBe("First.")
  })
})
