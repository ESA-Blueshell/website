import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import DiscordBand from "@/domains/discord/island/DiscordBand.vue"

const {mockRead, mockStop, watcher} = vi.hoisted(() => ({
  mockRead: vi.fn(),
  mockStop: vi.fn(),
  watcher: {tell: (_rooms: unknown) => {}},
}))

/* The watch hands over what mockRead answers first; a test tells it more through watcher.tell. */
vi.mock("@/domains/discord/rooms", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  watchDiscordRooms: (onRooms: (rooms: unknown) => void) => {
    watcher.tell = onRooms
    void Promise.resolve(mockRead()).then(onRooms)
    return mockStop
  },
}))

/* The shape #1344's endpoint answers with, members-only rooms and the member total included. */
const FIXTURE = {
  server: "Blueshell",
  online: 269,
  members: 1199,
  rooms: [
    {id: "1", name: "Public Voice 1", locked: false, people: [{name: "Emma", avatar: "/emma.png"}, {name: "Viktor"}], href: "https://discord.com/channels/g/1"},
    {id: "3", name: "Members lounge", locked: true, people: [{name: "Mo"}, {name: "Ana"}, {name: "Kai"}], href: "https://discord.com/channels/g/3"},
  ],
}

const mountBand = async () => {
  const wrapper = mount(DiscordBand)
  await flushPromises()
  return wrapper
}

describe("DiscordBand", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("invites a look in, with the widget's Join server as the band's only way in", async () => {
    mockRead.mockResolvedValue(FIXTURE)
    const wrapper = await mountBand()

    expect(wrapper.find("h2").text()).toBe("Come check the vibes")
    expect(wrapper.find(".band-head__way").exists()).toBe(false)
    expect(wrapper.get("[data-testid=home-discord-join]").attributes()).toMatchObject({href: "https://discord.gg/23YMFQy", target: "_blank"})
    expect(wrapper.get(".widget__server").text()).toBe("Blueshell")
  })

  it("counts online out of everybody, and joins each room in Discord itself", async () => {
    mockRead.mockResolvedValue(FIXTURE)
    const wrapper = await mountBand()

    expect(wrapper.get("[data-testid=home-discord-live]").text()).toBe("269/1199 online")
    const open = wrapper.get("[data-testid=home-discord-room-1]")
    expect(open.text()).toContain("Emma")
    expect(open.text()).toContain("Viktor")
    expect(open.get("img").attributes("src")).toBe("/emma.png")
    expect(open.get(".widget__join").attributes("href")).toBe("https://discord.com/channels/g/1")
  })

  it("draws Discord's voice glyph for a room, locked for a members-only one, green while somebody is in it", async () => {
    mockRead.mockResolvedValue({...FIXTURE, rooms: [...FIXTURE.rooms, {id: "4", name: "Quiet", locked: false, people: [], href: "h"}]})
    const wrapper = await mountBand()

    const glyph = (id: string) => wrapper.get(`[data-testid=home-discord-room-${id}] .widget__glyph`)
    expect(glyph("1").classes()).toContain("widget__glyph--live")
    expect(glyph("4").classes()).not.toContain("widget__glyph--live")
    expect(glyph("1").attributes("style")).toContain("voice.webp")
    expect(glyph("3").attributes("style")).toContain("voice-locked.webp")
  })

  it("marks a members-only room, and says why its way in asks for membership, with no footnote explaining it", async () => {
    mockRead.mockResolvedValue(FIXTURE)
    const wrapper = await mountBand()

    const locked = wrapper.get("[data-testid=home-discord-room-3]")
    expect(locked.classes()).toContain("widget__room--locked")
    expect(locked.get(".widget__room-name").text()).toBe("Members lounge · members only")
    expect(locked.get(".widget__join").attributes("aria-label")).toBe("Join Members lounge, which opens with membership")
    expect(wrapper.text()).not.toContain("Everything else is open")
  })

  it("says nobody is in voice where every room is empty, and offers the empty rooms to start", async () => {
    mockRead.mockResolvedValue({server: "Blueshell", online: 3, rooms: [{id: "4", name: "Public Voice 1", locked: false, people: [], href: "h"}]})
    const wrapper = await mountBand()

    expect(wrapper.get("[data-testid=home-discord-room-4]").text()).toContain("nobody yet, start it")
    expect(wrapper.get("[data-testid=home-discord-room-4]").text()).toContain("empty")
    expect(wrapper.get("[data-testid=home-discord-quiet]").text()).toBe("Nobody is in voice right now.")
    expect(wrapper.get("[data-testid=home-discord-live]").text()).toBe("3 online")
  })

  it("is the invite alone where Discord says nothing", async () => {
    mockRead.mockResolvedValue(null)
    const wrapper = await mountBand()

    const widget = wrapper.get("[data-testid=home-discord-widget]")
    expect(widget.classes()).toContain("widget--invite")
    expect(widget.find("[data-testid=home-discord-quiet]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=home-discord-live]").exists()).toBe(false)
    expect(widget.text()).toContain("Blueshell")
  })

  it("follows the server as it changes, keeps the last answer over nothing, and stops following when it goes", async () => {
    mockRead.mockResolvedValue(FIXTURE)
    const wrapper = await mountBand()

    watcher.tell({...FIXTURE, online: 300})
    await flushPromises()
    expect(wrapper.get("[data-testid=home-discord-live]").text()).toBe("300/1199 online")

    watcher.tell(null)
    await flushPromises()
    expect(wrapper.get("[data-testid=home-discord-live]").text()).toBe("300/1199 online")

    wrapper.unmount()
    expect(mockStop).toHaveBeenCalledOnce()
  })
})
