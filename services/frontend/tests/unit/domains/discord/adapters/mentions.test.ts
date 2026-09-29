import {describe, expect, it, vi} from "vitest"
import {listServerChannels, readMentionNames} from "@/domains/discord/adapters/mentions"
import {listDiscordChannels, readDiscordMentions} from "@/services/api"
import {answer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", () => ({listDiscordChannels: vi.fn(), readDiscordMentions: vi.fn()}))

describe("the mentions adapter", () => {
  it("reads the names asked for, or nothing where the api cannot ask", async () => {
    const named = {users: [{id: "11", name: "Anna"}], roles: [], channels: []}
    vi.mocked(readDiscordMentions).mockResolvedValue(answer(readDiscordMentions, named))
    expect(await readMentionNames({users: ["11"], roles: [], channels: []})).toEqual(named)
    expect(readDiscordMentions).toHaveBeenCalledWith({query: {users: ["11"], roles: [], channels: []}})

    vi.mocked(readDiscordMentions).mockResolvedValue(refusal(readDiscordMentions, {status: 503}))
    expect(await readMentionNames({users: ["11"], roles: [], channels: []})).toBeNull()
  })

  it("lists the channels everybody can see, or nothing where the api cannot ask", async () => {
    vi.mocked(listDiscordChannels).mockResolvedValue(answer(listDiscordChannels, [{id: "1", name: "general"}]))
    expect(await listServerChannels()).toEqual([{id: "1", name: "general"}])

    vi.mocked(listDiscordChannels).mockResolvedValue(refusal(listDiscordChannels, {status: 503}))
    expect(await listServerChannels()).toBeNull()
  })
})
