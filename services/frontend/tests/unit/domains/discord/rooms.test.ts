import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {fitting, howFull, liveOf, unlockedFor, POLL_MS, readDiscordRooms, RETRY_MAX_MS, RETRY_MS, watchDiscordRooms} from "@/domains/discord/rooms"
import {readGuildCounts, readGuildWidget} from "@/domains/discord/adapters/widget"
import {readLiveServer} from "@/domains/discord/adapters/live"
import {openLiveSocket} from "@/domains/discord/adapters/liveSocket"

vi.mock("@/domains/discord/adapters/live", () => ({readLiveServer: vi.fn()}))
vi.mock("@/domains/discord/adapters/liveSocket", () => ({openLiveSocket: vi.fn()}))
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
    // No bot unless a test says so: the widget answers.
    vi.mocked(readLiveServer).mockResolvedValue(null)
  })

  it("lists the rooms somebody is in, the fullest first, then the room-maker, and never another empty room or AFK", async () => {
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
          startsRoom: false,
        },
        {
          id: "1", name: "Public Voice 1", locked: false,
          people: [{name: "Emma", avatar: "https://cdn.discordapp.com/widget-avatars/emma"}],
          href: "https://discord.com/channels/324285132133629963/1",
          startsRoom: false,
        },
        {
          id: "134", name: "➕ Create Public VC", locked: false, people: [],
          href: "https://discord.com/channels/324285132133629963/134",
          startsRoom: true,
        },
      ],
    })
  })

  it("reads the api first where the bot is set up, locked rooms and room-makers and all, in the same order", async () => {
    vi.mocked(readLiveServer).mockResolvedValue({
      server: "Blueshell Esports",
      online: 270,
      members: 1199,
      rooms: [
        {id: "5", name: "➕ Create Public VC", locked: false, href: "h5", people: []},
        {id: "6", name: "Public Voice 1", locked: false, href: "h6", people: [{name: "Emma", avatar: "https://cdn/emma.png"}]},
        {id: "7", name: "Members lounge", locked: true, href: "h7", people: [{name: "Mo", avatar: null}, {name: "Ana", avatar: null}]},
        {id: "8", name: "Quiet lounge", locked: true, href: "h8", people: []},
      ],
    })

    expect(await readDiscordRooms()).toEqual({
      server: "Blueshell",
      online: 270,
      members: 1199,
      rooms: [
        {id: "7", name: "Members lounge", locked: true, href: "h7", people: [{name: "Mo", avatar: undefined}, {name: "Ana", avatar: undefined}], startsRoom: false},
        {id: "6", name: "Public Voice 1", locked: false, href: "h6", people: [{name: "Emma", avatar: "https://cdn/emma.png"}], startsRoom: false},
        {id: "5", name: "➕ Create Public VC", locked: false, href: "h5", people: [], startsRoom: true},
      ],
    })
    expect(readGuildWidget).not.toHaveBeenCalled()
  })

  it("leaves counts the api does not have out, and falls back to the widget where the api fails", async () => {
    vi.mocked(readLiveServer).mockResolvedValueOnce({server: "B", online: null, members: null, rooms: []})
    const bare = await readDiscordRooms()
    expect(bare?.online).toBeUndefined()
    expect(bare?.members).toBeUndefined()

    vi.mocked(readLiveServer).mockRejectedValueOnce(new Error("offline"))
    vi.mocked(readGuildWidget).mockResolvedValue(WIDGET as never)
    vi.mocked(readGuildCounts).mockResolvedValue({members: 1199, online: 269})
    expect((await readDiscordRooms())?.members).toBe(1199)
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

  it("opens a room locked to everybody where the viewer's own member may join it, and no other", () => {
    const rooms = {server: "Blueshell", rooms: [
      {id: "7", name: "Members lounge", locked: true, people: [], href: "h7", startsRoom: false},
      {id: "8", name: "Board", locked: true, people: [], href: "h8", startsRoom: false},
      {id: "6", name: "Public", locked: false, people: [], href: "h6", startsRoom: false},
    ]}

    expect(unlockedFor(rooms, new Set(["7", "6"])).rooms.map(room => room.locked)).toEqual([false, true, false])
  })

  it("says how full a room is", () => {
    const people = [{name: "Mo"}, {name: "Ana"}]
    expect(howFull({id: "1", name: "Lounge", locked: false, people, href: "", startsRoom: false})).toBe("2 in voice")
    expect(howFull({id: "1", name: "Lounge", locked: true, people, href: "", startsRoom: false})).toBe("2 inside")
    expect(howFull({id: "5", name: "➕ Create Public VC", locked: false, people: [], href: "", startsRoom: true})).toBe("new room")
  })
})

