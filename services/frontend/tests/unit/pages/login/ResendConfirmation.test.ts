import {beforeEach, describe, expect, it, vi} from "vitest"
import ResendConfirmation from "@/pages/login/ResendConfirmation.vue"
import {mountInApp, settle} from "../helpers"
import {clearEveryField, saidByLabel} from "../../helpers/fields"

const {
  mockRoute,
  mockResendActivation,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockRoute: {
    query: {username: "alice"},
  },
  mockResendActivation: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...actual,
    useRoute: () => mockRoute,
  }
})

vi.mock("@/domains/recovery", () => ({
  resendActivation: mockResendActivation,
}))

vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockHandleNetworkError}))

function mountPage() {
  return mountInApp(ResendConfirmation)
}

const field = (wrapper: ReturnType<typeof mountPage>) => wrapper.getComponent({name: "FormControl"})
const send = async (wrapper: ReturnType<typeof mountPage>) => {
  await wrapper.get('[data-testid="resend-confirmation-form"]').trigger("submit")
  await settle()
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

    expect(field(wrapper).props("modelValue")).toBe("alice")
  })

  it("asks for a fresh confirmation link", async () => {
    const wrapper = mountPage()
    await settle()

    await wrapper.get('[data-testid="resend-confirmation-form"]').trigger("submit")
    await settle()

    expect(mockResendActivation).toHaveBeenCalledWith("alice")
  })

  // Saying whether the account exists would turn this into a way of finding out who has one,
  // so the domain answers "sent" either way and this page shows that one message. Which
  // requests come back "sent" is the domain's own test.
  it("promises the email once the domain says it went", async () => {
    const wrapper = mountPage()
    await settle()

    await send(wrapper)

    expect(wrapper.text()).toContain("you’ll receive an email")
  })

  // Claiming a mail was sent when the api refused to send one is the same silence this
  // page exists to remove, and a refusal to send is about the caller, not the account.
  it("does not claim a mail was sent when the api refused to send one", async () => {
    mockResendActivation.mockResolvedValue({outcome: "rate-limited", cause: {response: {status: 429}}})
    const wrapper = mountPage()
    await settle()

    await send(wrapper)

    expect(mockHandleNetworkError).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="resend-confirmation-form-state"]').exists()).toBe(true)
  })

  it("offers the form again to somebody who has not asked yet", async () => {
    const wrapper = mountPage()
    await settle()

    expect(wrapper.find('[data-testid="resend-confirmation-form-state"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="resend-confirmation-success-state"]').exists()).toBe(false)
  })

  it("takes the username as typed, and asks for one before sending anything", async () => {
    mockRoute.query = {}
    const wrapper = mountPage()
    await settle()

    await send(wrapper)
    expect(mockResendActivation).not.toHaveBeenCalled()
    expect(field(wrapper).props("errorMessages")).toEqual(["This field is required"])

    field(wrapper).vm.$emit("update:modelValue", "bob")
    await send(wrapper)
    expect(mockResendActivation).toHaveBeenCalledWith("bob")
  })

  it("says a field left empty is required once it is left", async () => {
    const wrapper = mountInApp(ResendConfirmation)
    await settle()

    await clearEveryField(wrapper)

    expect(saidByLabel(wrapper)).toMatchObject({"Username": ["This field is required"]})
  })
})
