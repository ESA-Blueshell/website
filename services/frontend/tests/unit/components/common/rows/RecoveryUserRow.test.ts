import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import RecoveryUserRow from "@/components/common/rows/RecoveryUserRow.vue"
import EmailPreviewDialog from "@/components/common/modals/EmailPreviewDialog.vue"
import {settle} from "../../../helpers/testUtils"

const {
  mockResendRecoveryEmail,
  mockResetPassword,
  mockRestoreDeletedUserById,
  mockHandleNetworkError,
  mockPreviewRecoveryEmail,
} = vi.hoisted(() => ({
  mockResendRecoveryEmail: vi.fn(),
  mockResetPassword: vi.fn(),
  mockRestoreDeletedUserById: vi.fn(),
  mockHandleNetworkError: vi.fn(),
  mockPreviewRecoveryEmail: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  resendRecoveryEmail: mockResendRecoveryEmail,
  resetPassword: mockResetPassword,
  restoreDeletedUserById: mockRestoreDeletedUserById,
  previewRecoveryEmail: mockPreviewRecoveryEmail,
  TokenPurpose: {
    USER_ACTIVATION: "USER_ACTIVATION",
    MEMBER_ACTIVATION: "MEMBER_ACTIVATION",
    PASSWORD_RESET: "PASSWORD_RESET",
    SIGNUP_CONTINUATION: "SIGNUP_CONTINUATION",
  },
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({$handleNetworkError: mockHandleNetworkError}))

const emma = {id: 1, fullName: "Emma", username: "emma", enabled: false}

function row(
  actionType: "activation" | "password" | "restore",
  pendingActivation: string | null = null,
) {
  return mount(RecoveryUserRow, {props: {user: emma, actionType, pendingActivation}})
}

/** Click the row's one send button, which opens the email rather than sending it. */
async function openEmail(wrapper: ReturnType<typeof row>, purpose: string) {
  await wrapper.find(`[data-testid="recovery-user-send-btn-${purpose}-1"]`).trigger("click")
  await settle()
}

/** The dialog carries the real send. */
async function confirmInDialog(wrapper: ReturnType<typeof row>) {
  wrapper.findComponent(EmailPreviewDialog).vm.$emit("confirm")
  await settle()
}

describe("RecoveryUserRow", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockResendRecoveryEmail.mockResolvedValue({})
    mockResetPassword.mockResolvedValue({})
    mockRestoreDeletedUserById.mockResolvedValue({})
    mockPreviewRecoveryEmail.mockResolvedValue({data: {
      subject: "Activate your Account",
      html: "<p>hello</p>",
      recipientEmail: "emma@example.com",
      recipientName: "Emma",
      linkPlaceholder: "PREVIEW-ONLY-NO-TOKEN-ISSUED",
    }})
  })

  describe("choosing which email applies", () => {
    it("offers the member activation to an account the board created", () => {
      const wrapper = row("activation", "MEMBER_ACTIVATION")

      expect(wrapper.find('[data-testid="recovery-user-send-btn-MEMBER_ACTIVATION-1"]').text())
        .toContain("Resend Member Activation")
      // Only the one that applies; the other is not a choice the operator has to make.
      expect(wrapper.find('[data-testid="recovery-user-send-btn-USER_ACTIVATION-1"]').exists()).toBe(false)
    })

    it("offers the ordinary activation to an account that signed itself up", () => {
      const wrapper = row("activation", "USER_ACTIVATION")

      expect(wrapper.find('[data-testid="recovery-user-send-btn-USER_ACTIVATION-1"]').text())
        .toContain("Resend Activation")
      expect(wrapper.find('[data-testid="recovery-user-send-btn-MEMBER_ACTIVATION-1"]').exists()).toBe(false)
    })

    it("offers nothing when the server names no activation", () => {
      // The inactive panel also lists deleted accounts, which the server leaves out of the
      // map on purpose. Guessing the ordinary activation put a send button on those.
      const wrapper = row("activation", null)

      expect(wrapper.findAll('[data-testid^="recovery-user-send-btn-"]')).toHaveLength(0)
    })

    it("offers only the password reset on an active account", () => {
      const wrapper = row("password")

      expect(wrapper.find('[data-testid="recovery-user-send-btn-PASSWORD_RESET-1"]').exists()).toBe(true)
      expect(wrapper.findAll('[data-testid^="recovery-user-send-btn-USER"]')).toHaveLength(0)
    })

    it("offers no email for a deleted user, because restoring sends none", () => {
      const wrapper = row("restore")

      expect(wrapper.findAll('[data-testid^="recovery-user-send-btn-"]')).toHaveLength(0)
      expect(wrapper.find('[data-testid="recovery-user-action-btn-restore-1"]').exists()).toBe(true)
    })
  })

  describe("reading the email is how it is sent", () => {
    it("the button renders the email rather than sending it", async () => {
      const wrapper = row("activation", "MEMBER_ACTIVATION")

      await openEmail(wrapper, "MEMBER_ACTIVATION")

      expect(mockPreviewRecoveryEmail).toHaveBeenCalledWith({
        path: {userId: 1},
        query: {purpose: "MEMBER_ACTIVATION"},
      })
      expect(mockResendRecoveryEmail).not.toHaveBeenCalled()
    })

    it("the dialog offers to send the email that was read", async () => {
      const wrapper = row("activation", "MEMBER_ACTIVATION")
      await openEmail(wrapper, "MEMBER_ACTIVATION")

      const dialog = wrapper.findComponent(EmailPreviewDialog)
      expect(dialog.props("confirmLabel")).toBe("Resend Member Activation")
    })

    it("confirming sends the same email that was read", async () => {
      const wrapper = row("activation", "MEMBER_ACTIVATION")
      await openEmail(wrapper, "MEMBER_ACTIVATION")

      await confirmInDialog(wrapper)

      expect(mockResendRecoveryEmail).toHaveBeenCalledWith({
        path: {userId: 1},
        query: {purpose: "MEMBER_ACTIVATION"},
        throwOnError: true,
      })
      expect(wrapper.emitted("action:done")).toHaveLength(1)
    })

    it("a password reset still goes by username, which needs no elevated permission", async () => {
      const wrapper = row("password")
      await openEmail(wrapper, "PASSWORD_RESET")

      await confirmInDialog(wrapper)

      expect(mockResetPassword).toHaveBeenCalledWith({path: {username: "emma"}, throwOnError: true})
      expect(mockResendRecoveryEmail).not.toHaveBeenCalled()
    })

    it("the dialog closes once the email has gone", async () => {
      const wrapper = row("activation", "USER_ACTIVATION")
      await openEmail(wrapper, "USER_ACTIVATION")
      expect(wrapper.findComponent(EmailPreviewDialog).props("modelValue")).toBe(true)

      await confirmInDialog(wrapper)

      expect(wrapper.findComponent(EmailPreviewDialog).props("modelValue")).toBe(false)
    })

    it("a failed send is reported and nothing is claimed to have happened", async () => {
      mockResendRecoveryEmail.mockRejectedValue(new Error("boom"))
      const wrapper = row("activation", "USER_ACTIVATION")
      await openEmail(wrapper, "USER_ACTIVATION")

      await confirmInDialog(wrapper)

      expect(mockHandleNetworkError).toHaveBeenCalled()
      expect(wrapper.emitted("action:done")).toBeUndefined()
    })
  })

  it("restores a deleted user", async () => {
    const wrapper = row("restore")

    await wrapper.find('[data-testid="recovery-user-action-btn-restore-1"]').trigger("click")
    await settle()

    expect(mockRestoreDeletedUserById).toHaveBeenCalledWith({path: {userId: 1}, throwOnError: true})
  })
})
