import {describe, expect, it, vi} from "vitest"
import {searchServerMembers} from "@/domains/discord/adapters/members"
import {searchDiscordMembers} from "@/services/api"

vi.mock("@/services/api", () => ({searchDiscordMembers: vi.fn()}))

describe("searchServerMembers", () => {
  it("answers the members the bot found", async () => {
    const nelly = {id: "803", name: "Nelly B", username: "nelly", avatar: "https://cdn/nelly.png"}
    vi.mocked(searchDiscordMembers).mockResolvedValue({data: [nelly]} as never)

    expect(await searchServerMembers("nel")).toEqual([nelly])
    expect(searchDiscordMembers).toHaveBeenCalledWith({query: {query: "nel"}})
  })

  it("answers nothing where the api cannot ask Discord", async () => {
    vi.mocked(searchDiscordMembers).mockResolvedValue({error: {status: 503}} as never)
    expect(await searchServerMembers("nel")).toBeNull()

    vi.mocked(searchDiscordMembers).mockResolvedValue({data: undefined} as never)
    expect(await searchServerMembers("nel")).toBeNull()
  })
})
