import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import ActivateUser from "@/pages/activate/ActivateUser.vue"
import {settle} from "../helpers"

const {
  mockRoute,
  mockRouterPush,
  mockUserActivate,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockRoute: {query: {token: "user-token"}},
  mockRouterPush: vi.fn(),
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
    mockRoute.query = {token: "user-token"}
    mockUserActivate.mockResolvedValue({data: {path: "/membership/signup?step=2"}})
  })

  it("activates account and redirects with path from backend", async () => {
    shallowMount(ActivateUser)
    await settle()

    expect(mockUserActivate).toHaveBeenCalledWith({
      body: {token: "user-token"},
      throwOnError: true,
    })

    vi.advanceTimersByTime(1500)
    expect(mockRouterPush).toHaveBeenCalledWith({
      name: "login",
      query: {redirect: "/membership/signup?step=2"},
    })
  })

  it("redirects to login on missing token", async () => {
    mockRoute.query = {}

    const wrapper = shallowMount(ActivateUser)
    await settle()

    expect(wrapper.text()).toContain("invalid, expired, or already used")
    vi.advanceTimersByTime(2500)
    expect(mockRouterPush).toHaveBeenCalledWith({name: "login"})
  })
})
