import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {autocompletion, type Completion, CompletionContext, completionStatus} from "@codemirror/autocomplete"
import {EditorSelection, EditorState} from "@codemirror/state"
import {EditorView} from "@codemirror/view"
import {cursorCharRight} from "@codemirror/commands"
import MarkdownEditor from "@/components/island/MarkdownEditor.vue"
import {markdownEditing} from "@/components/island/markdownEditing"
import {channelCompletion, forgetMentionLists, mentionCompletion, mentionOption, mentionOptionClass, reopenMention} from "@/components/island/markdownMentions"
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
  vi.mocked(searchServerMembers).mockResolvedValue([{id: "11", name: "Anna", username: "anna", avatar: "https://cdn/anna.png"}])
  vi.mocked(listServerRoles).mockResolvedValue([{id: "901", name: "Gamers", colour: 0x3498DB}, {id: "902", name: "Board"}])
  vi.mocked(listServerChannels).mockResolvedValue([{id: "1", name: "general"}, {id: "2", name: "events-info", category: "Events"}])
  vi.mocked(readMentionNames).mockResolvedValue({users: [{id: "123456789012345611", name: "Anna"}], roles: [{id: "223456789012345901", name: "Gamers", colour: 0x3498DB}], channels: []})
})

const asking = (doc: string, explicit = false) =>
  new CompletionContext(EditorState.create({doc}), doc.length, explicit)

