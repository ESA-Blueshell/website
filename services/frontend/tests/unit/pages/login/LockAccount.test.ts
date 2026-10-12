import {beforeEach, describe, expect, it, vi} from "vitest"
import LockAccount from "@/pages/login/LockAccount.vue"
import {mountInApp, settle} from "../helpers"

const {mockLock, mockToken, mockStore} = vi.hoisted(() => ({
  mockLock: vi.fn(),
  mockToken: vi.fn(),
  mockStore: {getters: {isLoggedIn: false}, commit: vi.fn()},
}))

vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../helpers/testUtils")
  return withVueRouter(importOriginal, {route: {query: {}, hash: ""}, router: {replace: vi.fn()}})
})
vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})
vi.mock("@/domains/auth", () => ({lockAccount: mockLock}))
vi.mock("@/domains/discord", () => ({discordChannel: (name: string) => `https://discord.example/${name}`}))
vi.mock("@/plugins/recoveryToken", () => ({loadRecoveryTokenFromRoute: mockToken}))
vi.mock("@/components/common/banners/TopBanner.vue", () => ({default: {name: "TopBanner", template: "<div />"}}))

describe("locking an account from a warning email", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockStore.getters.isLoggedIn = false
    mockToken.mockReturnValue("s.v")
  })

  it("shows the island spinner until the account is locked, then how to get back in", async () => {
    let locked: (contact: string | null) => void = () => undefined
    mockLock.mockReturnValue(new Promise((resolve) => {
      locked = resolve
    }))
    const wrapper = mountInApp(LockAccount)
    await settle()

    expect(mockLock).toHaveBeenCalledWith("s.v")
    expect(wrapper.find("[role=status]").exists()).toBe(true)
    expect(wrapper.find("[role=progressbar]").exists()).toBe(false)

    locked("board@example.org")
    await settle()

    expect(wrapper.find("[role=status]").exists()).toBe(false)
    expect(wrapper.text()).toContain("Your account is locked")
    expect(wrapper.get("[data-testid=lock-account-contact-email]").attributes("href")).toBe("mailto:board@example.org")
  })

  it("signs this browser out, and names no address when the link gave none", async () => {
    mockToken.mockReturnValue("")
    mockStore.getters.isLoggedIn = true
    const wrapper = mountInApp(LockAccount)
    await settle()

    expect(mockLock).not.toHaveBeenCalled()
    expect(mockStore.commit).toHaveBeenCalledWith("logout")
    expect(wrapper.find("[data-testid=lock-account-contact-email]").exists()).toBe(false)
  })
})
