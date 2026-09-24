import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import AccountSecurityDialog from "@/domains/auth/components/AccountSecurityDialog.vue"
import {settle} from "../../pages/helpers"

const {mockStore, security} = vi.hoisted(() => ({
  mockStore: {commit: vi.fn(), getters: {getLogin: {userId: 1}}},
  security: {
    readAccountStanding: vi.fn(),
    readSecurityLogOf: vi.fn(),
    resendReenrolment: vi.fn(),
    resetTwoFactorOf: vi.fn(),
    unlockAccount: vi.fn(),
  },
}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})
vi.mock("@/domains/auth/adapters/accountSecurity", () => security)

const StepUpDialog = {name: "StepUpDialog", props: ["modelValue", "twoFactorOn"], emits: ["proved", "update:modelValue"], template: "<div />"}
const stubs = {VDialog: {name: "VDialog", template: "<div><slot /></div>"}, StepUpDialog}

const event = {
  id: 1, kind: "ACCOUNT_LOCKED", actorKind: "PERSON", occurredAt: "2026-09-24T12:00:00Z", browser: "Firefox on Linux", note: "lost phone",
}

const open = async (userId = 9) => {
  const wrapper = mount(AccountSecurityDialog, {props: {modelValue: true, userId, userName: "Alice Doe"}, global: {stubs}})
  await settle()
  return wrapper
}

const button = (wrapper: Awaited<ReturnType<typeof open>>, testId: string) => wrapper.find(`[data-testid=${testId}]`)

describe("an admin's account security dialog", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    security.readAccountStanding.mockResolvedValue({twoFactorOn: true, awaitingReenrolment: true, locked: true})
    security.readSecurityLogOf.mockResolvedValue({events: [event, {...event, id: 2, browser: null, note: null}]})
    for (const write of [security.unlockAccount, security.resetTwoFactorOf, security.resendReenrolment]) {
      write.mockResolvedValue({ok: true, value: undefined})
    }
  })

  it("shows the standing and the log of the person", async () => {
    const wrapper = await open()

    expect(security.readAccountStanding).toHaveBeenCalledWith(9)
    expect(wrapper.find("[data-testid=account-security-two-factor-chip]").text()).toContain("Two-factor on")
    expect(wrapper.find("[data-testid=account-security-awaiting-chip]").exists()).toBe(true)
    expect(wrapper.find("[data-testid=account-security-locked-chip]").exists()).toBe(true)
    const entries = wrapper.findAll("[data-testid=account-security-log-entry]")
    expect(entries).toHaveLength(2)
    expect(entries[0].text()).toContain("Firefox on Linux · lost phone")
  })

  it("unlocks, resets and resends with the reason given, then reads the standing again", async () => {
    const wrapper = await open()

    await wrapper.find("[data-testid=account-security-reason-field] textarea").setValue(" heard from them ")
    await wrapper.find("[data-testid=account-security-email-field] input").setValue(" fixed@example.com ")
    await button(wrapper, "account-security-unlock-btn").trigger("click")
    await settle()
    expect(security.unlockAccount).toHaveBeenCalledWith(9, "heard from them", "fixed@example.com")
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Unlocked. A password reset is on its way.")

    await wrapper.find("[data-testid=account-security-reason-field] textarea").setValue("lost phone")
    await button(wrapper, "account-security-reset-btn").trigger("click")
    await settle()
    expect(security.resetTwoFactorOf).toHaveBeenCalledWith(9, "lost phone")

    await button(wrapper, "account-security-resend-btn").trigger("click")
    await settle()
    expect(security.resendReenrolment).toHaveBeenCalledWith(9)
    expect(security.readAccountStanding).toHaveBeenCalledTimes(4)
  })

  it("asks for a step-up and runs the write again once proved, and shows any other refusal", async () => {
    security.resendReenrolment
      .mockResolvedValueOnce({ok: false, reason: "Confirm it is you first.", needsStepUp: true})
      .mockResolvedValueOnce({ok: false, reason: "That person is not waiting to set up two-factor again.", needsStepUp: false})
    const wrapper = await open()

    await button(wrapper, "account-security-resend-btn").trigger("click")
    await settle()
    const stepUp = wrapper.findComponent(StepUpDialog)
    expect(stepUp.props("modelValue")).toBe(true)

    stepUp.vm.$emit("update:modelValue", false)
    stepUp.vm.$emit("proved")
    await settle()
    expect(security.resendReenrolment).toHaveBeenCalledTimes(2)
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "That person is not waiting to set up two-factor again.")
  })

  it("offers no reset of the admin's own two-factor, and closes", async () => {
    security.readAccountStanding.mockResolvedValue({twoFactorOn: true, awaitingReenrolment: false, locked: false})
    const wrapper = await open(1)

    expect(button(wrapper, "account-security-reset-btn").exists()).toBe(false)
    expect(button(wrapper, "account-security-unlock-btn").exists()).toBe(false)
    wrapper.findComponent({name: "VDialog"}).vm.$emit("update:modelValue", false)
    await wrapper.findAll("button").find(b => b.text() === "Close")?.trigger("click")
    expect(wrapper.emitted("update:modelValue")).toEqual([[false], [false]])
  })

  it("reads nothing into an empty log", async () => {
    security.readSecurityLogOf.mockResolvedValue(null)
    const wrapper = await open()

    expect(wrapper.findAll("[data-testid=account-security-log-entry]")).toHaveLength(0)
  })
})
