import {beforeEach, describe, expect, it, vi} from "vitest"
import TwoFactorSetUp from "@/domains/auth/components/TwoFactorSetUp.vue"
import {mountInApp, settle} from "../../pages/helpers"

const {mockStart, mockConfirm, mockFinish} = vi.hoisted(() => ({
  mockStart: vi.fn(),
  mockConfirm: vi.fn(),
  mockFinish: vi.fn(),
}))

vi.mock("@/domains/auth/adapters/accountSecurity", () => ({
  startTwoFactorSetUp: mockStart,
  confirmTwoFactorCode: mockConfirm,
  finishTwoFactorSetUp: mockFinish,
}))

vi.mock("qrcode", () => ({default: {toDataURL: vi.fn(async () => "data:image/png;base64,qr")}}))

describe("setting up two-factor", () => {
  beforeEach(() => vi.clearAllMocks())

  it("goes from the password to the QR code, the backup codes and on", async () => {
    mockStart.mockResolvedValue({ok: true, value: {otpauthUri: "otpauth://totp/x", key: "ABCD"}})
    mockConfirm.mockResolvedValue({ok: true, value: ["aaaaa-bbbbb"]})
    mockFinish.mockResolvedValue({ok: true, value: undefined})
    const wrapper = mountInApp(TwoFactorSetUp)
    const vm = wrapper.vm as any

    vm.password = "Secret123!"
    await vm.start()
    expect(mockStart).toHaveBeenCalledWith("Secret123!")
    expect(vm.stage).toBe("scan")
    expect(vm.qr).toBe("data:image/png;base64,qr")
    expect(vm.password).toBe("")

    vm.code = "123456"
    await vm.confirm()
    expect(vm.stage).toBe("codes")
    expect(vm.codesToSave).toEqual(["aaaaa-bbbbb"])

    await vm.finish()
    await settle()
    expect(wrapper.emitted("done")).toHaveLength(1)
  })

  it("asks for a step-up when replacing an app, and shows any other refusal", async () => {
    mockStart.mockResolvedValueOnce({ok: false, reason: "Confirm it is you first.", needsStepUp: true})
    mockStart.mockResolvedValueOnce({ok: false, reason: "That password is not right.", needsStepUp: false})
    const wrapper = mountInApp(TwoFactorSetUp)
    const vm = wrapper.vm as any

    await vm.start()
    expect(wrapper.emitted("stepUp")).toHaveLength(1)

    await vm.start()
    expect(vm.error).toBe("That password is not right.")
  })
})
