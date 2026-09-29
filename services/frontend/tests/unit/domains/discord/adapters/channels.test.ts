import {describe, expect, it, vi} from "vitest"
import {gameRoomUrl, listGameRooms} from "@/domains/discord/adapters/channels"
import {GameChannelCategory, listGameChannels} from "@/services/api"
import {answer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", () => ({listGameChannels: vi.fn(), GameChannelCategory: {GAMES: "GAMES", ESPORTS: "ESPORTS"}}))

describe("listGameRooms", () => {
  it("answers the games category's channels, or nothing where the api cannot ask", async () => {
    vi.mocked(listGameChannels).mockResolvedValue(answer(listGameChannels, [{id: "900", guildId: "324", name: "chess"}]))
    expect(await listGameRooms()).toEqual([{id: "900", guildId: "324", name: "chess"}])
    expect(listGameChannels).toHaveBeenLastCalledWith({query: {category: "GAMES"}})
    await listGameRooms(GameChannelCategory.ESPORTS)
    expect(listGameChannels).toHaveBeenLastCalledWith({query: {category: "ESPORTS"}})

    vi.mocked(listGameChannels).mockResolvedValue(refusal(listGameChannels, {status: 503}))
    expect(await listGameRooms()).toBeNull()
  })
})

describe("gameRoomUrl", () => {
  it("links into the channel in the Discord app", () => {
    expect(gameRoomUrl({id: "900", guildId: "324", name: "chess"})).toBe("https://discord.com/channels/324/900")
  })
})
