import {describe, expect, it, vi} from "vitest"
import {makeGameChannel} from "@/domains/discord/adapters/channelAccess"
import {createGameChannel, GameChannelCategory} from "@/services/api"
import {answer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  createGameChannel: vi.fn(),
}))

describe("making a game's channel", () => {
  it("answers the channel made, and says when Discord cannot be reached", async () => {
    const made = {id: "1", guildId: "99", name: "valo"}
    vi.mocked(createGameChannel).mockResolvedValueOnce(answer(createGameChannel, made))
    await expect(makeGameChannel("valo", GameChannelCategory.GAMES)).resolves.toEqual({ok: true, saved: made})
    expect(createGameChannel).toHaveBeenCalledWith({body: {name: "valo", category: "GAMES"}})

    vi.mocked(createGameChannel).mockResolvedValueOnce(refusal(createGameChannel, {code: "DiscordUnreachable"}, 503))
    await expect(makeGameChannel("valo", GameChannelCategory.GAMES)).resolves.toEqual({ok: false, reason: "Discord cannot be reached now; try again in a moment."})
  })
})
