import {beforeEach, describe, expect, it, vi} from "vitest"
import Security from "@/pages/login/Security.vue"
import {mountInApp, settle} from "../helpers"

const {mockStore, mockAuth, mockReplace, mockRoute} = vi.hoisted(() => ({
  mockStore: {commit: vi.fn(), getters: {isBoard: false, isAdmin: false, getLogin: {userId: 3}}},
  mockReplace: vi.fn(),
  mockRoute: {query: {} as Record<string, string>},
  mockAuth: {
    readTwoFactor: vi.fn(),
    listTrustedBrowsers: vi.fn(),
    listSignIns: vi.fn(),
    readMySecurityLog: vi.fn(),
    savePassword: vi.fn(),
    newBackupCodes: vi.fn(),
    removeTwoFactor: vi.fn(),
    askToMoveEmail: vi.fn(),
    endEverySignIn: vi.fn(),
    endOneSignIn: vi.fn(),
    forgetEveryTrustedBrowser: vi.fn(),
    forgetOneTrustedBrowser: vi.fn(),
    describeSecurityEvent: vi.fn(() => "Signed in"),
  },
}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../helpers/testUtils")
  return withVueRouter(importOriginal, {route: mockRoute, router: {replace: mockReplace}})
})

vi.mock("@/domains/auth", () => ({
  ...mockAuth,
  BackupCodes: {name: "BackupCodes", props: ["codes"], template: "<div data-testid='fresh-codes' />"},
  StepUpDialog: {name: "StepUpDialog", props: ["modelValue", "twoFactorOn"], emits: ["proved", "update:modelValue"], template: "<div />"},
  TwoFactorSetUp: {name: "TwoFactorSetUp", emits: ["done", "stepUp"], template: "<div data-testid='set-up' />"},
}))

vi.mock("@/components/common/banners/TopBanner.vue", () => ({default: {name: "TopBanner", template: "<div />"}}))

const ok = {ok: true, value: undefined}
const standing = (on: boolean, backupCodesLeft = on ? 5 : 0, required = false) => ({on, backupCodesLeft, required, offered: false})
const when = "2026-09-24T12:00:00Z"
const trustedBrowser = {id: 4, browser: "Firefox", platform: "Linux", trustedAt: when, expiresAt: when, lastUsedAt: null}
const here = {id: "here", browser: "Firefox", platform: "Linux", signedInAt: when, lastSeenAt: when, current: true}
const there = {...here, id: "there", browser: "Safari", platform: "iOS", current: false}
const logEntry = (id: number, browser: string | null = "Firefox on Linux") => ({id, kind: "SIGNED_IN", actorKind: "PERSON", occurredAt: when, browser})

const open = async () => {
  const wrapper = mountInApp(Security)
  await settle()
  return wrapper
}
type Page = Awaited<ReturnType<typeof open>>
const click = async (wrapper: Page, testId: string) => {
  await wrapper.find(`[data-testid=${testId}]`).trigger("click")
  await settle()
}
const buttonLabelled = (wrapper: Page, label: string) => wrapper.findAll("button").filter(b => b.text() === label)

