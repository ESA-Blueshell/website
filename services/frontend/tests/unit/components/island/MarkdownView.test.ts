import {describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import MarkdownView from "@/components/island/MarkdownView.vue"
import {readMentionNames} from "@/domains/discord/adapters/mentions"
import {forgetMentionNames} from "@/domains/discord/mentions"
import {DescriptionNodeKind} from "@/services/api"

vi.mock("@/domains/discord/adapters/mentions", () => ({readMentionNames: vi.fn(), listServerChannels: vi.fn()}))

describe("MarkdownView", () => {
  it("draws the markdown it is given as its elements", () => {
    const wrapper = mount(MarkdownView, {props: {source: "# Hey\n\n- **one**\n- two"}})

    expect(wrapper.get(".markdown-view h1").text()).toBe("Hey")
    expect(wrapper.findAll(".markdown-view li")).toHaveLength(2)
    expect(wrapper.get("li strong").text()).toBe("one")
  })

  it("draws the api's tree where it has one, naming its mentions in one read", async () => {
    forgetMentionNames()
    vi.mocked(readMentionNames).mockResolvedValue({users: [], roles: [{id: "2", name: "Board", colour: 0xff0000}], channels: []})
    const role = {kind: DescriptionNodeKind.ROLE_MENTION, start: 0, end: 22, children: [], id: "2"}
    const wrapper = mount(MarkdownView, {
      props: {source: "ignored", nodes: [{kind: DescriptionNodeKind.PARAGRAPH, start: 0, end: 22, children: [role]}]},
    })
    await flushPromises()

    expect(readMentionNames).toHaveBeenCalledWith({users: [], roles: ["2"], channels: []})
    expect(wrapper.get(".markdown-view p .mention--role").text()).toBe("@Board")
    expect(wrapper.text()).not.toContain("ignored")
  })

  it("names nothing for a tree that is replaced before its names are read", async () => {
    forgetMentionNames()
    let answer: (names: object) => void = () => undefined
    vi.mocked(readMentionNames).mockReturnValue(new Promise(resolve => {
      answer = resolve
    }))
    const role = {kind: DescriptionNodeKind.ROLE_MENTION, start: 0, end: 22, children: [], id: "2"}
    const wrapper = mount(MarkdownView, {props: {nodes: [role]}})
    await wrapper.setProps({nodes: [{kind: DescriptionNodeKind.TEXT, start: 0, end: 2, children: [], text: "hi"}]})
    answer({users: [], roles: [{id: "2", name: "Board"}], channels: []})
    await flushPromises()

    expect(wrapper.text()).toBe("hi")
  })

  it("names the source's mentions once it is drawn in the tree's place", async () => {
    forgetMentionNames()
    vi.mocked(readMentionNames).mockResolvedValue({users: [{id: "123456789012345678", name: "Anna"}], roles: [], channels: []})
    const wrapper = mount(MarkdownView, {props: {source: "hi <@123456789012345678>", nodes: []}})
    await wrapper.setProps({nodes: null})
    await flushPromises()

    expect(wrapper.get("[data-user]").text()).toBe("@Anna")
  })

  it("draws the tree in the source's place once it has one", async () => {
    const wrapper = mount(MarkdownView, {props: {source: "**old**"}})
    await wrapper.setProps({nodes: [{kind: DescriptionNodeKind.TEXT, start: 0, end: 3, children: [], text: "new"}]})
    await flushPromises()

    expect(wrapper.text()).toBe("new")
  })

  it("drops a script written into it", () => {
    const wrapper = mount(MarkdownView, {props: {source: "hi <script>alert(1)</script>"}})

    expect(wrapper.find("script").exists()).toBe(false)
  })
})

describe("a spoiler in a description", () => {
  const view = () => mount(MarkdownView, {props: {source: "the end: ||he [wins](https://x.io)|| and more"}})

  it("is shown once pressed, and stays shown", async () => {
    const wrapper = view()
    const hidden = wrapper.get(".spoiler")

    await hidden.trigger("click")

    expect(hidden.classes()).toContain("spoiler--shown")
    expect(hidden.attributes("aria-expanded")).toBe("true")
  })

  it("is shown by Enter or the space bar, and by no other key", async () => {
    const wrapper = view()
    const hidden = wrapper.get(".spoiler")

    await hidden.trigger("keydown", {key: "a"})
    expect(hidden.classes()).not.toContain("spoiler--shown")
    await hidden.trigger("keydown", {key: " "})
    expect(hidden.classes()).toContain("spoiler--shown")
  })

  it("does not follow a link inside it until it is shown", async () => {
    const wrapper = view()
    const link = wrapper.get(".spoiler a")

    const hiddenPress = new MouseEvent("click", {bubbles: true, cancelable: true})
    link.element.dispatchEvent(hiddenPress)
    const shownPress = new MouseEvent("click", {bubbles: true, cancelable: true})
    link.element.dispatchEvent(shownPress)

    expect(hiddenPress.defaultPrevented).toBe(true)
    expect(shownPress.defaultPrevented).toBe(false)
  })
})

describe("an emoji in a description", () => {
  it("is the character where its picture will not load", async () => {
    const wrapper = mount(MarkdownView, {props: {source: "hot 🔥"}, attachTo: document.body})

    wrapper.get("img.emoji").element.dispatchEvent(new Event("error"))

    expect(wrapper.find("img.emoji").exists()).toBe(false)
    expect(wrapper.text()).toBe("hot 🔥")
    wrapper.unmount()
  })

  it("is the character where its picture will not load, drawn from the api's tree", async () => {
    const fire = {kind: DescriptionNodeKind.EMOJI, start: 4, end: 6, children: [], text: "🔥"}
    const wrapper = mount(MarkdownView, {props: {nodes: [fire]}, attachTo: document.body})

    await wrapper.get("img.emoji").trigger("error")

    expect(wrapper.find("img.emoji").exists()).toBe(false)
    expect(wrapper.text()).toBe("🔥")
    await wrapper.setProps({nodes: [fire, {...fire, start: 6, end: 8, text: "🎮"}]})
    expect(wrapper.findAll("img.emoji")).toHaveLength(1)
    wrapper.unmount()
  })

  it("leaves a picture the description links to alone when it will not load", async () => {
    const wrapper = mount(MarkdownView, {props: {source: "![map](https://x.io/map.png)"}, attachTo: document.body})

    wrapper.get("img").element.dispatchEvent(new Event("error"))

    expect(wrapper.find("img").exists()).toBe(true)
    wrapper.unmount()
  })
})

describe("a mention in a description", () => {
  it("is named once the api answers, and named again when the description changes", async () => {
    vi.mocked(readMentionNames).mockResolvedValue({
      users: [{id: "123456789012345611", name: "Anna"}, {id: "123456789012345612", name: "Bea"}], roles: [], channels: [],
    })
    const wrapper = mount(MarkdownView, {props: {source: "ask <@123456789012345611>"}})
    await flushPromises()
    expect(wrapper.get(".mention").text()).toBe("@Anna")

    await wrapper.setProps({source: "ask <@123456789012345612>"})
    await flushPromises()
    expect(wrapper.get(".mention").text()).toBe("@Bea")
  })
})
