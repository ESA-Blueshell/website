import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import ResetPassword from "@/pages/login/ResetPassword.vue"
import {mountInApp, settle} from "../helpers"

const {
  mockRoute,
  mockRouterReplace,
  mockSetNewPassword,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockRoute: {
    query: {},
    hash: "#token=reset-token",
  },
  mockRouterReplace: vi.fn(),
  mockSetNewPassword: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...actual,
    useRoute: () => mockRoute,
    useRouter: () => ({
      replace: mockRouterReplace,
    }),
  }
})

vi.mock("vee-validate", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vee-validate")>()
  return {
    ...actual,
    Form: {
      template: "<form @submit.prevent><slot :meta='{ valid: true }' /></form>",
    },
    useForm: () => ({
      handleSubmit: (cb: () => Promise<void>) => cb,
    }),
  }
})

vi.mock("@/domains/recovery", () => ({
  setNewPassword: mockSetNewPassword,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

describe("ResetPassword page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    mockRoute.query = {}
    mockRoute.hash = "#token=reset-token"
    mockSetNewPassword.mockResolvedValue(undefined)
  })

  it("reads token from hash, strips it from URL, and submits reset request", async () => {
    const wrapper = shallowMount(ResetPassword, {
      global: {
        stubs: {
          VvField: true,
        },
      },
    })

    await settle()

    expect(mockRouterReplace).toHaveBeenCalledWith({
      query: {},
      hash: "",
    })

    ;(wrapper.vm as any).form.password = "NewPass123!"
    await (wrapper.vm as any).onSubmit()
    await settle()

    expect(mockSetNewPassword).toHaveBeenCalledWith({
      password: "NewPass123!",
      token: "reset-token",
    })
    expect((wrapper.vm as any).succeeded).toBe(true)
  })

  it("redirects home when token is absent", async () => {
    mockRoute.query = {}
    mockRoute.hash = ""

    shallowMount(ResetPassword)
    await settle()

    expect(mockRouterReplace).toHaveBeenCalledWith({name: "home"})
  })

  it("takes the new password and its repeat as typed, under a Reset Password button", async () => {
    const wrapper = mountInApp(ResetPassword, {global: {stubs: {VvField: true}}})
    await settle()
    const [password, again] = wrapper.findAllComponents({name: "VvField"})
    password!.vm.$emit("update:modelValue", "NewPass123!")
    again!.vm.$emit("update:modelValue", "NewPass123!")
    await settle()

    expect((wrapper.vm as any).form.password).toBe("NewPass123!")
    expect((wrapper.vm as any).passwordAgain).toBe("NewPass123!")
    expect(wrapper.get('[data-testid="reset-password-submit-btn"]').text()).toBe("Reset Password")
  })
})