describe("the security page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.query = {}
    mockStore.getters.isBoard = false
    mockAuth.readTwoFactor.mockResolvedValue(standing(true))
    mockAuth.listTrustedBrowsers.mockResolvedValue([trustedBrowser])
    mockAuth.listSignIns.mockResolvedValue([here, there])
    mockAuth.readMySecurityLog.mockResolvedValue({events: [logEntry(1)], page: 0, totalPages: 1, totalElements: 1})
    for (const write of [
      mockAuth.savePassword, mockAuth.removeTwoFactor, mockAuth.askToMoveEmail, mockAuth.endEverySignIn,
      mockAuth.endOneSignIn, mockAuth.forgetEveryTrustedBrowser, mockAuth.forgetOneTrustedBrowser,
    ]) write.mockResolvedValue(ok)
  })

  it("reads the two-factor standing into the store and lists the rest", async () => {
    const wrapper = await open()

    expect(mockStore.commit).toHaveBeenCalledWith("setTwoFactor", standing(true))
    expect(wrapper.find("[data-testid=security-backup-codes-left]").text()).toBe("5 backup codes left.")
    expect(wrapper.find("[data-testid=security-backup-codes-low]").exists()).toBe(false)
    expect(wrapper.findAll("[data-testid=security-trusted-browser]")).toHaveLength(1)
    expect(wrapper.findAll("[data-testid=security-sign-in]").map(row => row.text())).toEqual([
      expect.stringContaining("Firefox on Linux (this browser)"),
      expect.stringContaining("Safari on iOS"),
    ])
    expect(wrapper.find("[data-testid=security-log-entry]").text()).toContain("Firefox on Linux")
  })

  it("warns when backup codes run low, and makes new ones", async () => {
    mockAuth.readTwoFactor.mockResolvedValue(standing(true, 1))
    mockAuth.newBackupCodes.mockResolvedValue({ok: true, value: ["aaaaa-bbbbb"]})
    const wrapper = await open()
    expect(wrapper.find("[data-testid=security-backup-codes-left]").text()).toBe("1 backup code left.")
    expect(wrapper.find("[data-testid=security-backup-codes-low]").exists()).toBe(true)

    await click(wrapper, "security-new-backup-codes-btn")

    expect(wrapper.findComponent({name: "BackupCodes"}).props("codes")).toEqual(["aaaaa-bbbbb"])
    expect(mockAuth.readTwoFactor).toHaveBeenCalledTimes(2)
  })

  it("replaces the authenticator app, and goes back where it was sent from", async () => {
    mockRoute.query = {redirect: "/management/users"}
    const wrapper = await open()

    await click(wrapper, "security-replace-two-factor-btn")
    wrapper.findComponent({name: "TwoFactorSetUp"}).vm.$emit("done")
    await settle()

    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Two-factor authentication is on.")
    expect(mockReplace).toHaveBeenCalledWith("/management/users")
    expect(wrapper.find("[data-testid=set-up]").exists()).toBe(false)
  })

  it("turns two-factor off for a member, and never offers it to a granted-role holder", async () => {
    mockAuth.readTwoFactor.mockResolvedValueOnce(standing(true)).mockResolvedValueOnce(standing(false))
    const wrapper = await open()

    await click(wrapper, "security-turn-off-two-factor-btn")

    expect(mockAuth.removeTwoFactor).toHaveBeenCalled()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Two-factor authentication is off.")

    mockStore.getters.isBoard = true
    mockAuth.readTwoFactor.mockResolvedValue(standing(true))
    const board = await open()
    expect(board.find("[data-testid=security-turn-off-two-factor-btn]").exists()).toBe(false)
  })

  it("sets two-factor up from off, and says a granted role waits on it", async () => {
    mockAuth.readTwoFactor.mockResolvedValue(standing(false, 0, true))
    const wrapper = await open()
    expect(wrapper.find("[data-testid=security-set-up-required]").exists()).toBe(true)

    await click(wrapper, "security-set-up-two-factor-btn")
    const setUp = wrapper.findComponent({name: "TwoFactorSetUp"})
    expect(setUp.exists()).toBe(true)

    const retry = vi.fn()
    setUp.vm.$emit("stepUp", retry)
    await settle()
    const stepUp = wrapper.findComponent({name: "StepUpDialog"})
    expect(stepUp.props("modelValue")).toBe(true)
    expect(stepUp.props("twoFactorOn")).toBe(false)
    stepUp.vm.$emit("update:modelValue", false)
    stepUp.vm.$emit("proved")
    expect(retry).toHaveBeenCalled()
  })

  it("opens straight on the set-up when sent here to set it up", async () => {
    mockRoute.query = {setUp: "1"}
    mockAuth.readTwoFactor.mockResolvedValue(standing(false))
    const wrapper = await open()

    expect(wrapper.find("[data-testid=set-up]").exists()).toBe(true)
    wrapper.findComponent({name: "TwoFactorSetUp"}).vm.$emit("done")
    await settle()
    expect(mockReplace).not.toHaveBeenCalled()
  })

  it("asks for a step-up when the api wants one, and runs the change again once proved", async () => {
    mockAuth.savePassword
      .mockResolvedValueOnce({ok: false, reason: "Confirm it is you first.", needsStepUp: true})
      .mockResolvedValueOnce(ok)
    const wrapper = await open()
    await wrapper.find("[data-testid=security-current-password-field] input").setValue("Secret123!")
    await wrapper.find("[data-testid=security-new-password-field] input").setValue("Another123!")

    await wrapper.find("[data-testid=security-password] form").trigger("submit")
    await settle()
    expect(wrapper.findComponent({name: "StepUpDialog"}).props("modelValue")).toBe(true)

    wrapper.findComponent({name: "StepUpDialog"}).vm.$emit("proved")
    await settle()
    expect(mockAuth.savePassword).toHaveBeenCalledTimes(2)
    expect(mockAuth.savePassword).toHaveBeenCalledWith("Secret123!", "Another123!")
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Your password is changed. Every other sign-in has ended.")
  })

  it("sends a confirmation link to a new address, and shows a refusal", async () => {
    mockAuth.askToMoveEmail
      .mockResolvedValueOnce(ok)
      .mockResolvedValueOnce({ok: false, reason: "That address belongs to another account.", needsStepUp: false})
    const wrapper = await open()
    const email = wrapper.find("[data-testid=security-new-email-field] input")

    await email.setValue(" new@example.com ")
    await wrapper.find("[data-testid=security-email] form").trigger("submit")
    await settle()
    expect(mockAuth.askToMoveEmail).toHaveBeenCalledWith("new@example.com")
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "A confirmation link is on its way to new@example.com.")

    await email.setValue("taken@example.com")
    await wrapper.find("[data-testid=security-email] form").trigger("submit")
    await settle()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "That address belongs to another account.")
  })

  it("forgets trusted browsers one at a time or all at once", async () => {
    mockAuth.forgetOneTrustedBrowser.mockResolvedValueOnce({ok: false, reason: "That browser could not be forgotten."})
    const wrapper = await open()

    await buttonLabelled(wrapper, "Forget")[0].trigger("click")
    await settle()
    expect(mockAuth.forgetOneTrustedBrowser).toHaveBeenCalledWith(4)
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "That browser could not be forgotten.")

    await click(wrapper, "security-forget-all-btn")
    expect(mockAuth.forgetEveryTrustedBrowser).toHaveBeenCalled()

    mockAuth.listTrustedBrowsers.mockResolvedValue([])
    const none = await open()
    expect(none.find("[data-testid=security-trusted-browsers]").text()).toContain("No browser skips the code at sign-in.")
  })

  it("ends another sign-in, but offers no way to end this one", async () => {
    mockAuth.endOneSignIn.mockResolvedValueOnce({ok: false, reason: "That sign-in could not be ended."})
    const wrapper = await open()

    const signOuts = buttonLabelled(wrapper, "Sign out")
    expect(signOuts).toHaveLength(1)
    await signOuts[0].trigger("click")
    await settle()

    expect(mockAuth.endOneSignIn).toHaveBeenCalledWith("there")
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "That sign-in could not be ended.")
  })

  it("signs out of this browser too when signing out everywhere", async () => {
    mockAuth.endEverySignIn.mockResolvedValueOnce({ok: false, reason: "Signing out everywhere failed."})
    const wrapper = await open()

    await click(wrapper, "security-sign-out-everywhere-btn")
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Signing out everywhere failed.")
    expect(mockReplace).not.toHaveBeenCalled()

    await click(wrapper, "security-sign-out-everywhere-btn")
    expect(mockStore.commit).toHaveBeenCalledWith("logout")
    expect(mockReplace).toHaveBeenCalledWith("/login")
  })

  it("reads older log entries a page at a time", async () => {
    mockAuth.readMySecurityLog
      .mockResolvedValueOnce({events: [logEntry(1)], page: 0, totalPages: 2, totalElements: 2})
      .mockResolvedValueOnce({events: [logEntry(2, null)], page: 1, totalPages: 2, totalElements: 2})
    const wrapper = await open()

    await buttonLabelled(wrapper, "Show older")[0].trigger("click")
    await settle()

    expect(mockAuth.readMySecurityLog).toHaveBeenLastCalledWith(1)
    expect(wrapper.findAll("[data-testid=security-log-entry]")).toHaveLength(2)
    expect(buttonLabelled(wrapper, "Show older")).toHaveLength(0)
  })

  it("shows nothing it could not read", async () => {
    mockAuth.readTwoFactor.mockResolvedValue(null)
    mockAuth.readMySecurityLog.mockResolvedValue(null)
    const wrapper = await open()

    expect(mockStore.commit).not.toHaveBeenCalledWith("setTwoFactor", expect.anything())
    expect(wrapper.findAll("[data-testid=security-log-entry]")).toHaveLength(0)
  })
})
