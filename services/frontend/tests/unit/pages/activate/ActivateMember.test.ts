import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import ActivateMember from "@/pages/activate/ActivateMember.vue"
import {mountInApp, settle} from "../helpers"

const {
  mockRoute,
  mockRouterPush,
  mockRouterReplace,
  mockActivateMember,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockRoute: {query: {}, hash: "#token=member-token"},
  mockRouterPush: vi.fn(),
  mockRouterReplace: vi.fn(),
  mockActivateMember: vi.fn(),
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
  activateMember: mockActivateMember,
}))

vi.mock("@/plugins/handleNetworkError", () => ({
  $handleNetworkError: mockHandleNetworkError,
  $showStatusMessage: vi.fn(),
}))

const fields = (wrapper: ReturnType<typeof mountInApp>) => wrapper.findAllComponents({name: "FormControl"})

const fill = async (wrapper: ReturnType<typeof mountInApp>, username: string, password: string, again: string) => {
  const [name, first, second] = fields(wrapper)
  name!.vm.$emit("update:modelValue", username)
  first!.vm.$emit("update:modelValue", password)
  second!.vm.$emit("update:modelValue", again)
  await settle()
}

const send = async (wrapper: ReturnType<typeof mountInApp>) => {
  await wrapper.get('[data-testid="activate-member-form"]').trigger("submit")
  await settle()
}

describe("ActivateMember page", () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    sessionStorage.clear()
    mockRoute.query = {}
    mockRoute.hash = "#token=member-token"
    mockActivateMember.mockResolvedValue(undefined)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it("submits activation and redirects to login", async () => {
    const wrapper = mountInApp(ActivateMember)
    await settle()

    await fill(wrapper, "tester.one", "Password123!", "Password123!")
    await send(wrapper)

    expect(mockActivateMember).toHaveBeenCalledWith({
      username: "tester.one",
      password: "Password123!",
      token: "member-token",
    })
    expect(wrapper.find('[data-testid="activate-member-success-alert"]').exists()).toBe(true)
    vi.advanceTimersByTime(2500)
    expect(mockRouterPush).toHaveBeenCalledWith({name: "login"})
  })

  it("sends nothing until every field holds what it should, and says what is missing", async () => {
    const wrapper = mountInApp(ActivateMember)
    await settle()

    await fill(wrapper, "", "password", "other")
    await send(wrapper)

    expect(mockActivateMember).not.toHaveBeenCalled()
    expect(fields(wrapper).map(field => field.props("errorMessages"))).toEqual([
      ["This field is required"],
      ["Include an uppercase letter"],
      ["Values do not match"],
    ])
  })

  it("says the link may be spent when the api refuses it without naming a field", async () => {
    mockActivateMember.mockRejectedValue(new Error("gone"))
    const wrapper = mountInApp(ActivateMember)
    await settle()

    await fill(wrapper, "tester", "Password123!", "Password123!")
    await send(wrapper)

    expect(mockHandleNetworkError).toHaveBeenCalled()
    expect(wrapper.get('[data-testid="activate-member-error-alert"]').text()).toContain("may be invalid or expired")
  })

  it("redirects home when no token is present", async () => {
    mockRoute.query = {}
    mockRoute.hash = ""

    shallowMount(ActivateMember)
    await settle()

    expect(mockRouterReplace).toHaveBeenCalledWith({name: "home"})
  })
})
