import {beforeEach, describe, expect, it, vi} from "vitest"
import TwoFactorOffer from "@/pages/login/TwoFactorOffer.vue"
import LockAccount from "@/pages/login/LockAccount.vue"
import {mountInApp, settle} from "../helpers"

const {mockStore, mockReplace, mockRoute, mockAnswerOffer, mockReadTwoFactor, mockLockAccount} = vi.hoisted(() => ({
  mockStore: {commit: vi.fn(), getters: {isLoggedIn: true}},
  mockReplace: vi.fn(),
  mockRoute: {query: {redirect: "/events"} as Record<string, string>, hash: ""},
  mockAnswerOffer: vi.fn(),
  mockReadTwoFactor: vi.fn(async () => ({on: false, offered: false, required: false, backupCodesLeft: 0})),
  mockLockAccount: vi.fn(async () => "board@example.org"),
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
  answerOffer: mockAnswerOffer,
  readTwoFactor: mockReadTwoFactor,
  lockAccount: mockLockAccount,
}))

vi.mock("@/plugins/recoveryToken", () => ({loadRecoveryTokenFromRoute: () => "sel.ver"}))
vi.mock("@/components/common/banners/TopBanner.vue", () => ({default: {name: "TopBanner", template: "<div />"}}))

describe("the one-time offer", () => {
  beforeEach(() => vi.clearAllMocks())

  it("records either answer and goes on, to the set-up or to where the reader was going", async () => {
    const wrapper = mountInApp(TwoFactorOffer)

    await (wrapper.vm as any).answer(false)
    expect(mockAnswerOffer).toHaveBeenCalled()
    expect(mockReplace).toHaveBeenCalledWith("/events")

    await (wrapper.vm as any).answer(true)
    expect(mockReplace).toHaveBeenCalledWith({path: "/account/security", query: {setUp: "1", redirect: "/events"}})
  })
})

describe("the lock page", () => {
  beforeEach(() => vi.clearAllMocks())

  it("follows the link, names who to contact and forgets the sign-in on this browser", async () => {
    const wrapper = mountInApp(LockAccount)
    await settle()

    expect(mockLockAccount).toHaveBeenCalledWith("sel.ver")
    expect((wrapper.vm as any).contact).toBe("board@example.org")
    expect(mockStore.commit).toHaveBeenCalledWith("logout")
  })
})
