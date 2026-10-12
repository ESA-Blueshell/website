import {beforeEach, describe, expect, it, vi} from "vitest"
import ActivateUser from "@/pages/activate/ActivateUser.vue"
import {mountInApp, settle} from "../helpers"

const {
  mockRoute,
  mockRouterPush,
  mockRouterReplace,
  mockActivateUser,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockRoute: {query: {}, hash: "#token=user-token"},
  mockRouterPush: vi.fn(),
  mockRouterReplace: vi.fn(),
  mockActivateUser: vi.fn(),
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

vi.mock("@/domains/recovery", () => ({
  activateUser: mockActivateUser,
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
    mockActivateUser.mockResolvedValue({membershipStarted: false})
  })

  it("shows the island spinner while the account is being activated", async () => {
    mockActivateUser.mockReturnValue(new Promise(() => undefined))
    const wrapper = mountInApp(ActivateUser)
    await settle()

    const waiting = wrapper.get("[data-testid=activate-user-loading-state]")
    expect(waiting.find("[role=status]").exists()).toBe(true)
    expect(waiting.find("[role=progressbar]").exists()).toBe(false)
  })

  it("confirms the account and sends the applicant to sign in", async () => {
    mountInApp(ActivateUser)
    await settle()

    expect(mockActivateUser).toHaveBeenCalledWith("user-token")

    vi.advanceTimersByTime(1500)
    expect(mockRouterPush).toHaveBeenCalledWith({name: "login"})
  })

  it("says the membership has started when confirmation completed it", async () => {
    mockActivateUser.mockResolvedValue({membershipStarted: true})

    const wrapper = mountInApp(ActivateUser)
    await settle()

    expect(wrapper.text()).toContain("your membership has started")
    expect(wrapper.get("[data-testid=activate-user-success-state]").findAll("i, svg")).toHaveLength(0)
  })

  it("says the link could not be verified when the API refuses the token", async () => {
    const refusal = new Error("gone")
    mockActivateUser.mockRejectedValue(refusal)

    const wrapper = mountInApp(ActivateUser)
    await settle()

    expect(mockHandleNetworkError).toHaveBeenCalledWith(refusal)
    expect(wrapper.text()).toContain("invalid, expired, or already used")
    const notice = wrapper.getComponent({name: "NoticeBox"})
    expect(notice.attributes("data-testid")).toBe("activate-user-error-alert")
    expect(notice.attributes("role")).toBe("alert")

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
