import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {CompletionContext} from "@codemirror/autocomplete"
import {EditorSelection, EditorState} from "@codemirror/state"
import {EditorView} from "@codemirror/view"
import MarkdownEditor from "@/components/island/MarkdownEditor.vue"
import {markdownEditing} from "@/components/island/markdownEditing"
import {channelCompletion, forgetMentionLists, mentionCompletion} from "@/components/island/markdownMentions"
import {forgetMentionNames} from "@/domains/discord"
import {listServerChannels, readMentionNames} from "@/domains/discord/adapters/mentions"
import {searchServerMembers} from "@/domains/discord/adapters/members"
import {listServerRoles} from "@/domains/discord/adapters/roles"
import {timestampText} from "@/plugins/discordTime"

vi.mock("@/domains/discord/adapters/mentions", () => ({readMentionNames: vi.fn(), listServerChannels: vi.fn()}))
vi.mock("@/domains/discord/adapters/members", () => ({searchServerMembers: vi.fn(), listUnclaimedMembers: vi.fn()}))
vi.mock("@/domains/discord/adapters/roles", () => ({listServerRoles: vi.fn()}))

beforeEach(() => {
  forgetMentionLists()
  forgetMentionNames()
  vi.mocked(searchServerMembers).mockResolvedValue([{id: "11", name: "Anna", username: "anna", avatar: ""}])
  vi.mocked(listServerRoles).mockResolvedValue([{id: "901", name: "Gamers"}, {id: "902", name: "Board"}])
  vi.mocked(listServerChannels).mockResolvedValue([{id: "1", name: "general"}, {id: "2", name: "events-info"}])
  vi.mocked(readMentionNames).mockResolvedValue({users: [{id: "123456789012345611", name: "Anna"}], roles: [{id: "223456789012345901", name: "Gamers", colour: 0x3498DB}], channels: []})
})

const asking = (doc: string, explicit = false) =>
  new CompletionContext(EditorState.create({doc}), doc.length, explicit)

describe("a mention being typed", () => {
  it("offers the members the server finds and the roles whose name holds what was typed", async () => {
    const found = await mentionCompletion(asking("ask @an"))

    expect(found?.options.map(one => [one.label, one.apply])).toEqual([["@Anna", "<@11>"]])
    expect(searchServerMembers).toHaveBeenCalledWith("an")
    expect(found?.validFor instanceof Function && found.validFor("@ann", 0, 4, EditorState.create())).toBe(true)
  })

  it("offers roles alone before two letters, and asks the server for no members", async () => {
    const found = await mentionCompletion(asking("hey @g"))

    expect(found?.options.map(one => one.apply)).toEqual(["<@&901>"])
    expect(searchServerMembers).not.toHaveBeenCalled()
    expect(found?.validFor instanceof Function && found.validFor("@ga", 0, 3, EditorState.create())).toBe(false)
  })

  it("offers nothing inside a word, for a bare @, or where nothing answers", async () => {
    vi.mocked(searchServerMembers).mockResolvedValue(null)

    expect(await mentionCompletion(asking("mail@an"))).toBeNull()
    expect(await mentionCompletion(asking("hey @"))).toBeNull()
    expect(await mentionCompletion(asking("hey @zz"))).toBeNull()
    expect((await mentionCompletion(asking("hey @", true)))?.options).toHaveLength(2)
  })

  it("reads the roles once however often it is asked", async () => {
    vi.mocked(listServerRoles).mockResolvedValue(null)

    expect(await mentionCompletion(asking("@b", true))).toBeNull()
    await mentionCompletion(asking("@x", true))
    expect(listServerRoles).toHaveBeenCalledTimes(1)
  })
})

describe("a channel being typed", () => {
  it("offers the channels whose name holds what was typed, in the middle of a line", async () => {
    const found = await channelCompletion(asking("see #event"))

    expect(found?.options.map(one => [one.label, one.apply])).toEqual([["#events-info", "<#2>"]])
  })

  it("offers nothing at the start of a line, where # is a heading, or where nothing answers", async () => {
    vi.mocked(listServerChannels).mockResolvedValue(null)

    expect(await channelCompletion(asking("#gen"))).toBeNull()
    expect(await channelCompletion(asking("  #gen"))).toBeNull()
    expect(await channelCompletion(asking("see #gen"))).toBeNull()
    expect(await channelCompletion(asking("plain"))).toBeNull()
  })
})

