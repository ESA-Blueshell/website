import {beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import BankAccount from "@/pages/login/BankAccount.vue"
import {mountInApp, settle} from "../helpers"

const api = vi.hoisted(() => ({
  findOwnBankAccount: vi.fn(),
  replaceOwnBankAccount: vi.fn(),
  deleteOwnBankAccount: vi.fn(),
  twoFactorStanding: vi.fn(),
}))
const mockStore = vi.hoisted(() => ({commit: vi.fn(), getters: {getLogin: {userId: 12}}}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))
vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})
vi.mock("@/components/common/AccountFrame.vue", () => ({
  default: {name: "AccountFrame", props: ["heading", "crumb", "islandContent", "eyebrow"], template: "<div><slot /></div>"},
}))

const StepUpDialog = {name: "StepUpDialog", props: ["modelValue", "twoFactorOn"], emits: ["proved", "update:modelValue"], template: "<div />"}
const held = {ibanCountry: "NL", ibanLastTwo: "00", bic: null, recordedAt: "2026-10-15T10:00:00Z"}

describe("the bank account page", () => {
  const mount = async () => {
    const wrapper = mountInApp(BankAccount, {global: {stubs: {StepUpDialog}}})
    await settle()
    return wrapper
  }

  const type = async (wrapper: VueWrapper, testid: string, value: string) => {
    const marked = wrapper.get(`[data-testid="${testid}"]`)
    const input = marked.element.tagName === "INPUT" ? marked : marked.get("input")
    ;(input.element as HTMLInputElement).value = value
    await input.trigger("input")
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.twoFactorStanding.mockResolvedValue({status: 200, data: {on: true, required: false}})
  })

  it("asks for an account where none is on file, saves it after a step-up and then shows only its preview", async () => {
    api.findOwnBankAccount.mockResolvedValue({status: 204, data: undefined})
    api.replaceOwnBankAccount.mockResolvedValueOnce({status: 403, error: {code: "StepUpRequired"}, response: {status: 403}})
    api.replaceOwnBankAccount.mockResolvedValueOnce({status: 200, data: held})
    const wrapper = await mount()

    await wrapper.get('[data-testid="bank-account-form"]').trigger("submit")
    await settle()
    expect(api.replaceOwnBankAccount).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain("Add your IBAN.")

    await type(wrapper, "bank-account-holder", "Ann Vos")
    await type(wrapper, "bank-account-iban-input", "NL91 ABNA 0417 1643 00")
    await wrapper.get('[data-testid="bank-account-form"]').trigger("submit")
    await settle()
    expect(wrapper.getComponent(StepUpDialog).props("modelValue")).toBe(true)

    wrapper.getComponent(StepUpDialog).vm.$emit("proved")
    await settle()

    expect(api.replaceOwnBankAccount).toHaveBeenLastCalledWith({body: {iban: "NL91ABNA0417164300", accountHolder: "Ann Vos", bic: undefined}})
    expect(wrapper.get('[data-testid="bank-account-preview"]').text()).toContain("NL •• … ••00")
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Your bank account is saved.")
  })

  it("asks a BIC for a bank outside the Netherlands, and shows what the api refused", async () => {
    api.findOwnBankAccount.mockResolvedValue({status: 204, data: undefined})
    api.replaceOwnBankAccount.mockResolvedValue({status: 400, error: {code: "InvalidBic"}, response: {status: 400}})
    const wrapper = await mount()

    await type(wrapper, "bank-account-holder", "Ann Vos")
    await type(wrapper, "bank-account-iban-input", "DE89370400440532013000")
    expect(wrapper.find('[data-testid="bank-account-bic"]').exists()).toBe(true)
    await type(wrapper, "bank-account-bic-input", "COBADEFFXXX")
    await wrapper.get('[data-testid="bank-account-form"]').trigger("submit")
    await settle()

    expect(api.replaceOwnBankAccount).toHaveBeenCalledWith({body: {iban: "DE89370400440532013000", accountHolder: "Ann Vos", bic: "COBADEFFXXX"}})
    expect(wrapper.text()).toContain("That is not a valid BIC.")
  })

  it("shows the account on file, replaces it on request, and deletes it after a step-up", async () => {
    api.findOwnBankAccount.mockResolvedValue({status: 200, data: held})
    api.deleteOwnBankAccount.mockResolvedValueOnce({status: 403, error: {code: "StepUpRequired"}, response: {status: 403}})
    api.deleteOwnBankAccount.mockResolvedValueOnce({status: 204, data: undefined})
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="bank-account-preview"]').text()).toContain("NL •• … ••00")
    await wrapper.get('[data-testid="bank-account-replace"]').trigger("click")
    await settle()
    expect(wrapper.find('[data-testid="bank-account-form"]').exists()).toBe(true)
    await wrapper.get('[data-testid="bank-account-cancel"]').trigger("click")
    await settle()

    await wrapper.get('[data-testid="bank-account-delete"]').trigger("click")
    wrapper.getComponent({name: "ConfirmDialog"}).vm.$emit("confirm")
    await settle()
    expect(wrapper.getComponent(StepUpDialog).props("modelValue")).toBe(true)
    wrapper.getComponent(StepUpDialog).vm.$emit("proved")
    await settle()

    expect(api.deleteOwnBankAccount).toHaveBeenCalledTimes(2)
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Your bank account is deleted.")
    expect(wrapper.find('[data-testid="bank-account-form"]').exists()).toBe(true)
  })

  it("is reached from its route, says why a delete was refused, and closes its dialogs when asked", async () => {
    const router = (await import("@/plugins/router")).default
    const route = router.resolve("/account/payment-details/bank-account")
    expect(route.name).toBe("accountBankAccount")
    const load = route.matched.at(-1)!.components!.default as () => Promise<{default: unknown}>
    expect((await load()).default).toBeTruthy()

    api.findOwnBankAccount.mockResolvedValue({status: 200, data: held})
    api.deleteOwnBankAccount.mockResolvedValue({status: 503, error: {code: "SealingUnavailable"}, response: {status: 503}})
    const wrapper = await mount()

    await wrapper.get('[data-testid="bank-account-delete"]').trigger("click")
    wrapper.getComponent({name: "ConfirmDialog"}).vm.$emit("update:open", false)
    await settle()
    expect(wrapper.getComponent({name: "ConfirmDialog"}).props("open")).toBe(false)
    await wrapper.get('[data-testid="bank-account-delete"]').trigger("click")
    wrapper.getComponent({name: "ConfirmDialog"}).vm.$emit("confirm")
    await settle()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", expect.stringContaining("cannot be saved right now"))
    wrapper.getComponent(StepUpDialog).vm.$emit("update:modelValue", false)
    expect(wrapper.find('[data-testid="bank-account-preview"]').exists()).toBe(true)
  })

  it("says an IBAN is not complete once it is left, and closes the form's step-up when asked", async () => {
    api.findOwnBankAccount.mockResolvedValue({status: 204, data: undefined})
    const wrapper = await mount()

    await type(wrapper, "bank-account-iban-input", "NL91ABNA")
    await wrapper.get('[data-testid="bank-account-iban-input"]').trigger("blur")
    await settle()
    expect(wrapper.text()).toContain("This IBAN is not complete yet.")
    wrapper.findAllComponents(StepUpDialog).at(0)!.vm.$emit("update:modelValue", false)
  })
})
