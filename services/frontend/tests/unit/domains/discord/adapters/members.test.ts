import {describe, expect, it, vi} from "vitest"
import {listUnclaimedMembers, searchServerMembers} from "@/domains/discord/adapters/members"
import {listUnclaimedDiscordMembers, searchDiscordMembers} from "@/services/api"

vi.mock("@/services/api", () => ({searchDiscordMembers: vi.fn(), listUnclaimedDiscordMembers: vi.fn()}))

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

  it("lists everybody nobody has linked, or nothing where the api cannot ask", async () => {
    const anna = {id: "804", name: "Anna", username: "annie", avatar: "https://cdn/anna.png"}
    vi.mocked(listUnclaimedDiscordMembers).mockResolvedValue({data: [anna]} as never)
    expect(await listUnclaimedMembers()).toEqual([anna])

    vi.mocked(listUnclaimedDiscordMembers).mockResolvedValue({error: {status: 503}} as never)
    expect(await listUnclaimedMembers()).toBeNull()
  })
})

