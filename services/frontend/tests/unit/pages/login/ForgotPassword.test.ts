import {beforeEach, describe, expect, it, vi} from "vitest"
import ForgotPassword from "@/pages/login/ForgotPassword.vue"
import {mountInApp, settle} from "../helpers"
import {clearEveryField, saidByLabel} from "../../helpers/fields"

const {
  mockRoute,
  mockRequestPasswordReset,
} = vi.hoisted(() => ({
  mockRoute: {
    query: {username: "alice"},
  },
  mockRequestPasswordReset: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...actual,
    useRoute: () => mockRoute,
  }
})

vi.mock("@/domains/recovery", () => ({
  requestPasswordReset: mockRequestPasswordReset,
}))

describe("ForgotPassword page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.query = {username: "alice"}
    mockRequestPasswordReset.mockResolvedValue(undefined)
  })

  const mountPage = () => mountInApp(ForgotPassword)
  const field = (wrapper: ReturnType<typeof mountPage>) => wrapper.getComponent({name: "FormControl"})

  it("prefills username from query and asks for the reset", async () => {
    const wrapper = mountPage()
    await settle()

    expect(field(wrapper).props("modelValue")).toBe("alice")

    await wrapper.get('[data-testid="forgot-password-form"]').trigger("submit")
    await settle()

    expect(mockRequestPasswordReset).toHaveBeenCalledWith("alice")
    expect(wrapper.text()).toContain("you’ll receive an email")
  })

  /**
   * Being vague about whether the account exists is the point, and it stays. Being vague
   * about whether anything was sent is not: this promised an email on a 500.
   */
  it("promises no email when the request did not get through", async () => {
    mockRequestPasswordReset.mockRejectedValue(new Error("boom"))
    const wrapper = mountPage()
    await settle()

    await wrapper.get('[data-testid="forgot-password-form"]').trigger("submit")
    await settle()

    expect(wrapper.text()).not.toContain("you’ll receive an email")
    expect(wrapper.find('[data-testid="forgot-password-failed-alert"]').exists()).toBe(true)
  })

  it("says nothing about a failure once it has succeeded", async () => {
    const wrapper = mountPage()
    await settle()

    await wrapper.get('[data-testid="forgot-password-form"]').trigger("submit")
    await settle()

    expect(wrapper.find('[data-testid="forgot-password-failed-alert"]').exists()).toBe(false)
  })

  it("takes the username as typed, and asks for one before sending anything", async () => {
    mockRoute.query = {}
    const wrapper = mountPage()
    await settle()

    await wrapper.get('[data-testid="forgot-password-form"]').trigger("submit")
    await settle()
    expect(mockRequestPasswordReset).not.toHaveBeenCalled()
    expect(field(wrapper).props("errorMessages")).toEqual(["This field is required"])

    field(wrapper).vm.$emit("update:modelValue", "bob")
    await wrapper.get('[data-testid="forgot-password-form"]').trigger("submit")
    await settle()
    expect(mockRequestPasswordReset).toHaveBeenCalledWith("bob")
  })

  it("says a field left empty is required once it is left", async () => {
    const wrapper = mountInApp(ForgotPassword)
    await settle()

    await clearEveryField(wrapper)

    expect(saidByLabel(wrapper)).toMatchObject({"Username": ["This field is required"]})
  })
})
