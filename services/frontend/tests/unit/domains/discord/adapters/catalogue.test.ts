import {describe, expect, it, vi} from "vitest"
import {adoptMatches, listCatalogue, listMatches} from "@/domains/discord/adapters/catalogue"
import {adoptDiscordMatches, listCataloguedChannels, listDiscordMatches} from "@/services/api"
import {answer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", () => ({adoptDiscordMatches: vi.fn(), listCataloguedChannels: vi.fn(), listDiscordMatches: vi.fn()}))

describe("the catalogue adapter", () => {
  it("reads the channels and the matches, and nothing where the api cannot say", async () => {
    const channel = {id: "1", name: "sitecie", kind: "TEXT" as const, private: true, roleIds: ["900"]}
    vi.mocked(listCataloguedChannels).mockResolvedValue(answer(listCataloguedChannels, [channel]))
    vi.mocked(listDiscordMatches).mockResolvedValue(refusal(listDiscordMatches, null, 503))

    expect(await listCatalogue()).toEqual([channel])
    expect(await listMatches()).toEqual([])
  })

  it("links the confirmed matches and says how many, or why not", async () => {
    vi.mocked(adoptDiscordMatches).mockResolvedValue(answer(adoptDiscordMatches, {linked: 2}))
    expect(await adoptMatches(["COMMITTEE_MEMBERS:1", "TEAM_PLAYERS:2"])).toEqual({ok: true, saved: 2})
    expect(adoptDiscordMatches).toHaveBeenCalledWith({body: {keys: ["COMMITTEE_MEMBERS:1", "TEAM_PLAYERS:2"]}})

    vi.mocked(adoptDiscordMatches).mockResolvedValue(refusal(adoptDiscordMatches, {code: "DiscordUnreachable"}, 503))
    expect(await adoptMatches(["COMMITTEE_MEMBERS:1"])).toEqual({ok: false, reason: "Discord cannot be reached now; try again in a moment."})
  })
})
