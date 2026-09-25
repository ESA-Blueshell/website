import {beforeEach, describe, expect, it, vi} from "vitest"
import Email from "@/pages/login/security/Email.vue"
import {mountInApp, settle} from "../../helpers"
import {ok, refused} from "./stubs"

const {mockStore, mockAuth} = vi.hoisted(() => ({
  mockStore: {commit: vi.fn(), getters: {getLogin: {userId: 3}}},
  mockAuth: {readTwoFactor: vi.fn(), readEmailAddress: vi.fn(), askToMoveEmail: vi.fn()},
}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("@/domains/auth", async (importOriginal) => {
  const {stepUpStub} = await import("./stubs")
  return {...(await importOriginal<Record<string, unknown>>()), ...mockAuth, StepUpDialog: stepUpStub}
})

vi.mock("@/components/common/AccountFrame.vue", async () => ({default: (await import("./stubs")).frameStub}))

const open = async () => {
  const wrapper = mountInApp(Email)
  await settle()
  return wrapper
}
type Page = Awaited<ReturnType<typeof open>>
const ask = async (wrapper: Page, address: string) => {
  await wrapper.get("[data-testid=security-new-email-field] input").setValue(address)
  await wrapper.get("[data-testid=security-email] form").trigger("submit")
  await settle()
}

describe("the email address page", () => {
  beforeEach(() => {
    mockAuth.readTwoFactor.mockResolvedValue({on: false, backupCodesLeft: 0, required: false, offered: false, mayTurnOff: false})
    mockAuth.readEmailAddress.mockResolvedValue({email: "alice@example.com", pendingEmail: null})
    mockAuth.askToMoveEmail.mockResolvedValue(ok())
  })

  it("shows the address in use, and sends a link to the one asked for", async () => {
    const wrapper = await open()
    expect(wrapper.get("[data-testid=security-email-now]").text()).toContain("alice@example.com")
    expect(wrapper.find("[data-testid=security-email-pending]").exists()).toBe(false)

    mockAuth.readEmailAddress.mockResolvedValue({email: "alice@example.com", pendingEmail: "alice@utwente.nl"})
    await ask(wrapper, " alice@utwente.nl ")

    expect(mockAuth.askToMoveEmail).toHaveBeenCalledWith("alice@utwente.nl")
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "A confirmation link is on its way to alice@utwente.nl.")
    expect(wrapper.get("[data-testid=security-email-pending]").text()).toContain("Waiting for alice@utwente.nl")
    expect((wrapper.get("[data-testid=security-new-email-field] input").element as HTMLInputElement).value).toBe("")
  })

  it("sends the link for a waiting move again", async () => {
    mockAuth.readEmailAddress.mockResolvedValue({email: "alice@example.com", pendingEmail: "alice@utwente.nl"})
    const wrapper = await open()

    await wrapper.get("[data-testid=security-email-resend-btn]").trigger("click")
    await settle()

    expect(mockAuth.askToMoveEmail).toHaveBeenCalledWith("alice@utwente.nl")
  })

  it("asks for a step-up before it moves, and says why a move was refused", async () => {
    mockAuth.askToMoveEmail.mockResolvedValueOnce(refused("Confirm it is you", true)).mockResolvedValueOnce(refused("That address is taken."))
    const wrapper = await open()
    await ask(wrapper, "taken@example.com")

    const dialog = wrapper.getComponent({name: "StepUpDialog"})
    expect(dialog.props("modelValue")).toBe(true)
    expect(dialog.props("twoFactorOn")).toBe(false)
    dialog.vm.$emit("proved")
    await settle()

    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "That address is taken.")
    dialog.vm.$emit("update:modelValue", false)
    await settle()
    expect(dialog.props("modelValue")).toBe(false)
  })
})
