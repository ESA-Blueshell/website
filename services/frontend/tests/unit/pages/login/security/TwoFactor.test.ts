import {beforeEach, describe, expect, it, vi} from "vitest"
import TwoFactor from "@/pages/login/security/TwoFactor.vue"
import {mountInApp, settle} from "../../helpers"
import {ok, refused} from "./stubs"

const {mockStore, mockAuth} = vi.hoisted(() => ({
  mockStore: {commit: vi.fn(), getters: {getLogin: {userId: 3}}},
  mockAuth: {readTwoFactor: vi.fn(), newBackupCodes: vi.fn(), removeTwoFactor: vi.fn()},
}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("@/domains/auth", async (importOriginal) => {
  const {stepUpStub} = await import("./stubs")
  return {
    ...(await importOriginal<Record<string, unknown>>()),
    ...mockAuth,
    StepUpDialog: stepUpStub,
    BackupCodes: {name: "BackupCodes", props: ["codes"], template: "<div data-testid='fresh-codes' />"},
  }
})

vi.mock("@/components/common/AccountFrame.vue", async () => ({default: (await import("./stubs")).frameStub}))

const standing = (on: boolean, backupCodesLeft = on ? 8 : 0, mayTurnOff = on) =>
  ({on, backupCodesLeft, required: false, offered: false, mayTurnOff, since: on ? "2026-09-12T10:00:00Z" : null})

const open = async () => {
  const wrapper = mountInApp(TwoFactor)
  await settle()
  return wrapper
}
type Page = Awaited<ReturnType<typeof open>>
const press = async (wrapper: Page, testid: string) => {
  await wrapper.get(`[data-testid=${testid}]`).trigger("click")
  await settle()
}

describe("the two-factor page", () => {
  beforeEach(() => {
    mockAuth.readTwoFactor.mockResolvedValue(standing(true))
    mockAuth.newBackupCodes.mockResolvedValue(ok(["aaaaa-bbbbb"]))
    mockAuth.removeTwoFactor.mockResolvedValue(ok())
  })

  it("says since when it is on and how many backup codes are left", async () => {
    const wrapper = await open()

    expect(wrapper.text()).toContain("On since Sat 12 Sep")
    expect(wrapper.get("[data-testid=security-backup-codes-left]").text()).toContain("8 of 10 left")
    expect(wrapper.find("[data-testid=security-backup-codes-low]").exists()).toBe(false)
    expect(wrapper.get("[data-testid=security-replace-two-factor-btn]").attributes("to")).toBe("/account/security/two-factor/set-up?replace=1")
  })

  it("makes new backup codes behind a step-up and shows them once", async () => {
    mockAuth.readTwoFactor.mockResolvedValueOnce(standing(true, 1)).mockResolvedValue(standing(true, 10))
    mockAuth.newBackupCodes.mockResolvedValueOnce(refused("Confirm it is you", true)).mockResolvedValue(ok(["aaaaa-bbbbb"]))
    const wrapper = await open()
    expect(wrapper.get("[data-testid=security-backup-codes-low]").text()).toContain("1 backup code left")

    await press(wrapper, "security-new-backup-codes-btn")
    const dialog = wrapper.getComponent({name: "StepUpDialog"})
    expect(dialog.props("modelValue")).toBe(true)
    expect(dialog.props("twoFactorOn")).toBe(true)
    dialog.vm.$emit("proved")
    await settle()

    expect(wrapper.getComponent({name: "BackupCodes"}).props("codes")).toEqual(["aaaaa-bbbbb"])
    dialog.vm.$emit("update:modelValue", false)
    await settle()
    expect(dialog.props("modelValue")).toBe(false)
    expect(wrapper.get("[data-testid=security-backup-codes-left]").text()).toContain("10 of 10 left")
    expect(mockStore.commit).toHaveBeenCalledWith("setTwoFactor", standing(true, 10))
  })

  it("turns off for somebody who may, and tells them", async () => {
    mockAuth.readTwoFactor.mockResolvedValueOnce(standing(true)).mockResolvedValue(standing(false))
    const wrapper = await open()

    await press(wrapper, "security-turn-off-two-factor-btn")

    expect(mockAuth.removeTwoFactor).toHaveBeenCalled()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Two-factor authentication is off.")
    expect(wrapper.get("[data-testid=security-set-up-two-factor-btn]").attributes("to")).toBe("/account/security/two-factor/set-up")
  })

  it("keeps a granted role's two-factor on, and says why", async () => {
    mockAuth.readTwoFactor.mockResolvedValue(standing(true, 8, false))
    const wrapper = await open()

    expect(wrapper.find("[data-testid=security-turn-off-two-factor-btn]").exists()).toBe(false)
    expect(wrapper.text()).toContain("Your role needs two-factor, so it can be replaced but not turned off.")
  })
})
