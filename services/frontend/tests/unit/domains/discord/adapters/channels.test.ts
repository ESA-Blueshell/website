import {describe, expect, it, vi} from "vitest"
import {gameRoomUrl, listGameRooms} from "@/domains/discord/adapters/channels"
import {listGameChannels} from "@/services/api"

vi.mock("@/services/api", () => ({listGameChannels: vi.fn()}))

describe("listGameRooms", () => {
  it("answers the games category's channels, or nothing where the api cannot ask", async () => {
    vi.mocked(listGameChannels).mockResolvedValue({data: [{id: "900", guildId: "324", name: "chess"}]} as never)
    expect(await listGameRooms()).toEqual([{id: "900", guildId: "324", name: "chess"}])

    vi.mocked(listGameChannels).mockResolvedValue({error: {status: 503}} as never)
    expect(await listGameRooms()).toBeNull()
  })
})

describe("gameRoomUrl", () => {
  it("links into the channel in the Discord app", () => {
    expect(gameRoomUrl({id: "900", guildId: "324", name: "chess"})).toBe("https://discord.com/channels/324/900")
  })
})
