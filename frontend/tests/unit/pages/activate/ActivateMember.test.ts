import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import ActivateMember from "@/pages/activate/ActivateMember.vue"
import {settle} from "../helpers"

const {
  mockRoute,
  mockRouterPush,
  mockRouterReplace,
  mockMemberActivate,
  mockValidate,
  mockApply,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockRoute: {query: {}, hash: "#token=member-token"},
  mockRouterPush: vi.fn(),
  mockRouterReplace: vi.fn(),
  mockMemberActivate: vi.fn(),
  mockValidate: vi.fn(async () => true),
  mockApply: vi.fn(() => false),
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

vi.mock("vee-validate", () => ({
  Form: {
    template: "<form><slot :meta='{ valid: true }' /></form>",
  },
  Field: {
    template: "<div><slot :value='\"\"' :errors='[]' /></div>",
  },
  useForm: () => ({
    handleSubmit: (cb: () => Promise<void>) => cb,
    validate: mockValidate,
  }),
}))

vi.mock("@/services/api", () => ({
  memberActivate: mockMemberActivate,
}))

vi.mock("@/plugins/validation.ts", () => ({
  apply: mockApply,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

describe("ActivateMember page", () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    sessionStorage.clear()
    mockRoute.query = {}
    mockRoute.hash = "#token=member-token"
    mockMemberActivate.mockResolvedValue({})
  })

  it("submits activation and redirects to login", async () => {
    const wrapper = shallowMount(ActivateMember, {
      global: {
        stubs: {
          VvField: true,
        },
      },
    })

    ;(wrapper.vm as any).form.username = "tester"
    ;(wrapper.vm as any).form.password = "Password123!"

    await (wrapper.vm as any).onSubmit()

    expect(mockMemberActivate).toHaveBeenCalledWith({
      body: {
        username: "tester",
        password: "Password123!",
        token: "member-token",
      },
      throwOnError: true,
    })

    vi.advanceTimersByTime(2500)
    expect(mockRouterPush).toHaveBeenCalledWith({name: "login"})
  })

  it("redirects home when no token is present", async () => {
    mockRoute.query = {}
    mockRoute.hash = ""

    shallowMount(ActivateMember)
    await settle()

    expect(mockRouterReplace).toHaveBeenCalledWith({name: "home"})
  })
})
