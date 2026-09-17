import {beforeEach, describe, expect, it, vi} from "vitest"
import ResendConfirmation from "@/pages/login/ResendConfirmation.vue"
import {mountInApp, settle} from "../helpers"

const {
  mockRoute,
  mockResendActivation,
  mockSetFieldValue,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockRoute: {
    query: {username: "alice"},
  },
  mockResendActivation: vi.fn(),
  mockSetFieldValue: vi.fn(),
  mockHandleNetworkError: vi.fn(),
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

vi.mock("@/domains/recovery", () => ({
  resendActivation: mockResendActivation,
}))

vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockHandleNetworkError}))

function mountPage() {
  return mountInApp(ResendConfirmation, {global: {stubs: {VvField: true}}})
}

describe("ResendConfirmation page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.query = {username: "alice"}
    mockResendActivation.mockResolvedValue({outcome: "sent"})
  })

  it("carries the username over from the login page they came from", async () => {
    const wrapper = mountPage()
    await settle()

    expect(mockSetFieldValue).toHaveBeenCalledWith("username", "alice")
    expect((wrapper.vm as any).form.username).toBe("alice")
  })

  it("asks for a fresh confirmation link", async () => {
    const wrapper = mountPage()
    await settle()

    await (wrapper.vm as any).onSubmit()

    expect(mockResendActivation).toHaveBeenCalledWith("alice")
  })

  // Saying whether the account exists would turn this into a way of finding out who has one,
  // so the domain answers "sent" either way and this page shows that one message. Which
  // requests come back "sent" is the domain's own test.
  it("promises the email once the domain says it went", async () => {
    const wrapper = mountPage()
    await settle()

    await (wrapper.vm as any).onSubmit()
    await settle()

    expect(wrapper.text()).toContain("you’ll receive an email")
  })

  // Claiming a mail was sent when the api refused to send one is the same silence this
  // page exists to remove, and a refusal to send is about the caller, not the account.
  it("does not claim a mail was sent when the api refused to send one", async () => {
    mockResendActivation.mockResolvedValue({outcome: "rate-limited", cause: {response: {status: 429}}})
    const wrapper = mountPage()
    await settle()

    await (wrapper.vm as any).onSubmit()
    await settle()

    expect(mockHandleNetworkError).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="resend-confirmation-form-state"]').exists()).toBe(true)
  })

  it("offers the form again to somebody who has not asked yet", async () => {
    const wrapper = mountPage()
    await settle()

    expect(wrapper.find('[data-testid="resend-confirmation-form-state"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="resend-confirmation-success-state"]').exists()).toBe(false)
  })
})
