import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {nextTick} from "vue"
import type {VueWrapper} from "@vue/test-utils"
import DeclarationReview from "@/pages/management/DeclarationReview.vue"
import {DeclarationState} from "@/services/api"
import {mountInApp, settle, unmountAll} from "../helpers"
import {answered} from "./declarationFixtures"

const api = vi.hoisted(() => ({
  findDeclaration: vi.fn(),
  approveDeclaration: vi.fn(),
  sendBackDeclaration: vi.fn(),
  rejectDeclaration: vi.fn(),
  findDeclarationBankAccount: vi.fn(),
  markDeclarationPaid: vi.fn(),
  revealIban: vi.fn(),
  downloadDeclarationsPdf: vi.fn(),
}))
const saved = vi.hoisted(() => ({saveFile: vi.fn()}))
vi.mock("@/utils/saveFile", () => saved)
const ACCOUNT = {accountHolder: "Ann Vos", ibanCountry: "NL", ibanLastTwo: "00", bic: null}
const mockRoute = vi.hoisted(() => ({params: {id: "1"}}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))
vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../helpers/testUtils")
  return withVueRouter(importOriginal, {route: mockRoute})
})

describe("a declaration as the board checks it", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(DeclarationReview, {attachTo: document.body})
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }
  const button = (wrapper: VueWrapper, testid: string) =>
    wrapper.findAllComponents({name: "CutButton"}).find(one => one.props("testid") === testid)
  const mini = (wrapper: VueWrapper, testid: string) =>
    wrapper.findAllComponents({name: "MiniButton"}).find(one => one.props("testid") === testid)
  const paidDialog = (wrapper: VueWrapper) =>
    wrapper.findAllComponents({name: "ConfirmDialog"}).find(one => one.props("testid") === "declaration-paid-dialog")!
  const approved = () => api.findDeclaration.mockResolvedValue({status: 200, data: answered(1, DeclarationState.APPROVED)})
  const reasonDialog = (wrapper: VueWrapper) =>
    wrapper.findAllComponents({name: "ModalDialog"}).find(one => one.props("testid") === "declaration-reason-dialog")!
  const giveReason = async (wrapper: VueWrapper, said: string) => {
    wrapper.findAllComponents({name: "FormControl"}).find(one => one.props("testid") === "declaration-reason")!.vm.$emit("update:modelValue", said)
    await settle()
    await button(wrapper, "declaration-reason-confirm")!.trigger("click")
    await settle()
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.findDeclaration.mockResolvedValue({status: 200, data: answered(1, DeclarationState.SUBMITTED, {
      receipts: [{id: 502, name: "shop.pdf", mediaType: "application/pdf", url: "/declarations/receipts/502"}],
    })})
    api.findDeclarationBankAccount.mockResolvedValue({status: 200, data: {bankAccount: ACCOUNT}})
    Object.defineProperty(navigator, "clipboard", {value: {writeText: vi.fn().mockResolvedValue(undefined)}, configurable: true})
  })
  afterEach(() => unmountAll(wrappers))

  it("shows the expenses with their receipts, the total and every step with who and when", async () => {
    const wrapper = await mount()

    expect(wrapper.getComponent({name: "ManagementPage"}).props("title")).toBe("Ann Vos")
    expect(wrapper.get('[data-testid="declaration-review-expense-0"]').text()).toContain("Event: Autumn LAN party")
    expect(wrapper.text()).toContain("€ 12.50")
    expect(wrapper.find('[data-testid="declaration-review-receipts"]').exists()).toBe(true)
    const history = wrapper.get('[data-testid="declaration-review-history"]').text()
    expect(history).toContain("Submitted by Ann Vos")
    expect(history).toContain("2 October 2026")
  })

  it("approves, and then offers only to send back or reject", async () => {
    api.approveDeclaration.mockResolvedValue({status: 200, data: answered(1, DeclarationState.APPROVED)})
    const wrapper = await mount()
    await button(wrapper, "declaration-approve")!.trigger("click")
    await settle()

    expect(api.approveDeclaration).toHaveBeenCalledWith({path: {id: 1}})
    expect(wrapper.get('[data-testid="declaration-review-state"]').text()).toContain("Approved")
    expect(button(wrapper, "declaration-approve")).toBeUndefined()
    expect(button(wrapper, "declaration-send-back")).toBeDefined()
  })

  it("sends back with a reason, after which nothing is left to do", async () => {
    api.sendBackDeclaration.mockResolvedValue({status: 200, data: answered(1, DeclarationState.DRAFT, {steps: [
      ...answered(1, DeclarationState.SUBMITTED).steps,
      {state: DeclarationState.DRAFT, at: "2026-10-03T10:00:00Z", by: "Cas Bos", reason: "The receipt is cut off."},
    ]})})
    const wrapper = await mount()
    await button(wrapper, "declaration-send-back")!.trigger("click")
    await settle()
    expect(document.querySelector('[data-testid="declaration-reason-dialog"]')?.textContent).toContain("submit it again")

    await giveReason(wrapper, "The receipt is cut off.")
    expect(api.sendBackDeclaration).toHaveBeenCalledWith({path: {id: 1}, body: {reason: "The receipt is cut off."}})
    expect(wrapper.get('[data-testid="declaration-review-history"]').text()).toContain("\"The receipt is cut off.\"")
    expect(button(wrapper, "declaration-reject")).toBeUndefined()
  })

  it("rejects with a reason, and says why the api refused", async () => {
    api.rejectDeclaration.mockResolvedValue({status: 409, error: {code: "DeclarationStateChanged", state: "PAID"}, response: {status: 409}})
    const wrapper = await mount()
    await button(wrapper, "declaration-reject")!.trigger("click")
    await settle()
    expect(document.querySelector('[data-testid="declaration-reason-dialog"]')?.textContent).toContain("cannot be submitted again")

    await giveReason(wrapper, "Not for the association.")
    expect(api.rejectDeclaration).toHaveBeenCalledWith({path: {id: 1}, body: {reason: "Not for the association."}})
    expect(wrapper.get('[data-testid="declaration-review-failure"]').text()).toBe("This declaration is already paid. Reload the page.")
  })

  it("closes the reason dialog when asked, and says when there is no such declaration", async () => {
    const wrapper = await mount()
    await button(wrapper, "declaration-reject")!.trigger("click")
    await settle()
    expect(reasonDialog(wrapper).props("open")).toBe(true)
    reasonDialog(wrapper).vm.$emit("update:open", false)
    await settle()
    expect(reasonDialog(wrapper).props("open")).toBe(false)
    reasonDialog(wrapper).vm.$emit("update:open", true)
    await settle()
    expect(reasonDialog(wrapper).props("open")).toBe(false)

    api.findDeclaration.mockResolvedValue({status: 404, error: {code: "DeclarationNotFound"}, response: {status: 404}})
    expect((await mount()).get('[data-testid="declaration-review-missing"]').exists()).toBe(true)
  })

  it("shows an approved declaration's transfer to copy, the IBAN masked until it is revealed", async () => {
    approved()
    api.revealIban.mockResolvedValue({status: 200, data: {iban: "NL91ABNA0417164300"}})
    const wrapper = await mount()

    expect(api.findDeclarationBankAccount).toHaveBeenCalledWith({path: {id: 1}})
    expect(wrapper.get('[data-testid="declaration-review-holder"]').text()).toBe("Ann Vos")
    expect(wrapper.get('[data-testid="declaration-review-iban"]').text()).toBe("NL •• … ••00")
    expect(wrapper.get('[data-testid="declaration-review-reference"]').text()).toBe("Declaration 1 Pizza for the committee meeting")
    expect(mini(wrapper, "declaration-copy-iban")).toBeUndefined()
    expect(wrapper.find('[data-testid="declaration-review-bic"]').exists()).toBe(false)

    vi.useFakeTimers()
    await mini(wrapper, "declaration-copy-amount")!.trigger("click")
    await nextTick()
    await nextTick()
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith("12.50")
    expect(mini(wrapper, "declaration-copy-amount")!.text()).toBe("Copied")
    vi.advanceTimersByTime(1500)
    await nextTick()
    expect(mini(wrapper, "declaration-copy-amount")!.text()).toBe("Copy")
    vi.useRealTimers()

    await mini(wrapper, "declaration-reveal")!.trigger("click")
    await settle()
    expect(api.revealIban).toHaveBeenCalledWith({path: {userId: 12}})
    expect(wrapper.get('[data-testid="declaration-review-iban"]').text()).toBe("NL91 ABNA 0417 1643 00")
    await mini(wrapper, "declaration-copy-iban")!.trigger("click")
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith("NL91ABNA0417164300")
    await mini(wrapper, "declaration-reveal")!.trigger("click")
    await settle()
    expect(wrapper.get('[data-testid="declaration-review-iban"]').text()).toBe("NL •• … ••00")
  })

  it("marks paid after asking, and the history keeps the account it went to", async () => {
    approved()
    api.markDeclarationPaid.mockResolvedValue({status: 200, data: answered(1, DeclarationState.PAID, {steps: [
      ...answered(1, DeclarationState.SUBMITTED).steps,
      {state: DeclarationState.APPROVED, at: "2026-10-03T10:00:00Z", by: "Cas Bos"},
      {state: DeclarationState.PAID, at: "2026-10-04T10:00:00Z", by: "Cas Bos", paidToCountry: "NL", paidToLastTwo: "00"},
    ]})})
    const wrapper = await mount()
    await button(wrapper, "declaration-mark-paid")!.trigger("click")
    await settle()
    expect(paidDialog(wrapper).props("open")).toBe(true)
    expect(paidDialog(wrapper).props("question")).toBe("You sent € 12.50 to NL •• … ••00. They get an email saying it is paid.")
    paidDialog(wrapper).vm.$emit("update:open", false)
    await settle()
    expect(paidDialog(wrapper).props("open")).toBe(false)
    await button(wrapper, "declaration-mark-paid")!.trigger("click")
    await settle()

    paidDialog(wrapper).vm.$emit("confirm")
    await settle()
    expect(api.markDeclarationPaid).toHaveBeenCalledWith({path: {id: 1}})
    expect(wrapper.get('[data-testid="declaration-review-history"]').text()).toContain("Marked as paid by Cas Bos, to NL •• … ••00")
    expect(wrapper.find('[data-testid="declaration-review-pay-to"]').exists()).toBe(false)
    expect(paidDialog(wrapper).props("open")).toBe(false)
  })

  it("approving reads the account to pay to, and the refused payout says why", async () => {
    api.approveDeclaration.mockResolvedValue({status: 200, data: answered(1, DeclarationState.APPROVED)})
    api.markDeclarationPaid.mockResolvedValue({status: 409, error: {code: "SubmitterHasNoBankAccount"}, response: {status: 409}})
    const wrapper = await mount()
    expect(api.findDeclarationBankAccount).not.toHaveBeenCalled()
    await button(wrapper, "declaration-approve")!.trigger("click")
    await settle()
    expect(wrapper.get('[data-testid="declaration-review-holder"]').text()).toBe("Ann Vos")

    paidDialog(wrapper).vm.$emit("confirm")
    await settle()
    expect(wrapper.get('[data-testid="declaration-review-failure"]').text()).toContain("has no bank account on file")
  })

  it("says when there is no account to pay to, when it cannot be read and when the holder or a reveal is not available", async () => {
    approved()
    api.findDeclarationBankAccount.mockResolvedValue({status: 200, data: {}})
    let wrapper = await mount()
    expect(wrapper.get('[data-testid="declaration-review-no-bank"]').text()).toContain("Ann Vos has not added a bank account yet")
    expect(button(wrapper, "declaration-mark-paid")!.attributes("disabled")).toBeDefined()

    api.findDeclarationBankAccount.mockResolvedValue({status: 500, error: {}, response: {status: 500}})
    wrapper = await mount()
    expect(wrapper.find('[data-testid="declaration-review-account-unread"]').exists()).toBe(true)

    api.findDeclarationBankAccount.mockResolvedValue({status: 200, data: {bankAccount: {...ACCOUNT, accountHolder: null, ibanCountry: "DE", bic: "COBADEFFXXX"}}})
    api.revealIban.mockResolvedValue({status: 503, error: {}, response: {status: 503}})
    wrapper = await mount()
    expect(wrapper.get('[data-testid="declaration-review-holder"]').text()).toBe("Name unavailable")
    expect(mini(wrapper, "declaration-copy-holder")).toBeUndefined()
    expect(wrapper.get('[data-testid="declaration-review-bic"]').text()).toBe("COBADEFFXXX")
    await mini(wrapper, "declaration-reveal")!.trigger("click")
    await settle()
    expect(wrapper.get('[data-testid="declaration-reveal-failure"]').text()).toBe("The IBAN could not be shown.")
  })

  it("downloads its PDF, and says when it could not be made", async () => {
    api.downloadDeclarationsPdf.mockResolvedValueOnce({status: 200, data: new Blob(["%PDF"])})
    const wrapper = await mount()
    await mini(wrapper, "declaration-pdf")!.trigger("click")
    await settle()
    expect(api.downloadDeclarationsPdf).toHaveBeenCalledWith({query: {ids: [1]}})
    expect(saved.saveFile).toHaveBeenCalledWith(expect.any(Blob), "declaration-1.pdf")

    api.downloadDeclarationsPdf.mockResolvedValueOnce({status: 500, error: {}, response: {status: 500}})
    await mini(wrapper, "declaration-pdf")!.trigger("click")
    await settle()
    expect(wrapper.get('[data-testid="declaration-review-failure"]').text()).toBe("We could not make the PDF. Try again.")
  })
})
