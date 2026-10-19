import {describe, expect, it, vi} from "vitest"
import {activateUser} from "@/domains/recovery/adapters/recovery"
import {userActivate} from "@/services/api"
import {answer} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  userActivate: vi.fn(),
}))

describe("activateUser", () => {
  it("says whether a membership started and whether the next incasso run collects its first contribution", async () => {
    vi.mocked(userActivate).mockResolvedValueOnce(answer(userActivate, {membershipStarted: true, collectedByIncasso: true}))
    await expect(activateUser("tok")).resolves.toEqual({membershipStarted: true, collectedByIncasso: true})
    expect(userActivate).toHaveBeenCalledWith({body: {token: "tok"}, throwOnError: true})

    vi.mocked(userActivate).mockResolvedValueOnce(answer(userActivate, {membershipStarted: false, collectedByIncasso: false}))
    await expect(activateUser("tok")).resolves.toEqual({membershipStarted: false, collectedByIncasso: false})
  })
})