describe("following the Discord server", () => {
  const LIVE = {
    server: "Blueshell Esports",
    online: 269,
    members: 1199,
    rooms: [{id: "3", name: "Members lounge", locked: true, href: "https://discord.com/channels/324/3", people: [{name: "Mo", avatar: null}]}],
  }

  /* Each socket the watch opens, with the two ways the api talks back and whether it was closed. */
  let sockets: {live: (live: typeof LIVE) => void, gone: () => void, close: ReturnType<typeof vi.fn>}[]
  /* Every watch a test starts, stopped after it so none keeps listening to the document. */
  let stops: (() => void)[]
  const watch = (onRooms: Parameters<typeof watchDiscordRooms>[0]) => {
    const stop = watchDiscordRooms(onRooms)
    stops.push(stop)
    return stop
  }

  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    sockets = []
    stops = []
    vi.mocked(openLiveSocket).mockImplementation((live, gone) => {
      const close = vi.fn()
      sockets.push({live: live as never, gone, close})
      return close
    })
    vi.mocked(readLiveServer).mockResolvedValue(null)
    vi.mocked(readGuildWidget).mockResolvedValue(WIDGET as never)
    vi.mocked(readGuildCounts).mockResolvedValue({members: 1199, online: 269})
    vi.spyOn(document, "visibilityState", "get").mockReturnValue("visible")
  })

  afterEach(() => {
    stops.forEach(stop => stop())
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it("hands on every server the socket pushes, and asks nothing while it is open", async () => {
    const heard = vi.fn()
    const stop = watch(heard)

    sockets[0].live(LIVE)
    sockets[0].live({...LIVE, online: 270})
    await vi.advanceTimersByTimeAsync(POLL_MS * 3)

    expect(heard.mock.calls.map(([rooms]) => rooms.online)).toEqual([269, 270])
    expect(heard.mock.calls[0][0]).toMatchObject({server: "Blueshell", rooms: [{name: "Members lounge", locked: true, people: [{name: "Mo"}]}]})
    expect(readGuildWidget).not.toHaveBeenCalled()
    stop()
    expect(sockets[0].close).toHaveBeenCalledOnce()
  })

  it("drops a room the moment its last person leaves, or Discord deletes it", async () => {
    const heard = vi.fn()
    watch(heard)

    sockets[0].live(LIVE)
    sockets[0].live({...LIVE, rooms: [{...LIVE.rooms[0], people: []}]})
    sockets[0].live({...LIVE, rooms: []})

    expect(heard.mock.calls.map(([rooms]) => rooms.rooms.length)).toEqual([1, 0, 0])
  })

  it("asks every minute while the socket is down, and tries it again after a wait that doubles", async () => {
    const heard = vi.fn()
    watch(heard)

    sockets[0].gone()
    await vi.advanceTimersByTimeAsync(0)
    expect(heard).toHaveBeenCalledOnce()
    expect(heard.mock.calls[0][0].online).toBe(269)

    await vi.advanceTimersByTimeAsync(RETRY_MS)
    expect(sockets).toHaveLength(2)
    sockets[1].gone()
    await vi.advanceTimersByTimeAsync(RETRY_MS * 2 - 1)
    expect(sockets).toHaveLength(2)
    await vi.advanceTimersByTimeAsync(1)
    expect(sockets).toHaveLength(3)

    await vi.advanceTimersByTimeAsync(POLL_MS - RETRY_MS * 3)
    expect(heard).toHaveBeenCalledTimes(2)

    // Back on the socket: the asking stops, and the next failure waits the shortest time again.
    sockets[2].live(LIVE)
    await vi.advanceTimersByTimeAsync(POLL_MS * 2)
    expect(heard).toHaveBeenCalledTimes(3)
    sockets[2].gone()
    await vi.advanceTimersByTimeAsync(RETRY_MS)
    expect(sockets).toHaveLength(4)
  })

  it("never waits longer than the longest wait", async () => {
    watch(vi.fn())
    for (let tries = 0; tries < 12; tries++) {
      sockets.at(-1)!.gone()
      await vi.advanceTimersByTimeAsync(RETRY_MAX_MS)
    }

    expect(sockets).toHaveLength(13)
  })

  it("holds no socket and asks nothing while the page is hidden, and follows again once seen", async () => {
    vi.spyOn(document, "visibilityState", "get").mockReturnValue("hidden")
    const heard = vi.fn()
    const stop = watch(heard)
    expect(sockets).toHaveLength(0)

    vi.spyOn(document, "visibilityState", "get").mockReturnValue("visible")
    document.dispatchEvent(new Event("visibilitychange"))
    document.dispatchEvent(new Event("visibilitychange"))
    expect(sockets).toHaveLength(1)

    sockets[0].gone()
    vi.spyOn(document, "visibilityState", "get").mockReturnValue("hidden")
    document.dispatchEvent(new Event("visibilitychange"))
    await vi.advanceTimersByTimeAsync(RETRY_MAX_MS)
    expect(sockets).toHaveLength(1)
    expect(heard).toHaveBeenCalledOnce()

    stop()
    vi.spyOn(document, "visibilityState", "get").mockReturnValue("visible")
    document.dispatchEvent(new Event("visibilitychange"))
    expect(sockets).toHaveLength(1)
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
