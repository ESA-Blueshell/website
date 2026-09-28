import {describe, expect, it, vi} from "vitest"
import {listServerRoles} from "@/domains/discord/adapters/roles"
import {listDiscordRoles} from "@/services/api"
import {answer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", () => ({listDiscordRoles: vi.fn()}))

describe("listServerRoles", () => {
  it("answers the roles an event may ping, or nothing where the api cannot ask", async () => {
    vi.mocked(listDiscordRoles).mockResolvedValue(answer(listDiscordRoles, [{id: "901", name: "Gamers"}]))
    expect(await listServerRoles()).toEqual([{id: "901", name: "Gamers"}])

    vi.mocked(listDiscordRoles).mockResolvedValue(refusal(listDiscordRoles, {status: 503}))
    expect(await listServerRoles()).toBeNull()
  })
})