describe("a mention being typed", () => {
  it("offers every member the server finds, as it found them, then the roles whose name holds what was typed", async () => {
    vi.mocked(searchServerMembers).mockResolvedValue([{id: "12", name: "The Old Man", username: "extratoast", avatar: ""}])
    const found = await mentionCompletion(asking("ask @ex"))

    expect(found?.options.map(one => [one.label, one.detail, one.apply])).toEqual([["The Old Man", "extratoast", "<@12>"]])
    expect(searchServerMembers).toHaveBeenCalledWith("ex")
  })

  it("keeps matching a name past a space, for as long as somebody answers to it", async () => {
    vi.mocked(searchServerMembers).mockResolvedValue([{id: "12", name: "The Old Man - Joris", username: "extratoast", avatar: ""}])
    const found = await mentionCompletion(asking("ask @The Old Ma"))

    expect(found?.from).toBe("ask ".length)
    expect(found?.options.map(one => one.label)).toEqual(["The Old Man - Joris"])
    expect(searchServerMembers).toHaveBeenLastCalledWith("the old ma")
  })

  it("lets a sentence after a name go once nobody answers to it, and asks nothing past a nickname's length", async () => {
    vi.mocked(searchServerMembers).mockResolvedValue([])
    expect(await mentionCompletion(asking("ask @Anna said hi"))).toBeNull()
    expect(searchServerMembers).toHaveBeenLastCalledWith("anna said hi")

    vi.mocked(searchServerMembers).mockClear()
    expect(await mentionCompletion(asking("@abcdefghij klmnopqrst uvwxyzabcd efgh"))).toBeNull()
    expect(searchServerMembers).not.toHaveBeenCalled()
  })

  it("offers a role whose name has a space in it", async () => {
    vi.mocked(listServerRoles).mockResolvedValue([{id: "903", name: "Events Gamenights!"}])
    vi.mocked(searchServerMembers).mockResolvedValue([])

    expect((await mentionCompletion(asking("for @events game")))?.options.map(one => one.apply)).toEqual(["<@&903>"])
  })

  it("offers roles alone before two letters, and asks the server for no members", async () => {
    const found = await mentionCompletion(asking("hey @g"))

    expect(found?.options.map(one => one.apply)).toEqual(["<@&901>"])
    expect(searchServerMembers).not.toHaveBeenCalled()
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

describe("a mention's row", () => {
  it("draws a member's avatar, and a role's name in its colour or the mention's", () => {
    const anna: Completion & {avatar: string} = {label: "Anna", type: "member", avatar: "https://cdn/anna.png"}
    const gamers: Completion & {colour: number} = {label: "@Gamers", type: "role", colour: 0x3498DB}
    const nobody: Completion & {avatar: string} = {label: "Nobody", type: "member", avatar: ""}
    const avatar = mentionOption.render(anna) as HTMLImageElement
    const coloured = mentionOption.render(gamers) as HTMLElement
    const plain = mentionOption.render({label: "@Board", type: "role"}) as HTMLElement

    expect(avatar.getAttribute("src")).toBe("https://cdn/anna.png")
    expect([coloured.textContent, coloured.style.getPropertyValue("--mention")]).toEqual(["@Gamers", "#3498db"])
    expect([plain.textContent, plain.style.getPropertyValue("--mention")]).toEqual(["@Board", ""])
    expect(mentionOption.render(nobody)).toBeNull()
    expect(mentionOption.render({label: "#general", type: "channel"})).toBeNull()
  })

  it("hides the label a role's row draws itself", () => {
    expect(mentionOptionClass({label: "@Board", type: "role"})).toBe("cm-option-role")
    expect(mentionOptionClass({label: "Anna", type: "member"})).toBe("")
  })
})

describe("a channel being typed", () => {
  it("offers the channels whose name holds what was typed, with the category each is in", async () => {
    const found = await channelCompletion(asking("see #e"))

    expect(found?.options.map(one => [one.label, one.detail, one.apply])).toEqual([
      ["#general", undefined, "<#1>"],
      ["#events-info", "Events", "<#2>"],
    ])
  })

  it("offers channels at the start of a line only right before a letter, since `# ` there is a heading", async () => {
    expect((await channelCompletion(asking("#gen")))?.options.map(one => one.apply)).toEqual(["<#1>"])
    expect((await channelCompletion(asking("  #gen")))?.options.map(one => one.apply)).toEqual(["<#1>"])
    expect(await channelCompletion(asking("#"))).toBeNull()
    expect(await channelCompletion(asking("#", true))).toBeNull()
    expect(await channelCompletion(asking("#-ev"))).toBeNull()
    expect(await channelCompletion(asking("# Heading"))).toBeNull()
    expect((await channelCompletion(asking("see #")))?.options).toHaveLength(2)
  })

  it("offers nothing inside a word, or where nothing answers", async () => {
    expect(await channelCompletion(asking("c#gen"))).toBeNull()
    forgetMentionLists()
    vi.mocked(listServerChannels).mockResolvedValue(null)

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

  it("are drawn as pills, named once the api answers, and stay pills under the cursor", async () => {
    const doc = "hi <@123456789012345611> and <@&223456789012345901> at <t:1790000000:f>"
    const view = open(doc, 0)
    await flushPromises()

    expect(view.contentDOM.textContent).toBe(`hi @Anna and @Gamers at ${timestampText(1790000000, "f")}`)
    expect((view.contentDOM.querySelectorAll(".cm-mention")[1] as HTMLElement).style.getPropertyValue("--mention")).toBe("#3498db")
    const touching = open(doc, 5)
    await flushPromises()
    expect(touching.contentDOM.textContent).toContain("@Anna")
    expect(touching.contentDOM.textContent).not.toContain("<@123456789012345611>")
  })

  it("redraw the cursor once named, so it is drawn after the whole name", async () => {
    const view = open("hi <#323456789012345602>", 0)
    const redrawn = vi.spyOn(view, "dispatch")
    await flushPromises()

    expect(redrawn).toHaveBeenCalledWith({selection: view.state.selection})
  })

  it("are one step for the cursor", () => {
    const doc = "hi <#323456789012345602> ok"
    const view = open(doc, 3)

    expect(cursorCharRight(view)).toBe(true)
    expect(view.state.selection.main.head).toBe(doc.indexOf(" ok"))
  })

  it("turn back into what was typed, less a letter, on a backspace after one, and ask again", async () => {
    vi.mocked(readMentionNames).mockResolvedValue({users: [{id: "123456789012345611", name: "Anna"}], roles: [], channels: [{id: "323456789012345602", name: "counter-strike-2"}]})
    const doc = "in <#323456789012345602> with <@123456789012345611>"
    const view = new EditorView({
      parent: document.body,
      state: EditorState.create({
        doc,
        selection: EditorSelection.cursor(doc.indexOf(" with")),
        extensions: [...markdownEditing, autocompletion({override: [mentionCompletion, channelCompletion]})],
      }),
    })
    views.push(view)
    await flushPromises()

    expect(reopenMention(view)).toBe(true)
    expect(view.state.doc.toString()).toBe("in #counter-strike- with <@123456789012345611>")
    expect(view.state.selection.main.head).toBe("in #counter-strike-".length)
    expect(completionStatus(view.state)).not.toBeNull()

    view.dispatch({selection: EditorSelection.cursor(view.state.doc.length)})
    expect(reopenMention(view)).toBe(true)
    expect(view.state.doc.toString()).toBe("in #counter-strike- with @Ann")
  })

  it("leave a backspace to delete as ever where no mention ends at the cursor, or its name is not read yet", () => {
    vi.mocked(readMentionNames).mockReturnValue(new Promise(() => {}))
    const unnamed = "in <#323456789012345602>"
    expect(reopenMention(open(unnamed, unnamed.length))).toBe(false)
    expect(reopenMention(open("plain text", 5))).toBe(false)
    expect(reopenMention(open("`<#323456789012345602>`", 23))).toBe(false)
    const picked = open("ab cd", 0)
    picked.dispatch({selection: EditorSelection.range(0, 2)})
    expect(reopenMention(picked)).toBe(false)
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
