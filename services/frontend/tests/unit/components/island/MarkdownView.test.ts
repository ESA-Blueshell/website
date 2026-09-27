import {describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import MarkdownView from "@/components/island/MarkdownView.vue"
import {readMentionNames} from "@/domains/discord/adapters/mentions"

vi.mock("@/domains/discord/adapters/mentions", () => ({readMentionNames: vi.fn(), listServerChannels: vi.fn()}))

describe("MarkdownView", () => {
  it("draws the markdown it is given as its elements", () => {
    const wrapper = mount(MarkdownView, {props: {source: "# Hey\n\n- **one**\n- two"}})

    expect(wrapper.get(".markdown-view h1").text()).toBe("Hey")
    expect(wrapper.findAll(".markdown-view li")).toHaveLength(2)
    expect(wrapper.get("li strong").text()).toBe("one")
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
