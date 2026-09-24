import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import DiscordBand from "@/domains/discord/island/DiscordBand.vue"

const {mockRead, mockStop, watcher, mockMine, session} = vi.hoisted(() => ({
  mockRead: vi.fn(),
  mockStop: vi.fn(),
  watcher: {tell: (_rooms: unknown) => {}},
  mockMine: vi.fn(),
  session: {getters: {isLoggedIn: false}},
}))

vi.mock("@/plugins/store", () => ({default: session}))
vi.mock("@/domains/discord/adapters/live", () => ({readMyRooms: mockMine}))

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
    session.getters.isLoggedIn = false
    mockMine.mockResolvedValue({linked: true, joinable: ["3"]})
  })

  it("invites a look in, with the widget's Join server as the band's only way in", async () => {
    mockRead.mockResolvedValue(FIXTURE)
    const wrapper = await mountBand()

    expect(wrapper.find("h2").text()).toBe("Come check the vibes")
    expect(wrapper.find(".band-head__way").exists()).toBe(false)
    expect(wrapper.get("[data-testid=home-discord-join]").attributes()).toMatchObject({href: "http://localhost:3000/api/discord/invite/welcome", target: "_blank"})
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

  it("draws Discord's voice glyph for a room, locked for a members-only one, green since only occupied rooms are listed", async () => {
    mockRead.mockResolvedValue(FIXTURE)
    const wrapper = await mountBand()

    const glyph = (id: string) => wrapper.get(`[data-testid=home-discord-room-${id}] .widget__glyph`)
    expect(glyph("1").classes()).toContain("widget__glyph--live")
    expect(glyph("3").classes()).toContain("widget__glyph--live")
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

  it("says nobody is in voice where no room has anybody in it, and lists no room", async () => {
    mockRead.mockResolvedValue({server: "Blueshell", online: 3, rooms: []})
    const wrapper = await mountBand()

    expect(wrapper.find(".widget__rooms").exists()).toBe(false)
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

  it("lists a room-maker as the way to start a room, grey until somebody is in it", async () => {
    mockRead.mockResolvedValue({...FIXTURE, rooms: [...FIXTURE.rooms, {id: "5", name: "➕ Create Public VC", locked: false, people: [], href: "h5", startsRoom: true}]})
    const wrapper = await mountBand()

    const maker = wrapper.get("[data-testid=home-discord-room-5]")
    expect(maker.text()).toContain("join to start a room of your own")
    expect(maker.text()).toContain("new room")
    expect(maker.get(".widget__glyph").classes()).not.toContain("widget__glyph--live")
    expect(wrapper.find("[data-testid=home-discord-quiet]").exists()).toBe(false)
  })

  it("says nobody is in voice where only room-makers are listed", async () => {
    mockRead.mockResolvedValue({server: "Blueshell", online: 3, rooms: [{id: "5", name: "➕ Create Public VC", locked: false, people: [], href: "h5", startsRoom: true}]})
    const wrapper = await mountBand()

    expect(wrapper.get("[data-testid=home-discord-quiet]").text()).toBe("Nobody is in voice right now.")
  })

  it("opens the rooms a logged-in member's own Discord member may join, asking again when the locked rooms change", async () => {
    session.getters.isLoggedIn = true
    mockRead.mockResolvedValue(FIXTURE)
    const wrapper = await mountBand()

    const lounge = wrapper.get("[data-testid=home-discord-room-3]")
    expect(mockMine).toHaveBeenCalledTimes(1)
    expect(lounge.classes()).not.toContain("widget__room--locked")
    expect(lounge.get(".widget__room-name").text()).toBe("Members lounge")

    watcher.tell({...FIXTURE, rooms: [...FIXTURE.rooms, {id: "9", name: "Board", locked: true, people: [{name: "Chair"}], href: "h9", startsRoom: false}]})
    await flushPromises()
    expect(mockMine).toHaveBeenCalledTimes(2)
    expect(wrapper.get("[data-testid=home-discord-room-9]").classes()).toContain("widget__room--locked")
  })

  it("keeps every lock for a visitor, and for an account with no member linked", async () => {
    mockRead.mockResolvedValue(FIXTURE)
    const visitor = await mountBand()
    expect(mockMine).not.toHaveBeenCalled()
    expect(visitor.get("[data-testid=home-discord-room-3]").classes()).toContain("widget__room--locked")

    session.getters.isLoggedIn = true
    mockMine.mockResolvedValue({linked: false, joinable: []})
    const unlinked = await mountBand()
    expect(unlinked.get("[data-testid=home-discord-room-3]").classes()).toContain("widget__room--locked")
  })

  it("asks nothing where no room is locked, and keeps the locks where the api cannot say", async () => {
    session.getters.isLoggedIn = true
    mockRead.mockResolvedValue({...FIXTURE, rooms: FIXTURE.rooms.filter(room => !room.locked)})
    await mountBand()
    expect(mockMine).not.toHaveBeenCalled()

    mockMine.mockResolvedValue(null)
    mockRead.mockResolvedValue(FIXTURE)
    const offline = await mountBand()
    expect(offline.get("[data-testid=home-discord-room-3]").classes()).toContain("widget__room--locked")
  })
})

