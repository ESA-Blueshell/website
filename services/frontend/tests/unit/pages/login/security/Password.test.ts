import {beforeEach, describe, expect, it, vi} from "vitest"
import Password from "@/pages/login/security/Password.vue"
import {mountInApp, settle} from "../../helpers"
import {ok, refused} from "./stubs"

const {mockStore, mockAuth} = vi.hoisted(() => ({
  mockStore: {commit: vi.fn(), getters: {getLogin: {userId: 3}}},
  mockAuth: {readTwoFactor: vi.fn(), readEmailAddress: vi.fn(), savePassword: vi.fn()},
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
  const wrapper = mountInApp(Password)
  await settle()
  return wrapper
}
type Page = Awaited<ReturnType<typeof open>>
const fill = async (wrapper: Page, current: string, next: string) => {
  await wrapper.get("[data-testid=security-current-password-field] input").setValue(current)
  await wrapper.get("[data-testid=security-new-password-field] input").setValue(next)
}
const submit = async (wrapper: Page) => {
  await wrapper.get("[data-testid=security-password] form").trigger("submit")
  await settle()
}

describe("the password page", () => {
  beforeEach(() => {
    mockAuth.readTwoFactor.mockResolvedValue({on: true, backupCodesLeft: 5, required: false, offered: false, mayTurnOff: true})
    mockAuth.readEmailAddress.mockResolvedValue({email: "alice@example.com"})
    mockAuth.savePassword.mockResolvedValue(ok())
  })

  it("changes the password, ends the other sign-ins and empties the form", async () => {
    const wrapper = await open()
    expect(wrapper.get("[data-testid=security-change-password-btn]").attributes("disabled")).toBeDefined()

    await fill(wrapper, "Secret123!", "Another123!")
    expect(wrapper.get("[data-testid=security-change-password-btn]").attributes("disabled")).toBeUndefined()
    await submit(wrapper)

    expect(mockAuth.savePassword).toHaveBeenCalledWith("Secret123!", "Another123!")
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Your password is changed. Every other sign-in has ended.")
    expect((wrapper.get("[data-testid=security-current-password-field] input").element as HTMLInputElement).value).toBe("")
  })

  it("names the address a forgotten password is reset through", async () => {
    expect((await open()).text()).toContain("The link goes to alice@example.com.")
  })

  it("asks for a step-up where the api wants one, and tries again once it is given", async () => {
    mockAuth.savePassword.mockResolvedValueOnce(refused("Confirm it is you", true)).mockResolvedValueOnce(ok())
    const wrapper = await open()
    await fill(wrapper, "Secret123!", "Another123!")
    await submit(wrapper)

    const dialog = wrapper.getComponent({name: "StepUpDialog"})
    expect(dialog.props("modelValue")).toBe(true)
    expect(dialog.props("twoFactorOn")).toBe(true)
    dialog.vm.$emit("proved")
    await settle()

    expect(mockAuth.savePassword).toHaveBeenCalledTimes(2)
    dialog.vm.$emit("update:modelValue", false)
    await settle()
    expect(dialog.props("modelValue")).toBe(false)
  })

  it("says why a change was refused", async () => {
    mockAuth.savePassword.mockResolvedValue(refused("That password is not right."))
    const wrapper = await open()
    await fill(wrapper, "Wrong", "Another123!")
    await submit(wrapper)

    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "That password is not right.")
  })
})
