import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import RecoveryUserRow from "@/components/common/rows/RecoveryUserRow.vue"

const {
  mockResendUserActivation,
  mockResetPassword,
  mockRestoreDeletedUserById,
  mockHandleNetworkError,
  mockPreviewRecoveryEmail,
} = vi.hoisted(() => ({
  mockResendUserActivation: vi.fn(),
  mockResetPassword: vi.fn(),
  mockRestoreDeletedUserById: vi.fn(),
  mockHandleNetworkError: vi.fn(),
  mockPreviewRecoveryEmail: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  resendUserActivation: mockResendUserActivation,
  resetPassword: mockResetPassword,
  restoreDeletedUserById: mockRestoreDeletedUserById,
  previewRecoveryEmail: mockPreviewRecoveryEmail,
  // The row picks the preview purpose from the generated enum, so the mock carries it.
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

describe("RecoveryUserRow", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockResendUserActivation.mockResolvedValue({})
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

  it("dispatches activation and password recovery actions", async () => {
    const activation = mount(RecoveryUserRow, {
      props: {
        user: {id: 1, fullName: "Emma", username: "emma", enabled: false},
        actionType: "activation",
      },
    })

    await (activation.vm as any).handleAction()
    expect(mockResendUserActivation).toHaveBeenCalledWith({
      path: {username: "emma"},
      throwOnError: true,
    })

    const password = mount(RecoveryUserRow, {
      props: {
        user: {id: 2, fullName: "Viktor", username: "viktor", enabled: true},
        actionType: "password",
      },
    })

    await (password.vm as any).handleAction()
    expect(mockResetPassword).toHaveBeenCalledWith({
      path: {username: "viktor"},
      throwOnError: true,
    })

    const restore = mount(RecoveryUserRow, {
      props: {
        user: {id: 3, fullName: "Rest Ored", username: "restored", enabled: false},
        actionType: "restore",
      },
    })

    await (restore.vm as any).handleAction()
    expect(mockRestoreDeletedUserById).toHaveBeenCalledWith({
      path: {userId: 3},
      throwOnError: true,
    })
  })

  it("forwards errors to network error handler", async () => {
    mockResendUserActivation.mockRejectedValueOnce(new Error("network"))

    const wrapper = mount(RecoveryUserRow, {
      props: {
        user: {id: 1, fullName: "Emma", username: "emma", enabled: false},
        actionType: "activation",
      },
    })

    await (wrapper.vm as any).handleAction()
    expect(mockHandleNetworkError).toHaveBeenCalled()
  })

  it("offers a preview for the two actions that send an email", () => {
    const purposeFor = {activation: "USER_ACTIVATION", password: "PASSWORD_RESET"} as const

    for (const [actionType, purpose] of Object.entries(purposeFor)) {
      const wrapper = mount(RecoveryUserRow, {
        props: {
          user: {id: 4, fullName: "Emma", username: "emma", enabled: false},
          actionType: actionType as "activation" | "password",
        },
      })

      expect(wrapper.find('[data-testid="recovery-user-preview-btn-4"]').exists()).toBe(true)
      expect((wrapper.vm as any).previewPurpose).toBe(purpose)
    }
  })

  it("offers no preview for restoring a user, which sends nothing", () => {
    const wrapper = mount(RecoveryUserRow, {
      props: {
        user: {id: 5, fullName: "Emma", username: "emma", enabled: false},
        actionType: "restore",
      },
    })

    expect(wrapper.find('[data-testid="recovery-user-preview-btn-5"]').exists()).toBe(false)
  })

  it("asks for the preview of the purpose its button stands for", async () => {
    const wrapper = mount(RecoveryUserRow, {
      props: {
        user: {id: 6, fullName: "Emma", username: "emma", enabled: false},
        actionType: "password",
      },
    })

    await wrapper.find('[data-testid="recovery-user-preview-btn-6"]').trigger("click")

    expect(mockPreviewRecoveryEmail).toHaveBeenCalledWith({
      path: {userId: 6},
      query: {purpose: "PASSWORD_RESET"},
    })
  })

  it("previewing does not send anything", async () => {
    const wrapper = mount(RecoveryUserRow, {
      props: {
        user: {id: 7, fullName: "Emma", username: "emma", enabled: false},
        actionType: "activation",
      },
    })

    await wrapper.find('[data-testid="recovery-user-preview-btn-7"]').trigger("click")

    expect(mockResendUserActivation).not.toHaveBeenCalled()
    expect(mockResetPassword).not.toHaveBeenCalled()
  })
})
