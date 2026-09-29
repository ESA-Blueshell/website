import {beforeEach, describe, expect, it, vi} from "vitest"
import {findUserRoleChanges, findUserRoles, Role, setUserRoles, type UserRolesResponse} from "@/services/api"
import {listRoleChanges, readRoleStanding, saveRolesOrReason} from "@/domains/user/adapters/roles"
import {answer, emptyAnswer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findUserRoleChanges: vi.fn(),
  findUserRoles: vi.fn(),
  setUserRoles: vi.fn(),
}))

const standing: UserRolesResponse = {
  userId: 7, roles: [Role.MEMBER], granted: [Role.MEMBER], assignable: [Role.MEMBER, Role.BOARD], derived: [], dormant: [], implied: [],
}

beforeEach(() => vi.clearAllMocks())

describe("the roles adapter", () => {
  it("reads a person's standing and history, or nothing where the api would not say", async () => {
    vi.mocked(findUserRoles).mockResolvedValueOnce(answer(findUserRoles, standing)).mockResolvedValueOnce(refusal(findUserRoles, {}))
    vi.mocked(findUserRoleChanges).mockResolvedValueOnce(answer(findUserRoleChanges, [])).mockResolvedValueOnce(emptyAnswer(findUserRoleChanges))

    await expect(readRoleStanding(7)).resolves.toEqual(standing)
    await expect(readRoleStanding(7)).resolves.toBeNull()
    await expect(listRoleChanges(7)).resolves.toEqual([])
    await expect(listRoleChanges(7)).resolves.toBeNull()
  })

  it("saves the granted set, and says why the api would not", async () => {
    vi.mocked(setUserRoles).mockResolvedValueOnce(answer(setUserRoles, standing)).mockResolvedValueOnce(refusal(setUserRoles, {code: "LastAdministrator"}))

    await expect(saveRolesOrReason(7, [Role.MEMBER], "note")).resolves.toEqual({ok: true, saved: standing})
    expect(setUserRoles).toHaveBeenCalledWith({path: {userId: 7}, body: {roles: [Role.MEMBER], note: "note"}})
    const refused = await saveRolesOrReason(7, [], null)
    expect(refused).toMatchObject({ok: false})
    expect((refused as {reason: string}).reason).toContain("last administrator")
  })
})
