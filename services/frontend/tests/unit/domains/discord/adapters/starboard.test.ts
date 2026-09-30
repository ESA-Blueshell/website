import {describe, expect, it, vi} from "vitest"
import {readStarboard} from "@/domains/discord/adapters/starboard"
import {readStarboard as readStarboardEntries} from "@/services/api"
import {answer, emptyAnswer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", () => ({readStarboard: vi.fn()}))

describe("readStarboard", () => {
  it("answers the starred messages the api read through the bot", async () => {
    const entries = [{id: "9", authorName: "Joris", stars: 12, channel: "general", href: "https://discord.com/channels/1/2/9", postedAt: "2026-09-23T08:22:05Z"}]
    vi.mocked(readStarboardEntries).mockResolvedValue(answer(readStarboardEntries, entries))

    expect(await readStarboard()).toEqual(entries)
  })

  it("answers nothing where the bot is not set up or Discord has not answered", async () => {
    vi.mocked(readStarboardEntries).mockResolvedValue(refusal(readStarboardEntries, {status: 503}))
    expect(await readStarboard()).toBeNull()

    vi.mocked(readStarboardEntries).mockResolvedValue(emptyAnswer(readStarboardEntries))
    expect(await readStarboard()).toBeNull()
  })
})
