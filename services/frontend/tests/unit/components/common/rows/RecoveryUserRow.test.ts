import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import RecoveryUserRow from "@/components/common/rows/RecoveryUserRow.vue"

const {mockResendUserActivation, mockResetPassword, mockRestoreDeletedUserById, mockHandleNetworkError} = vi.hoisted(() => ({
  mockResendUserActivation: vi.fn(),
  mockResetPassword: vi.fn(),
  mockRestoreDeletedUserById: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  resendUserActivation: mockResendUserActivation,
  resetPassword: mockResetPassword,
  restoreDeletedUserById: mockRestoreDeletedUserById,
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
})
