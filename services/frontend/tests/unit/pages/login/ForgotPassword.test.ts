import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import ForgotPassword from "@/pages/login/ForgotPassword.vue"
import {settle} from "../helpers"

const {
  mockRoute,
  mockResetPassword,
  mockSetFieldValue,
} = vi.hoisted(() => ({
  mockRoute: {
    query: {username: "alice"},
  },
  mockResetPassword: vi.fn(),
  mockSetFieldValue: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...actual,
    useRoute: () => mockRoute,
  }
})

vi.mock("vee-validate", () => ({
  Form: {
    template: "<form @submit.prevent><slot :meta='{ valid: true }' /></form>",
  },
  useForm: () => ({
    setFieldValue: mockSetFieldValue,
    handleSubmit: (cb: () => Promise<void>) => cb,
  }),
}))

vi.mock("@/services/api", () => ({
  resetPassword: mockResetPassword,
}))

describe("ForgotPassword page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.query = {username: "alice"}
    mockResetPassword.mockResolvedValue({})
  })

  it("prefills username from query and triggers reset request", async () => {
    const wrapper = shallowMount(ForgotPassword, {
      global: {
        stubs: {
          VvField: true,
        },
      },
    })

    await settle()

    expect(mockSetFieldValue).toHaveBeenCalledWith("username", "alice")
    expect((wrapper.vm as any).form.username).toBe("alice")

    await (wrapper.vm as any).onSubmit()
    expect(mockResetPassword).toHaveBeenCalledWith({
      path: {username: "alice"},
      throwOnError: false,
    })
    expect(wrapper.text()).toContain("you’ll receive an email")
  })
})
