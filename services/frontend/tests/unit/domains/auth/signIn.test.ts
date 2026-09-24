import {beforeEach, describe, expect, it, vi} from "vitest"

const {mockAuthenticate, mockAnswerChallenge, mockStepUp, mockReenrol} = vi.hoisted(() => ({
  mockAuthenticate: vi.fn(),
  mockAnswerChallenge: vi.fn(),
  mockStepUp: vi.fn(),
  mockReenrol: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  authenticate: mockAuthenticate,
  answerChallenge: mockAnswerChallenge,
  stepUp: mockStepUp,
  reenrol: mockReenrol,
  SignInStatus: {SIGNED_IN: "SIGNED_IN", TWO_FACTOR_REQUIRED: "TWO_FACTOR_REQUIRED"},
}))

const {answerChallenge, reenrol, signIn, stepUp} = await import("@/domains/auth")

const login = {username: "alice", userId: 4, roles: [], twoFactor: {on: false, backupCodesLeft: 0, required: false, offered: true}}

describe("signIn", () => {
  beforeEach(() => vi.clearAllMocks())

  it("hands back the sign-in", async () => {
    mockAuthenticate.mockResolvedValue({status: 200, data: {status: "SIGNED_IN", login}})

    await expect(signIn("alice", "Secret123!")).resolves.toEqual({outcome: "signed-in", login})
  })

  it("says a code comes next when two-factor is on", async () => {
    mockAuthenticate.mockResolvedValue({status: 200, data: {status: "TWO_FACTOR_REQUIRED"}})

    await expect(signIn("alice", "Secret123!")).resolves.toEqual({outcome: "two-factor"})
  })

  it("calls a wrong password a rejection rather than a fault", async () => {
    mockAuthenticate.mockResolvedValue({status: 401})

    await expect(signIn("alice", "wrong")).resolves.toEqual({outcome: "rejected"})
  })

  it("says what a locked account is to do", async () => {
    mockAuthenticate.mockResolvedValue({status: 403, error: {code: "AccountLocked"}})

    await expect(signIn("alice", "Secret123!")).resolves.toEqual({
      outcome: "refused",
      code: "AccountLocked",
      reason: "This account is locked. Contact the board to have it unlocked.",
    })
  })

  it("hands anything else on to be reported", async () => {
    const response = {status: 503}
    mockAuthenticate.mockResolvedValue(response)

    await expect(signIn("alice", "Secret123!")).resolves.toEqual({outcome: "failed", cause: response})
  })
})

describe("the code step", () => {
  beforeEach(() => vi.clearAllMocks())

  it("signs in on a right code, and says how many tries are left on a wrong one", async () => {
    mockAnswerChallenge.mockResolvedValueOnce({data: {status: "SIGNED_IN", login}})
    mockAnswerChallenge.mockResolvedValueOnce({error: {code: "WrongCode", triesLeft: 2}})

    await expect(answerChallenge("123456", true)).resolves.toEqual({outcome: "signed-in", login})
    expect(mockAnswerChallenge).toHaveBeenCalledWith({body: {code: "123456", trustThisBrowser: true}})
    await expect(answerChallenge("000000", false)).resolves.toMatchObject({
      outcome: "refused",
      code: "WrongCode",
      reason: "That code is not right. 2 tries left.",
    })
  })

  it("re-enrols with the link and the password", async () => {
    mockReenrol.mockResolvedValue({data: {status: "SIGNED_IN", login}})

    await expect(reenrol("sel.ver", "alice", "Secret123!")).resolves.toEqual({outcome: "signed-in", login})
  })

  it("steps up, or says why not", async () => {
    mockStepUp.mockResolvedValueOnce({})
    mockStepUp.mockResolvedValueOnce({error: {code: "WrongPassword"}})

    await expect(stepUp({password: "Secret123!"})).resolves.toEqual({ok: true})
    await expect(stepUp({password: "nope"})).resolves.toEqual({ok: false, reason: "That password is not right."})
  })
})

describe("needsStepUp", () => {
  it("reads a step-up refusal whether it was thrown or answered", async () => {
    const {needsStepUp} = await import("@/domains/auth")

    expect(needsStepUp({code: "StepUpRequired"})).toBe(true)
    expect(needsStepUp({response: {data: {code: "StepUpRequired"}}})).toBe(true)
    expect(needsStepUp({code: "WrongCode"})).toBe(false)
    expect(needsStepUp(null)).toBe(false)
  })
})
