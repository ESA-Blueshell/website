import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
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

const setUp = (props: Record<string, unknown> = {mode: "voluntary"}) => mountInApp(TwoFactorSetUp, {props})
type SetUp = ReturnType<typeof setUp>
const at = (wrapper: SetUp) => wrapper.get("[aria-current=step]").text()
const press = async (wrapper: SetUp, testid: string) => {
  await wrapper.get(`[data-testid=${testid}]`).trigger("click")
  await settle()
}
const submit = async (wrapper: SetUp) => {
  await wrapper.get("form").trigger("submit")
  await settle()
}

describe("setting up two-factor", () => {
  beforeEach(() => {
    mockStart.mockResolvedValue({ok: true, value: {otpauthUri: "otpauth://totp/x", key: "ABCD"}})
    mockConfirm.mockResolvedValue({ok: true, value: ["aaaaa-bbbbb"]})
    mockFinish.mockResolvedValue({ok: true, value: undefined})
  })

  afterEach(() => vi.unstubAllGlobals())

  it("goes from the password to the QR code, a first code, the backup codes and on", async () => {
    const writeText = vi.fn(async () => undefined)
    vi.stubGlobal("navigator", {clipboard: {writeText}})
    const wrapper = setUp()
    expect(at(wrapper)).toContain("Confirm it is you")
    expect(wrapper.text()).toContain("Step 1 of 4")

    await wrapper.get("[data-testid=two-factor-password-field] input").setValue("Secret123!")
    await submit(wrapper)
    expect(mockStart).toHaveBeenCalledWith("Secret123!")
    expect(at(wrapper)).toContain("Scan the code")
    expect(wrapper.get("[data-testid=two-factor-qr]").attributes("src")).toBe("data:image/png;base64,qr")
    expect(wrapper.get("[data-testid=two-factor-key]").text()).toBe("ABCD")
    await wrapper.findAll("button").find(button => button.text() === "Copy the key")!.trigger("click")
    await settle()
    expect(writeText).toHaveBeenCalledWith("ABCD")

    await press(wrapper, "two-factor-scanned-btn")
    await wrapper.get("[data-testid=two-factor-code-field] input").setValue(" 123456 ")
    await submit(wrapper)
    expect(mockConfirm).toHaveBeenCalledWith("123456")
    expect(at(wrapper)).toContain("Save backup codes")
    expect(wrapper.findAll("[data-testid=backup-code]").map(one => one.text())).toEqual(["aaaaa-bbbbb"])

    expect(wrapper.get("[data-testid=two-factor-finish-btn]").attributes("disabled")).toBeDefined()
    await wrapper.get("[data-testid=two-factor-saved-check] input").setValue(true)
    expect(wrapper.get("[data-testid=two-factor-finish-btn]").text()).toBe("Turn on two-factor")
    await press(wrapper, "two-factor-finish-btn")
    expect(wrapper.emitted("done")).toHaveLength(1)
  })

  it("goes back from the code to the QR code", async () => {
    const wrapper = setUp()
    await wrapper.get("[data-testid=two-factor-password-field] input").setValue("Secret123!")
    await submit(wrapper)
    await press(wrapper, "two-factor-scanned-btn")

    await wrapper.findAll("button").find(button => button.text() === "Back")!.trigger("click")
    await settle()

    expect(at(wrapper)).toContain("Scan the code")
  })

  it("offers a way off only where it was given one", () => {
    expect(setUp({mode: "voluntary", leave: "/account/security"}).get("[data-testid=two-factor-leave-btn]").attributes("to"))
      .toBe("/account/security")
    expect(setUp().find("[data-testid=two-factor-leave-btn]").exists()).toBe(false)
  })

  it("says what replacing changes, and asks for a step-up before it starts", async () => {
    mockStart.mockResolvedValueOnce({ok: false, reason: "Confirm it is you first.", needsStepUp: true})
    const wrapper = setUp({mode: "replace"})
    expect(wrapper.text()).toContain("Your current app keeps working until the new one is set up.")

    await wrapper.get("[data-testid=two-factor-password-field] input").setValue("Secret123!")
    await submit(wrapper)

    expect(wrapper.emitted("stepUp")).toHaveLength(1)
    expect(wrapper.find("[data-testid=two-factor-error]").exists()).toBe(false)
  })

  it("shows a wrong password, a wrong code and a set-up that could not be turned on", async () => {
    mockStart.mockResolvedValueOnce({ok: false, reason: "That password is not right.", needsStepUp: false})
    mockConfirm.mockResolvedValueOnce({ok: false, reason: "That code is not right.", needsStepUp: false})
    mockFinish.mockResolvedValueOnce({ok: false, reason: "Start setting up two-factor again.", needsStepUp: false})
    const wrapper = setUp()

    await wrapper.get("[data-testid=two-factor-password-field] input").setValue("Wrong")
    await submit(wrapper)
    expect(wrapper.get("[data-testid=two-factor-error]").text()).toBe("That password is not right.")

    await submit(wrapper)
    await press(wrapper, "two-factor-scanned-btn")
    await wrapper.get("[data-testid=two-factor-code-field] input").setValue("000000")
    await submit(wrapper)
    expect(wrapper.get("[data-testid=two-factor-error]").text()).toBe("That code is not right.")

    await submit(wrapper)
    await wrapper.get("[data-testid=two-factor-saved-check] input").setValue(true)
    await press(wrapper, "two-factor-finish-btn")
    expect(wrapper.get("[data-testid=two-factor-error]").text()).toBe("Start setting up two-factor again.")
    expect(wrapper.emitted("done")).toBeUndefined()
  })
})

describe("the set-up a granted role is sent to at sign-in", () => {
  beforeEach(() => {
    mockConfirm.mockResolvedValue({ok: true, value: ["aaaaa-bbbbb"]})
    mockFinish.mockResolvedValue({ok: true, value: undefined})
  })

  it("opens at the phone, three steps, on the proof of the sign-in", async () => {
    mockStart.mockResolvedValue({ok: true, value: {otpauthUri: "otpauth://totp/x", key: "ABCD"}})
    const wrapper = setUp({mode: "required"})
    await settle()

    expect(mockStart).toHaveBeenCalledWith(undefined)
    expect(at(wrapper)).toContain("Scan the code")
    expect(wrapper.text()).toContain("Step 1 of 3")
    expect(wrapper.find("[data-testid=two-factor-password-field]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=two-factor-leave-btn]").exists()).toBe(false)
    expect(wrapper.text()).toContain("Why this is asked")
  })

  it("asks for the password once the step-up window has passed", async () => {
    mockStart
      .mockResolvedValueOnce({ok: false, reason: "Confirm it is you", needsStepUp: true})
      .mockResolvedValue({ok: true, value: {otpauthUri: "otpauth://totp/x", key: "ABCD"}})
    const wrapper = setUp({mode: "required"})
    await settle()

    expect(at(wrapper)).toContain("Confirm it is you")
    expect(wrapper.text()).toContain("Step 1 of 4")
    expect(wrapper.text()).toContain("It has been a while since you signed in")
    expect(wrapper.emitted("stepUp")).toBeUndefined()

    await wrapper.get("[data-testid=two-factor-password-field] input").setValue("Secret123!")
    await submit(wrapper)
    expect(mockStart).toHaveBeenLastCalledWith("Secret123!")
    expect(at(wrapper)).toContain("Scan the code")
    expect(wrapper.text()).toContain("Step 2 of 4")
  })
})
