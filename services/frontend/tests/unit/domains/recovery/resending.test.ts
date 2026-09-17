import {beforeEach, describe, expect, it, vi} from "vitest"

const {mockResendUserActivation} = vi.hoisted(() => ({mockResendUserActivation: vi.fn()}))

vi.mock("@/services/api", () => ({
  memberActivate: vi.fn(),
  pendingActivations: vi.fn(),
  resendUserActivation: mockResendUserActivation,
  resetPassword: vi.fn(),
  setPassword: vi.fn(),
  userActivate: vi.fn(),
}))

const {resendActivation} = await import("@/domains/recovery")

/**
 * Whether an account exists is the one thing this may not report, and whether the api would take
 * the request at all is the one thing it must.
 */
describe("resendActivation", () => {
  beforeEach(() => vi.clearAllMocks())

  it("says it was sent", async () => {
    mockResendUserActivation.mockResolvedValue({})

    await expect(resendActivation("alice")).resolves.toEqual({outcome: "sent"})
  })

  it("says the same for an account that is not there to mail", async () => {
    mockResendUserActivation.mockRejectedValue(new Error("no such account"))

    await expect(resendActivation("nobody")).resolves.toEqual({outcome: "sent"})
  })

  it("says a refusal to send at all, which is about the caller and not the account", async () => {
    const cause = {response: {status: 429}}
    mockResendUserActivation.mockRejectedValue(cause)

    await expect(resendActivation("alice")).resolves.toEqual({outcome: "rate-limited", cause})
  })
})
