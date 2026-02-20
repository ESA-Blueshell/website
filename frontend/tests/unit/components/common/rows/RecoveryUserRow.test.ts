import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import RecoveryUserRow from "@/components/common/rows/RecoveryUserRow.vue"

const {mockResendUserActivation, mockResetPassword, mockHandleNetworkError} = vi.hoisted(() => ({
  mockResendUserActivation: vi.fn(),
  mockResetPassword: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  resendUserActivation: mockResendUserActivation,
  resetPassword: mockResetPassword,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

describe("RecoveryUserRow", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockResendUserActivation.mockResolvedValue({})
    mockResetPassword.mockResolvedValue({})
  })

  it("dispatches activation and password recovery actions", async () => {
    const activation = mount(RecoveryUserRow, {
      props: {
        user: {id: 1, fullName: "Emma", username: "emma", enabled: false},
        actionType: "activation",
      },
    })

    await (activation.vm as any).handleResend()
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

    await (password.vm as any).handleResend()
    expect(mockResetPassword).toHaveBeenCalledWith({
      path: {username: "viktor"},
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

    await (wrapper.vm as any).handleResend()
    expect(mockHandleNetworkError).toHaveBeenCalled()
  })
})
