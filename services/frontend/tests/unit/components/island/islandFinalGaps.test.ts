import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {CompletionContext} from "@codemirror/autocomplete"
import {markdown, markdownLanguage} from "@codemirror/lang-markdown"
import {EditorSelection, EditorState} from "@codemirror/state"
import {EditorView} from "@codemirror/view"
import ContributionPeriodPicker from "@/components/form/fields/ContributionPeriodPicker.vue"
import EventPicker from "@/components/form/fields/EventPicker.vue"
import UserPicker from "@/components/form/fields/UserPicker.vue"
import UserSelect from "@/components/form/fields/UserSelect.vue"
import IslandCountry from "@/components/island/IslandCountry.vue"
import IslandFile from "@/components/island/IslandFile.vue"
import IslandInput from "@/components/island/IslandInput.vue"
import IslandPhone from "@/components/island/IslandPhone.vue"
import {emojiCompletion} from "@/components/island/markdownEmoji"
import {markdownLive} from "@/components/island/markdownLive"

const {mockEvents, mockPeriods, mockUsers, mockSearch} = vi.hoisted(() => ({
  mockEvents: vi.fn(), mockPeriods: vi.fn(), mockUsers: vi.fn(), mockSearch: vi.fn(),
}))
vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  findEvents: mockEvents,
  findContributionPeriods: mockPeriods,
  findUsers: mockUsers,
}))
vi.mock("@/domains/user", () => ({searchMemberAccounts: mockSearch}))

const stubs = {IslandField: {template: "<div><slot /></div>"}}
const picker = (wrapper: ReturnType<typeof mount>) => wrapper.findComponent({name: "IslandPicker"})

describe("the eye on a password field", () => {
  it("carries no name of its own where the field has none", () => {
    const wrapper = mount(IslandInput, {props: {modelValue: "secret", type: "password"}})

    expect(wrapper.find("button").attributes("data-testid")).toBeUndefined()
    expect(wrapper.find("button").attributes("aria-label")).toBe("Show the password")
  })
})

describe("a file field", () => {
  it("says nothing about the weight of a file the browser sized at nothing", () => {
    const empty = new File([], "empty.pdf", {type: "application/pdf"})
    const wrapper = mount(IslandFile, {props: {modelValue: empty}})

    expect(wrapper.find(".island-file__weight").text()).toBe("")
  })

  it("empties the browser's own box when the file is taken off", async () => {
    const wrapper = mount(IslandFile, {
      props: {modelValue: new File(["x"], "a.png", {type: "image/png"}), testid: "poster"},
      attachTo: document.body,
    })

    await wrapper.find('[data-testid="poster-clear"]').trigger("click")

    expect((wrapper.find("input").element as HTMLInputElement).value).toBe("")
    wrapper.unmount()
  })
})

describe("a phone number the form hands back", () => {
  it("is left alone when it says nothing at all", async () => {
    const wrapper = mount(IslandPhone, {props: {modelValue: "", testidPrefix: "phone"}})

    await wrapper.setProps({modelValue: ""})

    expect(wrapper.emitted("update:modelValue")).toBeUndefined()
  })

  it("is not written back when it is already what the field holds", async () => {
    const wrapper = mount(IslandPhone, {props: {modelValue: "+31612345678", testidPrefix: "phone"}})
    const before = wrapper.emitted("update:modelValue")?.length ?? 0

    await wrapper.setProps({modelValue: "+31612345678"})

    expect(wrapper.emitted("update:modelValue")?.length ?? 0).toBe(before)
  })

  it("draws the flag of the country it holds while the list says nothing", () => {
    const wrapper = mount(IslandPhone, {props: {modelValue: "", testidPrefix: "phone"}})

    expect(wrapper.find(".country-flag").classes()).toContain("fi-nl")
  })
})

describe("a country with no demonym of its own", () => {
  it("is said by its name instead", () => {
    const wrapper = mount(IslandCountry, {
      props: {modelValue: "AQ", reading: "nationality", testidPrefix: "nat"},
    })
    const rows = picker(wrapper).props("options") as Array<{key: string; label: string}>

    expect(rows.find(one => one.key === "AQ")?.label).toBeTruthy()
  })
})

describe("what the pickers say when nothing is wrong", () => {
  beforeEach(() => {
    mockEvents.mockReset()
    mockPeriods.mockReset()
    mockUsers.mockReset()
    mockSearch.mockReset()
  })

  it("says nothing under an event field that was given no refusal", async () => {
    mockEvents.mockResolvedValue({data: {content: []}})
    const wrapper = mount(EventPicker, {props: {}})
    await flushPromises()

    expect(wrapper.find(".island-field__said").text()).toBe("")
  })

  it("says nothing under a period field that was given no refusal", async () => {
    mockPeriods.mockResolvedValue({data: []})
    const wrapper = mount(ContributionPeriodPicker, {props: {}})
    await flushPromises()

    expect(wrapper.find(".island-field__said").text()).toBe("")
  })

  it("sorts events and periods that carry no dates at all", async () => {
    mockEvents.mockResolvedValue({data: {content: [{id: 1, title: "A"}, {id: 2, title: "B"}]}})
    const events = mount(EventPicker, {props: {}, global: {stubs}})
    await flushPromises()
    expect((picker(events).props("options") as Array<{key: string}>)).toHaveLength(2)

    mockPeriods.mockResolvedValue({data: [{id: 1}, {id: 2}]})
    const periods = mount(ContributionPeriodPicker, {props: {}, global: {stubs}})
    await flushPromises()
    expect((picker(periods).props("options") as Array<{key: string}>)).toHaveLength(2)
  })

  it("reports the person a bare picker chose", async () => {
    mockUsers.mockResolvedValue({data: {content: []}})
    const wrapper = mount(UserPicker, {props: {}, global: {stubs}})

    picker(wrapper).vm.$emit("pick", "12")

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe(12)
  })

  it("keeps the member it holds when the page's list names nobody", async () => {
    mockSearch.mockResolvedValue([])
    const ada = {id: 1, fullName: "Ada", email: "ada@example.com", roles: ["MEMBER"]}
    const wrapper = mount(UserSelect, {props: {users: [ada], modelValue: 1}, global: {stubs}})

    await wrapper.setProps({users: []})

    expect(picker(wrapper).props("selectedKey")).toBe("1")
  })

  it("reports the member that was chosen, and forgets one it cannot place", async () => {
    mockSearch.mockResolvedValue([])
    const ada = {id: 1, fullName: "Ada", email: "ada@example.com", roles: ["MEMBER"]}
    const wrapper = mount(UserSelect, {props: {users: [ada]}, global: {stubs}})

    picker(wrapper).vm.$emit("pick", "1")
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe(1)

    picker(wrapper).vm.$emit("pick", "99")
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe(99)
  })
})

describe("the emoji list on a name it cannot read", () => {
  it("says nothing where the colon is followed by nothing it knows", () => {
    const state = EditorState.create({doc: "text :"})
    expect(emojiCompletion(new CompletionContext(state, 6, false))).toBeNull()
  })
})

describe("an emoji written where a line is being read", () => {
  it("is drawn from the second line as well as the first", () => {
    const view = new EditorView({
      parent: document.body,
      state: EditorState.create({
        doc: "first line\nsecond :fire: line",
        selection: EditorSelection.cursor(0),
        extensions: [markdown({base: markdownLanguage}), markdownLive],
      }),
    })

    expect(view.contentDOM.textContent).toContain("🔥")
    view.destroy()
  })
})
