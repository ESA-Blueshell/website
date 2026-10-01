import {describe, expect, it, vi} from "vitest"
import {archiveChannelOf, closeTo, createChannelFor, openTo, readOpenings} from "@/domains/discord/adapters/roleOpenings"
import {RoleAccess, archiveRoleChannel, createRoleChannel, listRoleOpenings, removeRoleOpening, setRoleOpening} from "@/services/api"
import {answer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", () => ({
  RoleAccess: {WRITE: "WRITE", READ: "READ", SPEAK: "SPEAK"},
  archiveRoleChannel: vi.fn(),
  createRoleChannel: vi.fn(),
  listRoleOpenings: vi.fn(),
  removeRoleOpening: vi.fn(),
  setRoleOpening: vi.fn(),
}))

const lounge = {channel: {id: "1", name: "lounge", kind: "TEXT" as const}, kept: RoleAccess.WRITE, actual: RoleAccess.WRITE, differs: false}

describe("the role openings adapter", () => {
  it("reads what a role opens, or nothing where Discord cannot be read", async () => {
    vi.mocked(listRoleOpenings).mockResolvedValue(answer(listRoleOpenings, [lounge]))
    expect(await readOpenings("500")).toEqual([lounge])
    expect(listRoleOpenings).toHaveBeenCalledWith({path: {roleId: "500"}})

    vi.mocked(listRoleOpenings).mockResolvedValue(refusal(listRoleOpenings, {code: "DiscordUnreachable"}, 503))
    expect(await readOpenings("500")).toBeNull()
  })

  it("opens, closes, makes and archives, answering what the role opens now or why not", async () => {
    vi.mocked(setRoleOpening).mockResolvedValue(answer(setRoleOpening, [lounge]))
    vi.mocked(removeRoleOpening).mockResolvedValue(answer(removeRoleOpening, []))
    vi.mocked(createRoleChannel).mockResolvedValue(answer(createRoleChannel, [lounge]))
    vi.mocked(archiveRoleChannel).mockResolvedValue(refusal(archiveRoleChannel, {code: "DiscordUnreachable"}, 503))

    expect(await openTo("500", "1", RoleAccess.READ)).toEqual({ok: true, saved: [lounge]})
    expect(setRoleOpening).toHaveBeenCalledWith({path: {roleId: "500", channelId: "1"}, body: {access: "READ"}})
    expect(await closeTo("500", "1")).toEqual({ok: true, saved: []})
    expect(await createChannelFor("500", "lounge", "Members", RoleAccess.WRITE)).toEqual({ok: true, saved: [lounge]})
    expect(createRoleChannel).toHaveBeenCalledWith({path: {roleId: "500"}, body: {name: "lounge", category: "Members", access: "WRITE"}})
    expect(await archiveChannelOf("500", "1")).toEqual({ok: false, reason: "Discord cannot be reached now; try again in a moment."})
  })
})
