import {describe, expect, it, vi} from "vitest"
import {listServerRoles} from "@/domains/discord/adapters/roles"
import {listDiscordRoles} from "@/services/api"

vi.mock("@/services/api", () => ({listDiscordRoles: vi.fn()}))

describe("listServerRoles", () => {
  it("answers the roles an event may ping, or nothing where the api cannot ask", async () => {
    vi.mocked(listDiscordRoles).mockResolvedValue({data: [{id: "901", name: "Gamers"}]} as never)
    expect(await listServerRoles()).toEqual([{id: "901", name: "Gamers"}])

    vi.mocked(listDiscordRoles).mockResolvedValue({error: {status: 503}} as never)
    expect(await listServerRoles()).toBeNull()
  })
})
