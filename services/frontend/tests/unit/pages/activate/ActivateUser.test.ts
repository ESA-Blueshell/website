import {beforeEach, describe, expect, it, vi} from "vitest"
import ActivateUser from "@/pages/activate/ActivateUser.vue"
import {mountInApp, settle} from "../helpers"

const {
  mockRoute,
  mockRouterPush,
  mockRouterReplace,
  mockUserActivate,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockRoute: {query: {}, hash: "#token=user-token"},
  mockRouterPush: vi.fn(),
  mockRouterReplace: vi.fn(),
  mockUserActivate: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...actual,
    useRoute: () => mockRoute,
    useRouter: () => ({
      push: mockRouterPush,
      replace: mockRouterReplace,
    }),
  }
})

vi.mock("@/services/api", () => ({
  userActivate: mockUserActivate,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

describe("ActivateUser page", () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    sessionStorage.clear()
    mockRoute.query = {}
    mockRoute.hash = "#token=user-token"
    mockUserActivate.mockResolvedValue({data: {membershipStarted: false}})
  })

  it("confirms the account and sends the applicant to sign in", async () => {
    mountInApp(ActivateUser)
    await settle()

    expect(mockUserActivate).toHaveBeenCalledWith({
      body: {token: "user-token"},
      throwOnError: true,
    })

    vi.advanceTimersByTime(1500)
    expect(mockRouterPush).toHaveBeenCalledWith({name: "login"})
  })

  it("says the membership has started when confirmation completed it", async () => {
    mockUserActivate.mockResolvedValue({data: {membershipStarted: true}})

    const wrapper = mountInApp(ActivateUser)
    await settle()

    expect(wrapper.text()).toContain("your membership has started")
  })

  it("says the link could not be verified when the API refuses the token", async () => {
    const refusal = new Error("gone")
    mockUserActivate.mockRejectedValue(refusal)

    const wrapper = mountInApp(ActivateUser)
    await settle()

    expect(mockHandleNetworkError).toHaveBeenCalledWith(refusal)
    expect(wrapper.text()).toContain("invalid, expired, or already used")

    vi.advanceTimersByTime(2499)
    expect(mockRouterPush).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(mockRouterPush).toHaveBeenCalledWith({name: "login"})
  })

  it("redirects to login on missing token", async () => {
    mockRoute.query = {}
    mockRoute.hash = ""

    const wrapper = mountInApp(ActivateUser)
    await settle()

    expect(wrapper.text()).toContain("invalid, expired, or already used")
    vi.advanceTimersByTime(2500)
    expect(mockRouterPush).toHaveBeenCalledWith({name: "login"})
  })
})
