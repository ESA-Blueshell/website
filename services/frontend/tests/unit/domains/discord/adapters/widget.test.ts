import {describe, expect, it, vi} from "vitest"
import axios from "axios"
import {readGuildWidget} from "@/domains/discord/adapters/widget"

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
