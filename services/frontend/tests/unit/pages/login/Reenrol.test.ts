import {beforeEach, describe, expect, it, vi} from "vitest"
import Reenrol from "@/pages/login/Reenrol.vue"
import {mountInApp, settle} from "../helpers"

const {mockReenrol, mockToken, mockClear, mockReplace, mockStore, mockNetworkError} = vi.hoisted(() => ({
  mockReenrol: vi.fn(),
  mockToken: vi.fn(),
  mockClear: vi.fn(),
  mockReplace: vi.fn(),
  mockStore: {commit: vi.fn(), getters: {}},
  mockNetworkError: vi.fn(),
}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})
vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../helpers/testUtils")
  return withVueRouter(importOriginal, {route: {query: {}, hash: ""}, router: {replace: mockReplace}})
})
vi.mock("@/domains/auth", () => ({reenrol: mockReenrol}))
vi.mock("@/plugins/recoveryToken", () => ({loadRecoveryTokenFromRoute: mockToken, clearStoredRecoveryToken: mockClear}))
vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockNetworkError}))
vi.mock("@/components/common/banners/TopBanner.vue", () => ({default: {name: "TopBanner", template: "<div />"}}))

const fill = async (wrapper: ReturnType<typeof mountInApp>) => {
  await wrapper.find("[data-testid=reenrol-username-field] input").setValue("alice")
  await wrapper.find("[data-testid=reenrol-password-field] input").setValue("Secret123!")
  await wrapper.find("form").trigger("submit")
  await settle()
}

describe("signing in with a re-enrolment link", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockToken.mockReturnValue("s.v")
  })

  it("signs in and goes on to set two-factor up again", async () => {
    const login = {userId: 3}
    mockReenrol.mockResolvedValue({outcome: "signed-in", login})
    const wrapper = mountInApp(Reenrol)
    await settle()

    await fill(wrapper)

    expect(mockReenrol).toHaveBeenCalledWith("s.v", "alice", "Secret123!")
    expect(mockClear).toHaveBeenCalled()
    expect(mockStore.commit).toHaveBeenCalledWith("setLogin", login)
    expect(mockReplace).toHaveBeenCalledWith("/account/security/two-factor/set-up")
  })

  it("shows a refusal and hands a fault on", async () => {
    mockReenrol.mockResolvedValueOnce({outcome: "refused", reason: "That link does not work any more."})
    const cause = {status: 503}
    mockReenrol.mockResolvedValueOnce({outcome: "failed", cause})
    const wrapper = mountInApp(Reenrol)
    await settle()

    await fill(wrapper)
    expect(wrapper.text()).toContain("That link does not work any more.")
    await fill(wrapper)
    expect(mockNetworkError).toHaveBeenCalledWith(cause)
  })

  it("asks for the emailed link when there is none", async () => {
    mockToken.mockReturnValue("")
    const wrapper = mountInApp(Reenrol)
    await settle()

    expect(wrapper.text()).toContain("This page needs the link from your email.")
  })
})
