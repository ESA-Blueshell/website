import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import ActivateMember from "@/pages/activate/ActivateMember.vue"
import {mountInApp, settle} from "../helpers"

const {
  mockRoute,
  mockRouterPush,
  mockRouterReplace,
  mockActivateMember,
  mockValidate,
  mockApply,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockRoute: {query: {}, hash: "#token=member-token"},
  mockRouterPush: vi.fn(),
  mockRouterReplace: vi.fn(),
  mockActivateMember: vi.fn(),
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

vi.mock("@/domains/recovery", () => ({
  activateMember: mockActivateMember,
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
    mockActivateMember.mockResolvedValue(undefined)
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

    expect(mockActivateMember).toHaveBeenCalledWith({
      username: "tester",
      password: "Password123!",
      token: "member-token",
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

  it("takes the username and both passwords as typed, and says when the account is activated", async () => {
    const wrapper = mountInApp(ActivateMember, {global: {stubs: {VvField: true}}})
    await settle()
    const [username, password, again] = wrapper.findAllComponents({name: "VvField"})
    username!.vm.$emit("update:modelValue", "tester")
    password!.vm.$emit("update:modelValue", "Password123!")
    again!.vm.$emit("update:modelValue", "Password123!")
    ;(wrapper.vm as any).succeeded = true
    await settle()

    expect((wrapper.vm as any).form).toMatchObject({username: "tester", password: "Password123!"})
    expect((wrapper.vm as any).passwordAgain).toBe("Password123!")
    expect(wrapper.get('[data-testid="activate-member-submit-btn"]').text()).toBe("Activate Member")
    expect(wrapper.get('[data-testid="activate-member-success-alert"]').text()).toContain("Account activated!")
  })
})
