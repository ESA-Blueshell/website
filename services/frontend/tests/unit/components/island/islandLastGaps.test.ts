import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {CompletionContext} from "@codemirror/autocomplete"
import {markdown, markdownLanguage} from "@codemirror/lang-markdown"
import {EditorSelection, EditorState} from "@codemirror/state"
import {EditorView} from "@codemirror/view"
import IslandPicker from "@/components/island/IslandPicker.vue"
import ContributionPeriodPicker from "@/components/form/fields/ContributionPeriodPicker.vue"
import EventPicker from "@/components/form/fields/EventPicker.vue"
import MemberTypeSelect from "@/components/form/fields/MemberTypeSelect.vue"
import UserPicker from "@/components/form/fields/UserPicker.vue"
import {emojiCompletion} from "@/components/island/markdownEmoji"
import {markdownLive} from "@/components/island/markdownLive"

const {mockEvents, mockPeriods, mockUsers} = vi.hoisted(() => ({
  mockEvents: vi.fn(), mockPeriods: vi.fn(), mockUsers: vi.fn(),
}))
vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  findEvents: mockEvents,
  findContributionPeriods: mockPeriods,
  findUsers: mockUsers,
}))

const stubs = {IslandField: {template: "<div><slot /></div>"}}
const picker = (wrapper: ReturnType<typeof mount>) => wrapper.findComponent({name: "IslandPicker"})
const rows = (wrapper: ReturnType<typeof mount>) =>
  picker(wrapper).props("options") as Array<{key: string; label: string; note?: string}>

describe("a picker with nothing to stand on", () => {
  it("keeps the list up while focus moves inside it, and closes when it lands outside", async () => {
    const wrapper = mount(IslandPicker, {
      props: {options: [{key: "a", label: "A"}], testidPrefix: "pick"},
      attachTo: document.body,
    })
    await wrapper.find('[data-testid="pick-search"]').trigger("click")
    await wrapper.vm.$nextTick()

    await wrapper.find(".picker").trigger("focusout", {relatedTarget: null})
    expect(document.querySelector('[data-testid="pick-list"]')).not.toBeNull()

    const row = document.querySelector('[data-testid="pick-a"]')
    await wrapper.find(".picker").trigger("focusout", {relatedTarget: row})
    expect(document.querySelector('[data-testid="pick-list"]')).not.toBeNull()

    await wrapper.find(".picker").trigger("focusout", {relatedTarget: document.body})
    await wrapper.vm.$nextTick()

    expect(document.querySelector('[data-testid="pick-list"]')).toBeNull()
    wrapper.unmount()
  })

  it("comes down and goes up again from the mark beside the field", async () => {
    const wrapper = mount(IslandPicker, {
      props: {options: [{key: "a", label: "A"}], testidPrefix: "pick"},
      attachTo: document.body,
    })

    await wrapper.find(".picker__caret--field").trigger("click")
    await wrapper.vm.$nextTick()
    expect(document.querySelector('[data-testid="pick-list"]')).not.toBeNull()

    await wrapper.find(".picker__caret--field").trigger("click")
    await wrapper.vm.$nextTick()

    expect(document.querySelector('[data-testid="pick-list"]')).toBeNull()
    wrapper.unmount()
  })
})

describe("what the emoji list answers to", () => {
  const asking = (doc: string, explicit = false) => {
    const state = EditorState.create({doc})
    return emojiCompletion(new CompletionContext(state, doc.length, explicit))
  }

  it("offers everything it has when it is asked for outright", () => {
    expect(asking(":", true)?.options.length).toBeGreaterThan(0)
  })

  it("says nothing on a bare colon nobody asked about", () => {
    expect(asking(":")).toBeNull()
  })
})

describe("an emoji drawn in the text", () => {
  it("takes no event of its own, so the line under it is still the line", () => {
    const view = new EditorView({
      parent: document.body,
      state: EditorState.create({
        doc: "we are :fire: about it",
        selection: EditorSelection.cursor(0),
        extensions: [markdown({base: markdownLanguage}), markdownLive],
      }),
    })
    const drawn = view.contentDOM.querySelector("span")
    drawn?.dispatchEvent(new Event("mousedown", {bubbles: true}))

    expect(view.contentDOM.textContent).toContain("🔥")
    view.destroy()
  })
})

describe("the pickers that fetch a list", () => {
  beforeEach(() => {
    mockEvents.mockReset()
    mockPeriods.mockReset()
    mockUsers.mockReset()
  })
  afterEach(() => vi.restoreAllMocks())

  it("names an event with no day, and a period with no dates at all", async () => {
    mockEvents.mockResolvedValue({data: {content: [{id: 3, title: "LAN"}]}})
    const events = mount(EventPicker, {props: {}, global: {stubs}})
    await flushPromises()
    expect(rows(events)[0]?.note).toBe("")

    mockPeriods.mockResolvedValue({data: [{id: 9, endDate: "2026-08-31"}]})
    const periods = mount(ContributionPeriodPicker, {props: {}, global: {stubs}})
    await flushPromises()
    expect(rows(periods)[0]?.label).toBe("Period #9")
  })

  it("reports the period that was picked", async () => {
    mockPeriods.mockResolvedValue({data: []})
    const wrapper = mount(ContributionPeriodPicker, {props: {}, global: {stubs}})
    await flushPromises()

    picker(wrapper).vm.$emit("pick", "4")

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe(4)
  })

  it("reads an answer with no content at all as an empty list", async () => {
    mockEvents.mockResolvedValue({})
    const events = mount(EventPicker, {props: {}, global: {stubs}})
    await flushPromises()
    expect(rows(events)).toEqual([])

    mockPeriods.mockResolvedValue({})
    const periods = mount(ContributionPeriodPicker, {props: {}, global: {stubs}})
    await flushPromises()
    expect(rows(periods)).toEqual([])

    mockUsers.mockResolvedValue({})
    const people = mount(UserPicker, {props: {}, global: {stubs}})
    picker(people).vm.$emit("opened")
    await flushPromises()
    expect(rows(people)).toEqual([])
  })

  it("takes the label and the name a form gives it, or falls back to its own", async () => {
    mockPeriods.mockResolvedValue({data: []})
    const told = mount(ContributionPeriodPicker, {
      props: {label: "Which year", testid: "period"}, global: {stubs},
    })
    await flushPromises()
    expect(picker(told).props("testidPrefix")).toBe("period")

    mockUsers.mockResolvedValue({data: {content: []}})
    const people = mount(UserPicker, {props: {}, global: {stubs}})
    expect(picker(people).props("testidPrefix")).toBe("user-picker")
  })

  it("says what is wrong with the member field", () => {
    mockUsers.mockResolvedValue({data: {content: []}})
    const wrapper = mount(UserPicker, {props: {errorMessages: ["Pick somebody."]}})

    expect(wrapper.find(".island-field__said").text()).toBe("Pick somebody.")
  })

  it("says what is wrong with the member-type field, given a sentence or a list", () => {
    expect(mount(MemberTypeSelect, {props: {errorMessages: "Pick one."}})
      .find(".island-field__said").text()).toBe("Pick one.")
    expect(mount(MemberTypeSelect, {props: {errorMessages: ["Pick one.", "Or two."]}})
      .find(".island-field__said").text()).toBe("Pick one.")
  })
})