describe("a mention and a timestamp in the editor", () => {
  const views: EditorView[] = []
  afterEach(() => {
    for (const view of views.splice(0)) view.destroy()
  })

  const open = (doc: string, at: number) => {
    const view = new EditorView({
      parent: document.body,
      state: EditorState.create({doc, selection: EditorSelection.cursor(at), extensions: markdownEditing}),
    })
    views.push(view)
    view.contentDOM.focus()
    view.dispatch({selection: EditorSelection.cursor(at)})
    return view
  }

  it("are drawn as pills, named once the api answers, and written out while the cursor touches one", async () => {
    const doc = "hi <@123456789012345611> and <@&223456789012345901> at <t:1790000000:f>"
    const view = open(doc, 0)
    await flushPromises()

    expect(view.contentDOM.textContent).toBe(`hi @Anna and @Gamers at ${timestampText(1790000000, "f")}`)
    expect((view.contentDOM.querySelectorAll(".cm-mention")[1] as HTMLElement).style.getPropertyValue("--mention")).toBe("#3498db")
    expect(open(doc, 5).contentDOM.textContent).toContain("<@123456789012345611>")
  })

  it("draw a channel with its sign and a timestamp without a style as f", async () => {
    vi.mocked(readMentionNames).mockResolvedValue({users: [], roles: [], channels: [{id: "323456789012345602", name: "events-info"}]})
    const view = open("in <#323456789012345602> at <t:1790000000> ok", 0)
    await flushPromises()

    expect(view.contentDOM.textContent).toBe(`in #events-info at ${timestampText(1790000000, "f")} ok`)
  })

  it("take a press on a pill as a press on the line, which leaves the text alone", () => {
    const doc = "hi <@123456789012345611> <t:1790000000>"
    const view = open(doc, 0)
    const pills = [...view.contentDOM.querySelectorAll(".cm-mention, .cm-timestamp")]
    expect(pills).toHaveLength(2)
    const time = open("at <t:1790000000>", 0).contentDOM.querySelector(".cm-timestamp") as HTMLElement
    for (const pill of [...pills, time]) pill.dispatchEvent(new MouseEvent("mousedown", {bubbles: true, cancelable: true}))

    expect(view.state.doc.toString()).toBe(doc)
  })
})

describe("the timestamp picker", () => {
  it("writes the moment chosen in the style chosen, where the cursor is", async () => {
    const wrapper = mount(MarkdownEditor, {attachTo: document.body, props: {modelValue: "at "}})
    await flushPromises()

    await wrapper.get("[data-testid=markdown-time]").trigger("click")
    wrapper.findComponent({name: "DateTimeInput"}).vm.$emit("update:modelValue", "2026-10-03T20:00")
    await flushPromises()
    await wrapper.get("[data-testid=markdown-time-R]").trigger("click")

    const unix = Math.floor(new Date("2026-10-03T20:00").getTime() / 1000)
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([`<t:${unix}:R>at `])
    expect(wrapper.find("[data-testid=markdown-time-picker]").exists()).toBe(false)
    wrapper.unmount()
  })

  it("offers no style until a moment is chosen, and shuts on Escape", async () => {
    const wrapper = mount(MarkdownEditor, {attachTo: document.body, props: {modelValue: ""}})
    await wrapper.get("[data-testid=markdown-time]").trigger("click")

    expect(wrapper.find("[data-testid=markdown-time-f]").exists()).toBe(false)
    document.dispatchEvent(new KeyboardEvent("keydown", {key: "Escape"}))
    await flushPromises()
    expect(wrapper.find("[data-testid=markdown-time-picker]").exists()).toBe(false)

    await wrapper.get("[data-testid=markdown-time]").trigger("click")
    await wrapper.get("[data-testid=markdown-time]").trigger("click")
    expect(wrapper.find("[data-testid=markdown-time-picker]").exists()).toBe(false)
    wrapper.unmount()
  })
})
