import {describe, expect, it, vi} from "vitest"
import {readLiveServer} from "@/domains/discord/adapters/live"
import {readDiscordLive} from "@/services/api"

vi.mock("@/services/api", () => ({readDiscordLive: vi.fn()}))

describe("readLiveServer", () => {
  it("answers what the api read through the bot", async () => {
    const live = {server: "Blueshell", online: 3, members: 40, rooms: []}
    vi.mocked(readDiscordLive).mockResolvedValue({data: live} as never)

    expect(await readLiveServer()).toEqual(live)
  })

  it("answers nothing where the bot is not set up or not connected", async () => {
    vi.mocked(readDiscordLive).mockResolvedValue({error: {status: 503}} as never)
    expect(await readLiveServer()).toBeNull()

    vi.mocked(readDiscordLive).mockResolvedValue({data: undefined} as never)
    expect(await readLiveServer()).toBeNull()
  })
})
