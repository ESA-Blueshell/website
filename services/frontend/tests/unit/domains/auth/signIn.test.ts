import {beforeEach, describe, expect, it, vi} from "vitest"

const {mockAuthenticate} = vi.hoisted(() => ({mockAuthenticate: vi.fn()}))

vi.mock("@/services/api", () => ({authenticate: mockAuthenticate}))

const {signIn} = await import("@/domains/auth")

/**
 * The login form has three things to say and only one of them is a fault, so the status code is
 * read here rather than there.
 */
describe("signIn", () => {
  beforeEach(() => vi.clearAllMocks())

  it("hands back the sign-in", async () => {
    const login = {username: "alice", userId: 4}
    mockAuthenticate.mockResolvedValue({status: 200, data: login})

    await expect(signIn("alice", "Secret123!")).resolves.toEqual({outcome: "signed-in", login})
  })

  it("calls a wrong password a rejection rather than a fault", async () => {
    mockAuthenticate.mockResolvedValue({status: 401})

    await expect(signIn("alice", "wrong")).resolves.toEqual({outcome: "rejected"})
  })

  it("hands anything else on to be reported", async () => {
    const response = {status: 503}
    mockAuthenticate.mockResolvedValue(response)

    await expect(signIn("alice", "Secret123!")).resolves.toEqual({outcome: "failed", cause: response})
  })
})
