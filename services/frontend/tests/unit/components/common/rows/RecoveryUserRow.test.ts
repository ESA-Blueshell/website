import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import RecoveryUserRow from "@/components/common/rows/RecoveryUserRow.vue"

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
  // The row picks purposes off the generated enum rather than restating the strings.
  TokenPurpose: {
    USER_ACTIVATION: "USER_ACTIVATION",
    MEMBER_ACTIVATION: "MEMBER_ACTIVATION",
    PASSWORD_RESET: "PASSWORD_RESET",
    SIGNUP_CONTINUATION: "SIGNUP_CONTINUATION",
  },
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

const emma = {id: 1, fullName: "Emma", username: "emma", enabled: false}

function row(actionType: "activation" | "password" | "restore", user = emma) {
  return mount(RecoveryUserRow, {props: {user, actionType}})
}

describe("RecoveryUserRow", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockResendRecoveryEmail.mockResolvedValue({})
    mockResetPassword.mockResolvedValue({})
    mockRestoreDeletedUserById.mockResolvedValue({})
    mockPreviewRecoveryEmail.mockResolvedValue({data: {
      purpose: "USER_ACTIVATION",
      subject: "Activate your Account",
      html: "<p>hello</p>",
      recipientEmail: "emma@example.com",
      recipientName: "Emma",
      linkPlaceholder: "PREVIEW-ONLY-NO-TOKEN-ISSUED",
    }})
  })

  describe("an inactive account", () => {
    it("offers both activation emails, because the list cannot tell which applies", () => {
      const wrapper = row("activation")

      expect(wrapper.find('[data-testid="recovery-user-send-btn-USER_ACTIVATION-1"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="recovery-user-send-btn-MEMBER_ACTIVATION-1"]').exists()).toBe(true)
    })

    it("sends the member activation when that is the one asked for", async () => {
      const wrapper = row("activation")

      await wrapper.find('[data-testid="recovery-user-send-btn-MEMBER_ACTIVATION-1"]').trigger("click")

      expect(mockResendRecoveryEmail).toHaveBeenCalledWith({
        path: {userId: 1},
        query: {purpose: "MEMBER_ACTIVATION"},
        throwOnError: true,
      })
    })

    it("sends the user activation when that is the one asked for", async () => {
      const wrapper = row("activation")

      await wrapper.find('[data-testid="recovery-user-send-btn-USER_ACTIVATION-1"]').trigger("click")

      expect(mockResendRecoveryEmail).toHaveBeenCalledWith({
        path: {userId: 1},
        query: {purpose: "USER_ACTIVATION"},
        throwOnError: true,
      })
    })

    it("previews either one without sending it", async () => {
      const wrapper = row("activation")

      await wrapper.find('[data-testid="recovery-user-preview-btn-MEMBER_ACTIVATION-1"]').trigger("click")

      expect(mockPreviewRecoveryEmail).toHaveBeenCalledWith({
        path: {userId: 1},
        query: {purpose: "MEMBER_ACTIVATION"},
      })
      expect(mockResendRecoveryEmail).not.toHaveBeenCalled()
    })

    it("reports the send upwards so the lists reload", async () => {
      const wrapper = row("activation")

      await wrapper.find('[data-testid="recovery-user-send-btn-USER_ACTIVATION-1"]').trigger("click")
      await Promise.resolve()

      expect(wrapper.emitted("action:done")).toHaveLength(1)
    })
  })

  describe("an active account", () => {
    it("offers only the password reset, and previews it", async () => {
      const wrapper = row("password")

      expect(wrapper.find('[data-testid="recovery-user-send-btn-USER_ACTIVATION-1"]').exists()).toBe(false)
      await wrapper.find('[data-testid="recovery-user-preview-btn-PASSWORD_RESET-1"]').trigger("click")

      expect(mockPreviewRecoveryEmail).toHaveBeenCalledWith({
        path: {userId: 1},
        query: {purpose: "PASSWORD_RESET"},
      })
    })

    it("sends the reset by username, which needs no elevated permission", async () => {
      const wrapper = row("password")

      await wrapper.find('[data-testid="recovery-user-send-btn-PASSWORD_RESET-1"]').trigger("click")

      expect(mockResetPassword).toHaveBeenCalledWith({path: {username: "emma"}, throwOnError: true})
      expect(mockResendRecoveryEmail).not.toHaveBeenCalled()
    })
  })

  describe("a deleted user", () => {
    it("offers restore and no email at all, because restoring sends none", () => {
      const wrapper = row("restore")

      expect(wrapper.find('[data-testid="recovery-user-action-btn-restore-1"]').exists()).toBe(true)
      expect(wrapper.findAll('[data-testid^="recovery-user-preview-btn-"]')).toHaveLength(0)
      expect(wrapper.findAll('[data-testid^="recovery-user-send-btn-"]')).toHaveLength(0)
    })

    it("restores the user", async () => {
      const wrapper = row("restore")

      await wrapper.find('[data-testid="recovery-user-action-btn-restore-1"]').trigger("click")

      expect(mockRestoreDeletedUserById).toHaveBeenCalledWith({path: {userId: 1}, throwOnError: true})
    })
  })

  it("forwards a failed send to the network error handler", async () => {
    mockResendRecoveryEmail.mockRejectedValue(new Error("boom"))
    const wrapper = row("activation")

    await wrapper.find('[data-testid="recovery-user-send-btn-USER_ACTIVATION-1"]').trigger("click")
    await Promise.resolve()

    expect(mockHandleNetworkError).toHaveBeenCalled()
    expect(wrapper.emitted("action:done")).toBeUndefined()
  })

  it("forwards a failed restore to the network error handler", async () => {
    mockRestoreDeletedUserById.mockRejectedValue(new Error("boom"))
    const wrapper = row("restore")

    await wrapper.find('[data-testid="recovery-user-action-btn-restore-1"]').trigger("click")
    await Promise.resolve()

    expect(mockHandleNetworkError).toHaveBeenCalled()
  })
})
