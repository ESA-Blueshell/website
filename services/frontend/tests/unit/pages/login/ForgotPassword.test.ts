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

  const mountPage = () =>
    shallowMount(ForgotPassword, {global: {stubs: {VvField: true}}})

  it("prefills username from query and asks for the reset", async () => {
    const wrapper = mountPage()
    await settle()

    expect(mockSetFieldValue).toHaveBeenCalledWith("username", "alice")
    expect((wrapper.vm as any).form.username).toBe("alice")

    await (wrapper.vm as any).onSubmit()

    expect(mockResetPassword).toHaveBeenCalledWith({
      path: {username: "alice"},
      throwOnError: true,
    })
    expect(wrapper.text()).toContain("you’ll receive an email")
  })

  /**
   * Being vague about whether the account exists is the point, and it stays. Being vague
   * about whether anything was sent is not: this promised an email on a 500.
   */
  it("promises no email when the request did not get through", async () => {
    mockResetPassword.mockRejectedValue(new Error("boom"))
    const wrapper = mountPage()
    await settle()

    await (wrapper.vm as any).onSubmit()
    await settle()

    expect(wrapper.text()).not.toContain("you’ll receive an email")
    expect(wrapper.find('[data-testid="forgot-password-failed-alert"]').exists()).toBe(true)
  })

  it("says nothing about a failure once it has succeeded", async () => {
    const wrapper = mountPage()
    await settle()

    await (wrapper.vm as any).onSubmit()
    await settle()

    expect(wrapper.find('[data-testid="forgot-password-failed-alert"]').exists()).toBe(false)
  })
})
