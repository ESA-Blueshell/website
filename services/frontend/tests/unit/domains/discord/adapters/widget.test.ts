import {describe, expect, it, vi} from "vitest"
import axios from "axios"
import {readGuildCounts, readGuildWidget, voiceRoomUrl} from "@/domains/discord/adapters/widget"

vi.mock("axios", () => ({default: {get: vi.fn()}}))

describe("readGuildWidget", () => {
  it("reads the association's own guild", async () => {
    vi.mocked(axios.get).mockResolvedValue({data: {name: "Blueshell", members: [], channels: []}})

    await expect(readGuildWidget()).resolves.toEqual({name: "Blueshell", members: [], channels: []})
    expect(axios.get).toHaveBeenCalledWith(
      "https://discordapp.com/api/guilds/324285132133629963/widget.json",
    )
  })

  it("reads a quiet evening as empty lists rather than as missing ones", async () => {
    vi.mocked(axios.get).mockResolvedValue({data: {name: "Blueshell"}})

    await expect(readGuildWidget()).resolves.toEqual({name: "Blueshell", members: [], channels: []})
  })

  it("throws when Discord would not say, so the caller decides what to draw", async () => {
    vi.mocked(axios.get).mockRejectedValue(new Error("offline"))

    await expect(readGuildWidget()).rejects.toBeDefined()
  })
})

describe("readGuildCounts", () => {
  it("reads the member total and the online count off the public invite", async () => {
    vi.mocked(axios.get).mockResolvedValue({data: {approximate_member_count: 1199, approximate_presence_count: 269}})

    await expect(readGuildCounts()).resolves.toEqual({members: 1199, online: 269})
    expect(axios.get).toHaveBeenCalledWith("https://discord.com/api/v10/invites/23YMFQy", {params: {with_counts: true}})
  })
})

describe("voiceRoomUrl", () => {
  it("opens the room itself in Discord", () => {
    expect(voiceRoomUrl("42")).toBe("https://discord.com/channels/324285132133629963/42")
  })
})
