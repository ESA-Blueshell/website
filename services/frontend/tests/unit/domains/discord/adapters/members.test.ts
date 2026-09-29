import {describe, expect, it, vi} from "vitest"
import {listUnclaimedMembers, searchServerMembers} from "@/domains/discord/adapters/members"
import {listUnclaimedDiscordMembers, searchDiscordMembers} from "@/services/api"
import {answer, emptyAnswer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", () => ({searchDiscordMembers: vi.fn(), listUnclaimedDiscordMembers: vi.fn()}))

describe("searchServerMembers", () => {
  it("answers the members the bot found", async () => {
    const nelly = {id: "803", name: "Nelly B", username: "nelly", avatar: "https://cdn/nelly.png"}
    vi.mocked(searchDiscordMembers).mockResolvedValue(answer(searchDiscordMembers, [nelly]))

    expect(await searchServerMembers("nel")).toEqual([nelly])
    expect(searchDiscordMembers).toHaveBeenCalledWith({query: {query: "nel"}})
  })

  it("answers nothing where the api cannot ask Discord", async () => {
    vi.mocked(searchDiscordMembers).mockResolvedValue(refusal(searchDiscordMembers, {status: 503}))
    expect(await searchServerMembers("nel")).toBeNull()

    vi.mocked(searchDiscordMembers).mockResolvedValue(emptyAnswer(searchDiscordMembers))
    expect(await searchServerMembers("nel")).toBeNull()
  })

  it("lists everybody nobody has linked, or nothing where the api cannot ask", async () => {
    const anna = {id: "804", name: "Anna", username: "annie", avatar: "https://cdn/anna.png"}
    vi.mocked(listUnclaimedDiscordMembers).mockResolvedValue(answer(listUnclaimedDiscordMembers, [anna]))
    expect(await listUnclaimedMembers()).toEqual([anna])

    vi.mocked(listUnclaimedDiscordMembers).mockResolvedValue(refusal(listUnclaimedDiscordMembers, {status: 503}))
    expect(await listUnclaimedMembers()).toBeNull()
  })
})

