import {describe, expect, it, vi} from "vitest"
import {listServerEmoji} from "@/domains/discord/adapters/emoji"
import {listDiscordEmojis} from "@/services/api"

vi.mock("@/services/api", () => ({listDiscordEmojis: vi.fn()}))

describe("listServerEmoji", () => {
  it("answers the server's own emoji, or nothing where the api cannot ask", async () => {
    vi.mocked(listDiscordEmojis).mockResolvedValue({data: [{id: "657", name: "ShellyStar", animated: false}]} as never)
    expect(await listServerEmoji()).toEqual([{id: "657", name: "ShellyStar", animated: false}])

    vi.mocked(listDiscordEmojis).mockResolvedValue({error: {status: 503}} as never)
    expect(await listServerEmoji()).toBeNull()
  })
})
