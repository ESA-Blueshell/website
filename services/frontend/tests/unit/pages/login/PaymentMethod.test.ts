import {beforeEach, describe, expect, it, vi} from "vitest"
import {RouterLinkStub, type VueWrapper} from "@vue/test-utils"
import PaymentMethod from "@/pages/login/PaymentMethod.vue"
import {IncassoStanding} from "@/services/api"
import {mountInApp, settle} from "../helpers"

const api = vi.hoisted(() => ({
  findOwnBankAccount: vi.fn(),
  findOwnMandate: vi.fn(),
  signOwnMandate: vi.fn(),
  endOwnMandate: vi.fn(),
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
const account = {ibanCountry: "NL", ibanLastTwo: "00", bic: null, recordedAt: "2026-10-15T10:00:00Z"}
const none = {standing: IncassoStanding.NONE}
const signed = {standing: IncassoStanding.MANDATE_RECORDED, ibanCountry: "NL", ibanLastTwo: "00", reference: "BLUESHELL-12-20261019", signedOn: "2026-10-19"}
const stepUp = {status: 403, error: {code: "StepUpRequired"}, response: {status: 403}}

describe("the payment method page", () => {
  const mount = async () => {
    const wrapper = mountInApp(PaymentMethod, {global: {stubs: {StepUpDialog, RouterLink: RouterLinkStub}}})
    await settle()
    return wrapper
  }
  const linkOf = (wrapper: VueWrapper, testid: string) =>
    wrapper.findAllComponents(RouterLinkStub).find(link => link.attributes("data-testid") === testid)?.props("to")
  const pick = async (wrapper: VueWrapper, key: string) => {
    wrapper.getComponent({name: "RadioGroup"}).vm.$emit("update:modelValue", key)
    await settle()
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.findOwnBankAccount.mockResolvedValue({status: 200, data: account})
    api.findOwnMandate.mockResolvedValue({status: 200, data: none})
    api.twoFactorStanding.mockResolvedValue({status: 200, data: {on: true, required: false}})
  })

  it("asks how to pay, and leads bank transfer or cash to How to pay", async () => {
    const wrapper = await mount()

    expect(wrapper.text()).toContain("How do you want to pay?")
    expect(wrapper.getComponent({name: "RadioGroup"}).props("modelValue")).toBe("manual")
    expect(linkOf(wrapper, "payment-method-how-to-pay")).toBe("/account/payment-details#how-to-pay")
  })

  it("says how the member pays could not be read, and offers nothing to change over it", async () => {
    api.findOwnMandate.mockResolvedValue({status: 500, error: {}, response: {status: 500}})
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="payment-method-unread"]').text()).toContain("cannot load your payment method")
    expect(wrapper.findComponent({name: "RadioGroup"}).exists()).toBe(false)
  })

  it("asks for a bank account first where none is on file", async () => {
    api.findOwnBankAccount.mockResolvedValue({status: 204, data: undefined})
    const wrapper = await mount()
    await pick(wrapper, "debit")

    expect(wrapper.get('[data-testid="payment-method-no-bank"]').text()).toContain("Add a bank account first")
    expect(linkOf(wrapper, "payment-method-add-bank")).toBe("/account/payment-details/bank-account")
    expect(wrapper.find('[data-testid="payment-method-form"]').exists()).toBe(false)
  })

  it("sets up direct debit from the account on file once the wording is agreed to, after a step-up", async () => {
    api.signOwnMandate.mockResolvedValueOnce(stepUp)
    api.signOwnMandate.mockResolvedValueOnce({status: 200, data: signed})
    const wrapper = await mount()
    await pick(wrapper, "debit")
    expect(wrapper.get('[data-testid="payment-method-account"]').text()).toBe("NL •• … ••00")

    await wrapper.get('[data-testid="payment-method-form"]').trigger("submit")
    await settle()
    expect(api.signOwnMandate).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain("Tick the box to allow direct debit.")

    wrapper.getComponent({name: "CheckBox"}).vm.$emit("update:modelValue", true)
    await wrapper.get('[data-testid="payment-method-form"]').trigger("submit")
    await settle()
    expect(wrapper.getComponent(StepUpDialog).props("modelValue")).toBe(true)
    expect(wrapper.getComponent(StepUpDialog).props("twoFactorOn")).toBe(true)

    wrapper.getComponent(StepUpDialog).vm.$emit("proved")
    await settle()
    expect(api.signOwnMandate).toHaveBeenLastCalledWith({body: {authorised: true, wordingVersion: "2026-10"}})
    expect(wrapper.get('[data-testid="payment-method-on"]').text()).toContain("Since 19 Oct 2026")
    expect(wrapper.text()).toContain("Direct debit reference BLUESHELL-12-20261019")
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Direct debit is set up.")
  })

  it("says why direct debit could not be set up", async () => {
    api.signOwnMandate.mockResolvedValue({status: 409, error: {code: "NoBankAccount"}, response: {status: 409}})
    const wrapper = await mount()
    await pick(wrapper, "debit")
    wrapper.getComponent({name: "CheckBox"}).vm.$emit("update:modelValue", true)
    await wrapper.get('[data-testid="payment-method-form"]').trigger("submit")
    await settle()

    expect(wrapper.get('[data-testid="payment-method-failure"]').text()).toContain("Add a bank account first.")
  })

  it("shows a standing mandate, and stops it after a confirmation and a step-up", async () => {
    api.findOwnMandate.mockResolvedValue({status: 200, data: signed})
    api.endOwnMandate.mockResolvedValueOnce(stepUp)
    api.endOwnMandate.mockResolvedValueOnce({status: 200, data: none})
    const wrapper = await mount()
    expect(wrapper.getComponent({name: "RadioGroup"}).props("modelValue")).toBe("debit")
    expect(wrapper.get('[data-testid="payment-method-bank-row"]').text()).toContain("stops direct debit")

    await pick(wrapper, "manual")
    await wrapper.get('[data-testid="payment-method-stop"]').trigger("click")
    const dialog = wrapper.getComponent({name: "ConfirmDialog"})
    expect(dialog.props("open")).toBe(true)
    dialog.vm.$emit("confirm")
    await settle()
    expect(wrapper.getComponent(StepUpDialog).props("modelValue")).toBe(true)

    wrapper.getComponent(StepUpDialog).vm.$emit("proved")
    await settle()
    expect(api.endOwnMandate).toHaveBeenCalledTimes(2)
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Direct debit has stopped.")
    expect(wrapper.find('[data-testid="payment-method-how-to-pay"]').exists()).toBe(true)
  })

  it("is reached from its route, and closes its dialogs when asked", async () => {
    const router = (await import("@/plugins/router")).default
    const route = router.resolve("/account/payment-details/payment-method")
    expect(route.name).toBe("accountPaymentMethod")
    const load = route.matched.at(-1)!.components!.default as () => Promise<{default: unknown}>
    expect((await load()).default).toBeTruthy()

    api.findOwnMandate.mockResolvedValue({status: 200, data: signed})
    api.endOwnMandate.mockResolvedValue(stepUp)
    const wrapper = await mount()
    await pick(wrapper, "manual")
    await wrapper.get('[data-testid="payment-method-stop"]').trigger("click")
    wrapper.getComponent({name: "ConfirmDialog"}).vm.$emit("update:open", false)
    await settle()
    expect(wrapper.getComponent({name: "ConfirmDialog"}).props("open")).toBe(false)

    await wrapper.get('[data-testid="payment-method-stop"]').trigger("click")
    wrapper.getComponent({name: "ConfirmDialog"}).vm.$emit("confirm")
    await settle()
    wrapper.getComponent(StepUpDialog).vm.$emit("update:modelValue", false)
    await settle()
    expect(wrapper.getComponent(StepUpDialog).props("modelValue")).toBe(false)
  })

  it("lets a paper mandate without an account be stopped, and says why a stop failed", async () => {
    api.findOwnMandate.mockResolvedValue({status: 200, data: {standing: IncassoStanding.ON_INCASSO_WITHOUT_BANK_DETAILS}})
    api.endOwnMandate.mockResolvedValue({status: 503, error: {code: "SealingUnavailable"}, response: {status: 503}})
    const wrapper = await mount()
    expect(wrapper.find('[data-testid="payment-method-form"]').exists()).toBe(true)

    await pick(wrapper, "manual")
    await wrapper.get('[data-testid="payment-method-stop"]').trigger("click")
    wrapper.getComponent({name: "ConfirmDialog"}).vm.$emit("confirm")
    await settle()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", expect.stringContaining("nothing was changed"))
    expect(wrapper.find('[data-testid="payment-method-stop"]').exists()).toBe(true)
  })
})
