import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import RecoveryUserRow from "@/components/common/rows/RecoveryUserRow.vue"
import EmailPreviewDialog from "@/components/common/modals/EmailPreviewDialog.vue"
import {settle} from "../../../helpers/testUtils"

const {
  mockResendRecoveryMail,
  mockRequestPasswordReset,
  mockRestoreDeletedUser,
  mockHandleNetworkError,
  mockPreviewRecoveryMail,
} = vi.hoisted(() => ({
  mockResendRecoveryMail: vi.fn(),
  mockRequestPasswordReset: vi.fn(),
  mockRestoreDeletedUser: vi.fn(),
  mockHandleNetworkError: vi.fn(),
  mockPreviewRecoveryMail: vi.fn(),
}))

vi.mock("@/domains/recovery", () => ({
  resendRecoveryMail: mockResendRecoveryMail,
  requestPasswordReset: mockRequestPasswordReset,
  restoreDeletedUser: mockRestoreDeletedUser,
  previewRecoveryMail: mockPreviewRecoveryMail,
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
  user: Record<string, unknown> = emma,
) {
  return mount(RecoveryUserRow, {props: {user, actionType, pendingActivation}})
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
    mockResendRecoveryMail.mockResolvedValue(undefined)
    mockRequestPasswordReset.mockResolvedValue(undefined)
    mockRestoreDeletedUser.mockResolvedValue(undefined)
    mockPreviewRecoveryMail.mockResolvedValue({
      subject: "Activate your Account",
      html: "<p>hello</p>",
      recipientEmail: "emma@example.com",
      recipientName: "Emma",
      linkPlaceholder: "PREVIEW-ONLY-NO-TOKEN-ISSUED",
    })
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

      expect(mockPreviewRecoveryMail).toHaveBeenCalledWith(1, "MEMBER_ACTIVATION")
      expect(mockResendRecoveryMail).not.toHaveBeenCalled()
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

      expect(mockResendRecoveryMail).toHaveBeenCalledWith(1, "MEMBER_ACTIVATION")
      expect(wrapper.emitted("action:done")).toHaveLength(1)
    })

    it("a password reset still goes by username, which needs no elevated permission", async () => {
      const wrapper = row("password")
      await openEmail(wrapper, "PASSWORD_RESET")

      await confirmInDialog(wrapper)

      expect(mockRequestPasswordReset).toHaveBeenCalledWith("emma")
      expect(mockResendRecoveryMail).not.toHaveBeenCalled()
    })

    it("the dialog closes once the email has gone", async () => {
      const wrapper = row("activation", "USER_ACTIVATION")
      await openEmail(wrapper, "USER_ACTIVATION")
      expect(wrapper.findComponent(EmailPreviewDialog).props("modelValue")).toBe(true)

      await confirmInDialog(wrapper)

      expect(wrapper.findComponent(EmailPreviewDialog).props("modelValue")).toBe(false)
    })

    it("a failed send is reported and nothing is claimed to have happened", async () => {
      mockResendRecoveryMail.mockRejectedValue(new Error("boom"))
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

    expect(mockRestoreDeletedUser).toHaveBeenCalledWith(1)
  })

  it("reports a refused restore, and leaves the row where it was", async () => {
    mockRestoreDeletedUser.mockRejectedValue(new Error("refused"))
    const wrapper = row("restore")

    await wrapper.find('[data-testid="recovery-user-action-btn-restore-1"]').trigger("click")
    await settle()

    expect(mockHandleNetworkError).toHaveBeenCalled()
    expect(wrapper.emitted("action:done")).toBeUndefined()
  })

  // The window is what is left of the fortnight a deleted account can be brought back in.
  it.each([
    [3, "3 days left", true],
    [1, "1 day left", true],
    // A fortnight left is not something to hurry over, so the chip is drawn plain.
    [12, "12 days left", false],
  ])("says how long the restore window has left, at %s days", async (days, expected, urgent) => {
    const until = new Date(Date.now() + (days - 0.5) * 24 * 60 * 60 * 1000).toISOString()
    const wrapper = row("restore", null, {...emma, restoreUntilAt: until})

    expect(wrapper.text()).toContain(expected)
    expect((wrapper.vm as any).restoreWindowUrgent).toBe(urgent)
  })

  it("marks no urgency for an account with no deadline on it", () => {
    const wrapper = row("restore")

    expect((wrapper.vm as any).restoreWindowUrgent).toBe(false)
  })

  it("restores nothing twice while the first restore is still going", async () => {
    let release: () => void = () => undefined
    mockRestoreDeletedUser.mockReturnValue(new Promise<void>((resolve) => {
      release = () => resolve()
    }))
    const wrapper = row("restore")

    void (wrapper.vm as any).restore()
    void (wrapper.vm as any).restore()
    release()
    await settle()

    expect(mockRestoreDeletedUser).toHaveBeenCalledTimes(1)
  })

  // A purpose the row has no wording for still has to say what the button does.
  it("falls back to Send for an activation it has no wording for", () => {
    const wrapper = row("activation", "SIGNUP_CONTINUATION")

    expect(wrapper.find('[data-testid="recovery-user-send-btn-SIGNUP_CONTINUATION-1"]').text()).toBe("Send")
  })

  it("closes the preview when the dialog is dismissed", async () => {
    const wrapper = row("activation", "USER_ACTIVATION")
    await openEmail(wrapper, "USER_ACTIVATION")

    wrapper.findComponent(EmailPreviewDialog).vm.$emit("update:modelValue", false)
    await settle()

    expect((wrapper.vm as any).previewOpen).toBe(false)
  })

  it("says nothing about a window for an account with no deadline on it", () => {
    const wrapper = row("restore")

    expect(wrapper.text()).not.toContain("left")
  })

  // Nothing to send means nothing to read either, so the dialog stays shut.
  it("offers no email to an account no activation applies to", async () => {
    const wrapper = row("activation", null)

    expect(wrapper.find('[data-testid="recovery-user-send-btn-USER_ACTIVATION-1"]').exists()).toBe(false)
    await (wrapper.vm as any).openEmail()
    await settle()

    expect(mockPreviewRecoveryMail).not.toHaveBeenCalled()
  })

  it("sends nothing twice while the first send is still going", async () => {
    const wrapper = row("activation", "USER_ACTIVATION")
    await openEmail(wrapper, "USER_ACTIVATION")

    let release: () => void = () => undefined
    mockResendRecoveryMail.mockReturnValue(new Promise<void>((resolve) => {
      release = () => resolve()
    }))

    await confirmInDialog(wrapper)
    await confirmInDialog(wrapper)
    release()
    await settle()

    expect(mockResendRecoveryMail).toHaveBeenCalledTimes(1)
  })
})
