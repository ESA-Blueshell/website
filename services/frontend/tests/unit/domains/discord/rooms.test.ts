import {beforeEach, describe, expect, it, vi} from "vitest"
import {howFull, readDiscordRooms, whoIsIn} from "@/domains/discord/rooms"
import {readGuildWidget} from "@/domains/discord/adapters/widget"

vi.mock("@/domains/discord/adapters/widget", () => ({readGuildWidget: vi.fn()}))

const room = (people: string[], locked = false) => ({id: "1", name: "#general-voice", locked, people})

describe("the Discord's voice rooms", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("reads the public widget into rooms in Discord's order, each with who is in it", async () => {
    vi.mocked(readGuildWidget).mockResolvedValue({
      name: "ESA Blueshell",
      presence_count: 42,
      channels: [{id: "2", name: "#valorant-voice", position: 2}, {id: "1", name: "#general-voice", position: 1}],
      members: [{username: "Emma", channel_id: "1"}, {username: "Viktor", channel_id: "2"}, {username: "Idle"}],
    } as never)

    expect(await readDiscordRooms()).toEqual({
      server: "ESA Blueshell",
      online: 42,
      rooms: [
        {id: "1", name: "#general-voice", locked: false, people: ["Emma"]},
        {id: "2", name: "#valorant-voice", locked: false, people: ["Viktor"]},
      ],
    })
  })

  it("names the server itself where the widget does not, and keeps Discord's order where none is given", async () => {
    vi.mocked(readGuildWidget).mockResolvedValue({
      name: "", presence_count: 0, members: [],
      channels: [{id: "1", name: "a"}, {id: "2", name: "b"}],
    } as never)

    const said = await readDiscordRooms()

    expect(said?.server).toBe("ESA Blueshell")
    expect(said?.rooms.map(one => one.name)).toEqual(["a", "b"])
  })

  it("answers nothing where Discord would not say, so the band shows the invite alone", async () => {
    vi.mocked(readGuildWidget).mockRejectedValue(new Error("widget disabled"))

    expect(await readDiscordRooms()).toBeNull()
  })

  it("says who is in a room in a line, and how full it is", () => {
    expect(whoIsIn(room([]))).toBe("nobody yet, start it")
    expect(whoIsIn(room(["Emma"]))).toBe("Emma")
    expect(whoIsIn(room(["Emma", "Viktor"]))).toBe("Emma and Viktor")
    expect(whoIsIn(room(["Emma", "Viktor", "A", "B", "C"]))).toBe("Emma, Viktor and 3 more")
    expect(howFull(room([]))).toBe("empty")
    expect(howFull(room(["Emma", "Viktor"]))).toBe("2 in voice")
  })

  it("marks a members-only room as such, quiet or inside", () => {
    expect(whoIsIn(room([], true))).toBe("members only · quiet right now")
    expect(whoIsIn(room(["Emma", "Viktor", "Mo"], true))).toBe("members only · Emma, Viktor and 1 more")
    expect(howFull(room(["Emma", "Viktor", "Mo"], true))).toBe("3 inside")
  })
})
