import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import EmailConfirmationPanel from "@/components/form/EmailConfirmationPanel.vue"

const {mockCorrectEmail, mockResend, mockStore, mockHandleNetworkError, mockGoto} = vi.hoisted(() => ({
  mockCorrectEmail: vi.fn(),
  mockResend: vi.fn(),
  mockStore: {commit: vi.fn(), getters: {}},
  mockHandleNetworkError: vi.fn(),
  mockGoto: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  correctEmail: mockCorrectEmail,
  resendUserActivation: mockResend,
}))
vi.mock("@/plugins/store", () => ({default: mockStore}))
vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockHandleNetworkError}))
vi.mock("@/plugins/goto", () => ({$goto: mockGoto}))

const mountPanel = (props: Record<string, unknown> = {}) =>
  shallowMount(EmailConfirmationPanel, {
    props: {email: "lena@example.com", username: "lena", continuationToken: "sel.ver", ...props},
  })

type Panel = {
  correcting: boolean
  correctedEmail: string
  startCorrecting: () => void
  correctEmailAddress: () => Promise<void>
  resend: () => Promise<void>
}

describe("EmailConfirmationPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCorrectEmail.mockResolvedValue({})
    mockResend.mockResolvedValue({})
  })

  describe("correcting a mistyped address", () => {
    it("prefills the field with the address it was sent to", () => {
      const wrapper = mountPanel()
      const vm = wrapper.vm as unknown as Panel

      vm.startCorrecting()

      expect(vm.correctedEmail).toBe("lena@example.com")
      expect(vm.correcting).toBe(true)
    })

    it("sends the correction on the continuation token", async () => {
      const wrapper = mountPanel()
      const vm = wrapper.vm as unknown as Panel
      vm.correctedEmail = "corrected@example.com"

      await vm.correctEmailAddress()

      expect(mockCorrectEmail).toHaveBeenCalledWith({
        headers: {"X-Signup-Token": "sel.ver"},
        body: {email: "corrected@example.com"},
        throwOnError: true,
      })
      expect(vm.correcting).toBe(false)
      expect(wrapper.emitted("email-corrected")).toEqual([["corrected@example.com"]])
    })

    it("surfaces a refused correction and keeps the form open", async () => {
      mockCorrectEmail.mockRejectedValue(new Error("taken"))
      const wrapper = mountPanel()
      const vm = wrapper.vm as unknown as Panel
      vm.startCorrecting()
      vm.correctedEmail = "taken@example.com"

      await vm.correctEmailAddress()

      expect(mockHandleNetworkError).toHaveBeenCalled()
      expect(vm.correcting).toBe(true)
      expect(wrapper.emitted("email-corrected")).toBeUndefined()
    })

    it("sends nothing when the field is empty", async () => {
      const wrapper = mountPanel()
      const vm = wrapper.vm as unknown as Panel
      vm.correctedEmail = ""

      await vm.correctEmailAddress()

      expect(mockCorrectEmail).not.toHaveBeenCalled()
    })

    it("sends nothing without a continuation token", async () => {
      const wrapper = mountPanel({continuationToken: undefined})
      const vm = wrapper.vm as unknown as Panel
      vm.correctedEmail = "corrected@example.com"

      await vm.correctEmailAddress()

      expect(mockCorrectEmail).not.toHaveBeenCalled()
    })
  })

  describe("asking for the email again", () => {
    it("resends to the address on file", async () => {
      const wrapper = mountPanel()

      await (wrapper.vm as unknown as Panel).resend()

      expect(mockResend).toHaveBeenCalledWith({path: {username: "lena"}, throwOnError: true})
      expect(mockStore.commit).toHaveBeenCalledWith(
        "setStatusSnackbarMessage",
        "Confirmation sent to lena@example.com",
      )
    })

    it("surfaces a refused resend", async () => {
      mockResend.mockRejectedValue(new Error("too many"))
      const wrapper = mountPanel()

      await (wrapper.vm as unknown as Panel).resend()

      expect(mockHandleNetworkError).toHaveBeenCalled()
    })

    it("asks for nothing when there is no account to ask about", async () => {
      const wrapper = mountPanel({username: ""})

      await (wrapper.vm as unknown as Panel).resend()

      expect(mockResend).not.toHaveBeenCalled()
    })
  })

  describe("what it offers", () => {
    it("names the address and what confirming brings about", () => {
      const wrapper = mountPanel({confirmationConsequence: "Your membership starts as soon as you do."})

      expect(wrapper.text()).toContain("lena@example.com")
      expect(wrapper.text()).toContain("Your membership starts as soon as you do.")
    })

    it("offers a way back, the same as every other step", async () => {
      const wrapper = mountPanel()

      await wrapper.find('[data-testid="email-confirm-back-btn"]').trigger("click")

      expect(wrapper.emitted("back")).toHaveLength(1)
    })

    it("sends the applicant to sign in", async () => {
      const wrapper = mountPanel()

      await wrapper.find('[data-testid="email-confirm-sign-in-btn"]').trigger("click")

      expect(mockGoto).toHaveBeenCalledWith("/login")
    })

    it("shows only the correction form while an address is being typed", async () => {
      const wrapper = mountPanel()
      ;(wrapper.vm as unknown as Panel).startCorrecting()
      await wrapper.vm.$nextTick()

      expect(wrapper.find('[data-testid="email-confirm-back-btn"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="email-confirm-resend-btn"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="email-confirm-correct-form"]').exists()).toBe(true)
    })
  })
})
