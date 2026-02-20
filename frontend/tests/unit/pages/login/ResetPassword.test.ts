import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import ResetPassword from "@/pages/login/ResetPassword.vue"
import {settle} from "../helpers"

const {
  mockRoute,
  mockRouterReplace,
  mockSetPassword,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockRoute: {
    query: {token: "reset-token"},
  },
  mockRouterReplace: vi.fn(),
  mockSetPassword: vi.fn(),
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

vi.mock("vee-validate", () => ({
  Form: {
    template: "<form @submit.prevent><slot :meta='{ valid: true }' /></form>",
  },
  useForm: () => ({
    handleSubmit: (cb: () => Promise<void>) => cb,
  }),
}))

vi.mock("@/services/api", () => ({
  setPassword: mockSetPassword,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

describe("ResetPassword page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.query = {token: "reset-token"}
    mockSetPassword.mockResolvedValue({})
  })

  it("keeps token in route query and submits reset request", async () => {
    const wrapper = shallowMount(ResetPassword, {
      global: {
        stubs: {
          VvField: true,
        },
      },
    })

    await settle()

    expect(mockRouterReplace).toHaveBeenCalledWith({
      name: "resetPassword",
      query: {token: "reset-token"},
    })

    ;(wrapper.vm as any).form.password = "NewPass123!"
    await (wrapper.vm as any).onSubmit()
    await settle()

    expect(mockSetPassword).toHaveBeenCalledWith({
      body: {
        password: "NewPass123!",
        token: "reset-token",
      },
      throwOnError: true,
    })
    expect((wrapper.vm as any).succeeded).toBe(true)
  })

  it("redirects home when token is absent", async () => {
    mockRoute.query = {}

    shallowMount(ResetPassword)
    await settle()

    expect(mockRouterReplace).toHaveBeenCalledWith({name: "home"})
  })
})
