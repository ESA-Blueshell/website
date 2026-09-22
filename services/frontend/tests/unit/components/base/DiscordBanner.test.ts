import {beforeEach, describe, expect, it, vi} from "vitest"
import DiscordBanner from "@/components/base/DiscordBanner.vue"
import {mountInApp, settle} from "../../helpers/testUtils"

const {mockReadGuildWidget} = vi.hoisted(() => ({mockReadGuildWidget: vi.fn()}))

vi.mock("@/domains/discord", () => ({readGuildWidget: mockReadGuildWidget}))

const widget = {
  presence_count: 3,
  members: [
    {username: "roos", status: "online", channel_id: "12"},
    {username: "lena", status: "idle", channel_id: "12"},
    {username: "sam", status: "dnd"},
  ],
  channels: [
    {id: "12", name: "General"},
    {id: "13", name: "Empty"},
  ],
}

describe("DiscordBanner", () => {
  it("paints itself in the theme's wallpaper, and in nothing where the theme names none", async () => {
    const wrapper = mountInApp(DiscordBanner, {
      global: {stubs: {DiscordUser: true}},
    })
    await settle()

    expect(wrapper.get("div").attributes("style")).toBeDefined()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    mockReadGuildWidget.mockResolvedValue(structuredClone(widget))
  })

  it("says how many people are on the server", async () => {
    const wrapper = mountInApp(DiscordBanner, {global: {stubs: {DiscordUser: true}}})
    await settle()

    expect(wrapper.text()).toContain("3 online now")
  })

  it("names only the channels somebody is actually in", async () => {
    const wrapper = mountInApp(DiscordBanner, {global: {stubs: {DiscordUser: true}}})
    await settle()

    const text = wrapper.text()
    expect(text).toContain("General")
    expect(text).not.toContain("Empty")
  })

  it("shows the members alone when nobody is in a voice channel", async () => {
    mockReadGuildWidget.mockResolvedValue({
      presence_count: 1,
      members: [{username: "sam", status: "dnd"}],
      channels: [{id: "13", name: "Empty"}],
    })

    const wrapper = mountInApp(DiscordBanner, {global: {stubs: {DiscordUser: true}}})
    await settle()

    expect(wrapper.text()).not.toContain("Empty")
    expect(wrapper.text()).toContain("1 online now")
  })

  it("reads a widget that names neither members nor channels", async () => {
    mockReadGuildWidget.mockResolvedValue({presence_count: 0, members: [], channels: []})

    const wrapper = mountInApp(DiscordBanner, {global: {stubs: {DiscordUser: true}}})
    await settle()

    expect(wrapper.text()).toContain("0 online now")
  })

  // Discord's widget stops at 100 members, so the rest are counted rather than named.
  it("counts the members past the hundred the widget names", async () => {
    mockReadGuildWidget.mockResolvedValue({
      presence_count: 140,
      members: Array.from({length: 100}, (_one, i) => ({username: `member-${i}`, status: "online"})),
      channels: [],
    })

    const wrapper = mountInApp(DiscordBanner, {
      global: {
        stubs: {
          DiscordUser: {props: ["customText"], template: "<span>{{ customText }}</span>"},
        },
      },
    })
    await settle()

    expect(wrapper.text()).toContain("+40 more")
  })

  // The server being unreachable is not the association having no server, so the banner keeps
  // its invitation and drops the live half.
  it("keeps the invitation when Discord would not say what the server is doing", async () => {
    mockReadGuildWidget.mockRejectedValue(new Error("offline"))
    const logged = vi.spyOn(console, "error").mockImplementation(() => {})

    const wrapper = mountInApp(DiscordBanner, {global: {stubs: {DiscordUser: true}}})
    await settle()

    expect(logged).toHaveBeenCalled()
    expect(wrapper.text()).toContain("Join our Discord")
    expect(wrapper.text()).not.toContain("online now")
  })
})
