import {beforeEach, describe, expect, it, vi} from "vitest"
import Security from "@/pages/login/Security.vue"
import {mountInApp, settle} from "../helpers"

const {mockStore, mockAuth, mockReplace} = vi.hoisted(() => ({
  mockStore: {commit: vi.fn(), getters: {isBoard: false, isAdmin: false, getLogin: {userId: 3}}},
  mockReplace: vi.fn(),
  mockAuth: {
    readTwoFactor: vi.fn(),
    listTrustedBrowsers: vi.fn(async () => []),
    listSignIns: vi.fn(async () => []),
    readMySecurityLog: vi.fn(async () => ({events: [], page: 0, totalPages: 1, totalElements: 0})),
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
  return withVueRouter(importOriginal, {route: {query: {}}, router: {replace: mockReplace}})
})

vi.mock("@/domains/auth", () => ({
  ...mockAuth,
  BackupCodes: {name: "BackupCodes", template: "<div />"},
  StepUpDialog: {name: "StepUpDialog", template: "<div />"},
  TwoFactorSetUp: {name: "TwoFactorSetUp", template: "<div />"},
}))

vi.mock("@/components/common/banners/TopBanner.vue", () => ({default: {name: "TopBanner", template: "<div />"}}))

const standing = (on: boolean) => ({on, backupCodesLeft: on ? 2 : 0, required: false, offered: false})

describe("the security page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockAuth.readTwoFactor.mockResolvedValue(standing(true))
  })

  it("reads the two-factor standing into the store and lists the rest", async () => {
    mountInApp(Security)
    await settle()

    expect(mockStore.commit).toHaveBeenCalledWith("setTwoFactor", standing(true))
    expect(mockAuth.listSignIns).toHaveBeenCalled()
    expect(mockAuth.listTrustedBrowsers).toHaveBeenCalled()
    expect(mockAuth.readMySecurityLog).toHaveBeenCalledWith(0)
  })

  it("asks for a step-up when the api wants one, and runs the change again once proved", async () => {
    mockAuth.savePassword
      .mockResolvedValueOnce({ok: false, reason: "Confirm it is you first.", needsStepUp: true})
      .mockResolvedValueOnce({ok: true, value: undefined})
    const wrapper = mountInApp(Security)
    await settle()
    const vm = wrapper.vm as any
    vm.currentPassword = "Secret123!"
    vm.newPassword = "Another123!"

    await vm.guarded(vm.changePassword)
    expect(vm.stepUpOpen).toBe(true)

    vm.retryAfterStepUp()
    await settle()
    expect(mockAuth.savePassword).toHaveBeenCalledTimes(2)
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Your password is changed. Every other sign-in has ended.")
  })

  it("signs out of this browser too when signing out everywhere", async () => {
    mockAuth.endEverySignIn.mockResolvedValue({ok: true, value: undefined})
    const wrapper = mountInApp(Security)
    await settle()

    await (wrapper.vm as any).everywhere()

    expect(mockStore.commit).toHaveBeenCalledWith("logout")
    expect(mockReplace).toHaveBeenCalledWith("/login")
  })

  it("offers no way to turn two-factor off to somebody holding a granted role", async () => {
    mockStore.getters.isBoard = true
    const wrapper = mountInApp(Security)
    await settle()

    expect((wrapper.vm as any).holdsGrantedRole).toBe(true)
    mockStore.getters.isBoard = false
  })
})
