import {describe, expect, it, vi} from "vitest"
import {previewRecoveryMail, resendRecoveryMail, restoreDeletedUser} from "@/domains/recovery/adapters/recovery"
import {previewRecoveryEmail, resendRecoveryEmail, restoreDeletedUserById, TokenPurpose} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  previewRecoveryEmail: vi.fn(),
  resendRecoveryEmail: vi.fn(),
  restoreDeletedUserById: vi.fn(),
}))

describe("previewRecoveryMail", () => {
  it("reads the email the account would receive, for the one purpose asked", async () => {
    vi.mocked(previewRecoveryEmail).mockResolvedValue({data: {subject: "Activate"}} as never)

    await expect(previewRecoveryMail(1, TokenPurpose.MEMBER_ACTIVATION)).resolves.toEqual({subject: "Activate"})
    expect(previewRecoveryEmail).toHaveBeenCalledWith({
      path: {userId: 1},
      query: {purpose: TokenPurpose.MEMBER_ACTIVATION},
    })
  })

  it("answers with nothing where the api would not say", async () => {
    vi.mocked(previewRecoveryEmail).mockResolvedValue({} as never)

    await expect(previewRecoveryMail(1, TokenPurpose.PASSWORD_RESET)).resolves.toBeNull()
  })
})

describe("resendRecoveryMail", () => {
  it("sends the one email the purpose names", async () => {
    vi.mocked(resendRecoveryEmail).mockResolvedValue({} as never)

    await resendRecoveryMail(1, TokenPurpose.USER_ACTIVATION)

    expect(resendRecoveryEmail).toHaveBeenCalledWith({
      path: {userId: 1},
      query: {purpose: TokenPurpose.USER_ACTIVATION},
      throwOnError: true,
    })
  })
})

describe("restoreDeletedUser", () => {
  it("addresses the account by its number", async () => {
    vi.mocked(restoreDeletedUserById).mockResolvedValue({} as never)

    await restoreDeletedUser(4)

    expect(restoreDeletedUserById).toHaveBeenCalledWith({path: {userId: 4}, throwOnError: true})
  })
})
