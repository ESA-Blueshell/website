import {beforeEach, describe, expect, it, vi} from "vitest"

const api = vi.hoisted(() => {
  const names = [
    "accountStanding", "answerTwoFactorOffer", "changePassword", "confirmEmailChange", "confirmTwoFactor", "emailAddress", "endSignIn",
    "forgetTrustedBrowser", "forgetTrustedBrowsers", "lock", "mySecurityEvents", "regenerateBackupCodes",
    "previewRecoveryEmail", "requestEmailChange", "resendReenrolmentLink", "resetTwoFactor", "securityEvents", "setUpTwoFactor", "signIns",
    "signOutElsewhere", "signOutEverywhere", "trustedBrowsers", "turnOffTwoFactor", "twoFactorSaved", "twoFactorStanding", "unlock",
  ]
  return Object.fromEntries(names.map(name => [name, vi.fn()])) as Record<string, ReturnType<typeof vi.fn>>
})

vi.mock("@/services/api", () => ({...api, TokenPurpose: {TWO_FACTOR_REENROLMENT: "TWO_FACTOR_REENROLMENT"}}))

const security = await import("@/domains/auth/adapters/accountSecurity")

const refused = {error: {code: "StepUpRequired"}}
const failed = {error: {code: "WrongPassword"}}

describe("account security writes", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    for (const call of Object.values(api)) call.mockResolvedValue({data: undefined})
  })

  const writes: [string, string, () => Promise<unknown>, unknown][] = [
    ["setUpTwoFactor", "startTwoFactorSetUp", () => security.startTwoFactorSetUp("pw"), {body: {password: "pw"}}],
    ["setUpTwoFactor", "startTwoFactorSetUp", () => security.startTwoFactorSetUp(), {body: {}}],
    ["confirmTwoFactor", "confirmTwoFactorCode", () => security.confirmTwoFactorCode("123456"), {body: {code: "123456"}}],
    ["twoFactorSaved", "finishTwoFactorSetUp", () => security.finishTwoFactorSetUp(), undefined],
    ["turnOffTwoFactor", "removeTwoFactor", () => security.removeTwoFactor(), undefined],
    ["regenerateBackupCodes", "newBackupCodes", () => security.newBackupCodes(), undefined],
    ["answerTwoFactorOffer", "answerOffer", () => security.answerOffer(), undefined],
    ["changePassword", "savePassword", () => security.savePassword("old", "new"), {body: {currentPassword: "old", newPassword: "new"}}],
    ["requestEmailChange", "askToMoveEmail", () => security.askToMoveEmail("n@example.com"), {body: {email: "n@example.com"}}],
    ["confirmEmailChange", "confirmNewEmail", () => security.confirmNewEmail("s.v"), {body: {token: "s.v"}}],
    ["endSignIn", "endOneSignIn", () => security.endOneSignIn("s"), {path: {signInId: "s"}}],
    ["signOutEverywhere", "endEverySignIn", () => security.endEverySignIn(), undefined],
    ["signOutElsewhere", "endOtherSignIns", () => security.endOtherSignIns(), undefined],
    ["forgetTrustedBrowser", "forgetOneTrustedBrowser", () => security.forgetOneTrustedBrowser(3), {path: {id: 3}}],
    ["forgetTrustedBrowsers", "forgetEveryTrustedBrowser", () => security.forgetEveryTrustedBrowser(), undefined],
    ["resetTwoFactor", "resetTwoFactorOf", () => security.resetTwoFactorOf(9, "lost"), {path: {userId: 9}, body: {reason: "lost"}}],
    ["resendReenrolmentLink", "resendReenrolment", () => security.resendReenrolment(9), {path: {userId: 9}}],
    ["unlock", "unlockAccount", () => security.unlockAccount(9, "heard", ""), {path: {userId: 9}, body: {reason: "heard", email: undefined}}],
  ]

  it.each(writes)("%s goes through %s, and a refusal says whether a step-up clears it", async (sdk, _name, call, args) => {
    await expect(call()).resolves.toMatchObject({ok: true})
    if (args) expect(api[sdk]).toHaveBeenCalledWith(args)

    api[sdk].mockResolvedValueOnce(refused)
    await expect(call()).resolves.toEqual({ok: false, reason: "Confirm it is you first.", needsStepUp: true})
    api[sdk].mockResolvedValueOnce(failed)
    await expect(call()).resolves.toMatchObject({ok: false, needsStepUp: false})
  })

  it("answers the values a write hands back", async () => {
    api.setUpTwoFactor.mockResolvedValue({data: {otpauthUri: "otpauth://x", key: "K"}})
    api.confirmTwoFactor.mockResolvedValue({data: {codes: ["a"]}})
    api.regenerateBackupCodes.mockResolvedValue({data: {codes: ["b"]}})

    await expect(security.startTwoFactorSetUp("pw")).resolves.toEqual({ok: true, value: {otpauthUri: "otpauth://x", key: "K"}})
    await expect(security.confirmTwoFactorCode("1")).resolves.toEqual({ok: true, value: ["a"]})
    await expect(security.newBackupCodes()).resolves.toEqual({ok: true, value: ["b"]})
  })
})

