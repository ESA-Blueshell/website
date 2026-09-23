import {beforeEach, describe, expect, it, vi} from "vitest"
import {fitting, howFull, liveOf, readDiscordRooms} from "@/domains/discord/rooms"
import {readGuildCounts, readGuildWidget} from "@/domains/discord/adapters/widget"

vi.mock("@/domains/discord/adapters/widget", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  readGuildWidget: vi.fn(),
  readGuildCounts: vi.fn(),
}))

const WIDGET = {
  name: "Blueshell Esports",
  presence_count: 42,
  channels: [
    {id: "357", name: "AFK"},
    {id: "2", name: "Public Voice 2", position: 2},
    {id: "1", name: "Public Voice 1", position: 1},
    {id: "134", name: "➕ Create Public VC", position: 3},
    {id: "3", name: "Public Voice 3", position: 4},
  ],
  members: [
    {username: "Emma", channel_id: "1", avatar_url: "https://cdn.discordapp.com/widget-avatars/emma"},
    {username: "Viktor", channel_id: "2", avatar_url: ""},
    {username: "Mo", channel_id: "2", avatar_url: ""},
    {username: "Sleepy", channel_id: "357", avatar_url: ""},
    {username: "Idle"},
  ],
}

describe("the Discord's voice rooms", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("lists the fullest rooms first, the empty ones after, and never AFK or the room-making room", async () => {
    vi.mocked(readGuildWidget).mockResolvedValue(WIDGET as never)
    vi.mocked(readGuildCounts).mockResolvedValue({members: 1199, online: 269})

    expect(await readDiscordRooms()).toEqual({
      server: "Blueshell",
      online: 269,
      members: 1199,
      rooms: [
        {
          id: "2", name: "Public Voice 2", locked: false,
          people: [{name: "Viktor", avatar: undefined}, {name: "Mo", avatar: undefined}],
          href: "https://discord.com/channels/324285132133629963/2",
        },
        {
          id: "1", name: "Public Voice 1", locked: false,
          people: [{name: "Emma", avatar: "https://cdn.discordapp.com/widget-avatars/emma"}],
          href: "https://discord.com/channels/324285132133629963/1",
        },
        {id: "3", name: "Public Voice 3", locked: false, people: [], href: "https://discord.com/channels/324285132133629963/3"},
      ],
    })
  })

  it("counts from the widget alone where the invite would not say", async () => {
    vi.mocked(readGuildWidget).mockResolvedValue(WIDGET as never)
    vi.mocked(readGuildCounts).mockRejectedValue(new Error("refused"))

    const rooms = await readDiscordRooms()
    expect(rooms?.online).toBe(42)
    expect(rooms?.members).toBeUndefined()
  })

  it("answers nothing where the widget would not say", async () => {
    vi.mocked(readGuildWidget).mockRejectedValue(new Error("offline"))
    vi.mocked(readGuildCounts).mockResolvedValue({members: 1199, online: 269})

    expect(await readDiscordRooms()).toBeNull()
  })

  it("says who is online out of everybody, or online alone, or nothing", () => {
    expect(liveOf({server: "Blueshell", online: 269, members: 1199, rooms: []})).toBe("269/1199 online")
    expect(liveOf({server: "Blueshell", online: 7, rooms: []})).toBe("7 online")
    expect(liveOf({server: "Blueshell", rooms: []})).toBe("")
    expect(liveOf(null)).toBe("")
  })

  it("says how full a room is", () => {
    const people = [{name: "Mo"}, {name: "Ana"}]
    expect(howFull({id: "1", name: "Lounge", locked: false, people, href: ""})).toBe("2 in voice")
    expect(howFull({id: "1", name: "Lounge", locked: true, people, href: ""})).toBe("2 inside")
    expect(howFull({id: "1", name: "Lounge", locked: false, people: [], href: ""})).toBe("empty")
  })
})

describe("how many people fit on a line", () => {
  const more = () => 60

  it("fits everybody where they all fit, with no label needed", () => {
    expect(fitting([50, 50, 50], 8, 166, more)).toBe(3)
    expect(fitting([], 8, 100, more)).toBe(0)
  })

  it("fits as many as leave room for the label, and always one", () => {
    // 50 + 8 + 50 + 8 + 60 = 176 fits in 180; a third would not.
    expect(fitting([50, 50, 50, 50], 8, 180, more)).toBe(2)
    expect(fitting([300, 50], 8, 100, more)).toBe(1)
  })
})