describe("account security reads", () => {
  beforeEach(() => vi.clearAllMocks())

  it("answers what was read, or nothing", async () => {
    const page = {events: [], page: 0, totalPages: 0, totalElements: 0}
    api.twoFactorStanding.mockResolvedValue({data: {on: true}})
    api.lock.mockResolvedValue({data: {contactEmail: "board@example.org"}})
    api.signIns.mockResolvedValue({data: [{id: "s"}]})
    api.trustedBrowsers.mockResolvedValue({data: [{id: 1}]})
    api.mySecurityEvents.mockResolvedValue({data: page})
    api.securityEvents.mockResolvedValue({data: page})
    api.accountStanding.mockResolvedValue({data: {locked: true}})
    api.emailAddress.mockResolvedValue({data: {email: "a@example.com", pendingEmail: null}})

    await expect(security.readTwoFactor()).resolves.toEqual({on: true})
    await expect(security.lockAccount("s.v")).resolves.toBe("board@example.org")
    await expect(security.listSignIns()).resolves.toEqual([{id: "s"}])
    await expect(security.listTrustedBrowsers()).resolves.toEqual([{id: 1}])
    await expect(security.readMySecurityLog()).resolves.toBe(page)
    expect(api.mySecurityEvents).toHaveBeenCalledWith({query: {page: 0, size: 20}})
    await expect(security.readSecurityLogOf(9, 2)).resolves.toBe(page)
    expect(api.securityEvents).toHaveBeenCalledWith({path: {userId: 9}, query: {page: 2, size: 20}})
    await expect(security.readAccountStanding(9)).resolves.toEqual({locked: true})
    await expect(security.readEmailAddress()).resolves.toEqual({email: "a@example.com", pendingEmail: null})
    api.previewRecoveryEmail.mockResolvedValue({data: {subject: "s"}})
    await expect(security.previewReenrolment(9)).resolves.toEqual({subject: "s"})
    expect(api.previewRecoveryEmail).toHaveBeenCalledWith({path: {userId: 9}, query: {purpose: "TWO_FACTOR_REENROLMENT"}})

    for (const call of Object.values(api)) call.mockResolvedValue({})
    await expect(security.readTwoFactor()).resolves.toBeNull()
    await expect(security.lockAccount("s.v")).resolves.toBeNull()
    await expect(security.listSignIns()).resolves.toEqual([])
    await expect(security.listTrustedBrowsers()).resolves.toEqual([])
    await expect(security.readMySecurityLog()).resolves.toBeNull()
    await expect(security.readSecurityLogOf(9)).resolves.toBeNull()
    await expect(security.readAccountStanding(9)).resolves.toBeNull()
    await expect(security.readEmailAddress()).resolves.toBeNull()
    await expect(security.previewReenrolment(9)).resolves.toBeNull()
  })
})
