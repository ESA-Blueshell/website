import {beforeEach, describe, expect, it, vi} from "vitest"
import PaymentDetails from "@/pages/login/PaymentDetails.vue"
import {RouterLinkStub} from "@vue/test-utils"
import {IncassoStanding} from "@/services/api"
import {mountInApp, settle} from "../helpers"

const api = vi.hoisted(() => ({findOwnBankAccount: vi.fn(), findOwnMandate: vi.fn()}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const mockRoute = vi.hoisted(() => ({path: "/account/payment-details", params: {}, hash: ""}))
const {mockStore} = vi.hoisted(() => ({
  mockStore: {getters: {getLogin: {userId: 5, addressId: 12} as {userId: number; addressId?: number} | null}, dispatch: vi.fn()},
}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../helpers/testUtils")
  return withVueRouter(importOriginal, {route: mockRoute, router: {replace: vi.fn()}})
})

vi.mock("@/components/account/ManualPayment.vue", async () => {
  const {defineComponent} = await import("vue")
  return {default: defineComponent({name: "ManualPayment", setup: () => () => null})}
})

vi.mock("@/components/account/OwnContributions.vue", async () => {
  const {defineComponent} = await import("vue")
  return {default: defineComponent({name: "OwnContributions", setup: () => () => null})}
})

describe("the account's Payment details page", () => {
  beforeEach(() => {
    mockStore.getters.getLogin = {userId: 5, addressId: 12}
    mockRoute.hash = ""
    api.findOwnBankAccount.mockResolvedValue({status: 200, data: {ibanCountry: "NL", ibanLastTwo: "00", bic: null, recordedAt: "2026-10-15T10:00:00Z"}})
    api.findOwnMandate.mockResolvedValue({status: 200, data: {standing: IncassoStanding.NONE}})
  })

  it("links to the Payment method page, and lists how to pay by hand under How to pay", async () => {
    const wrapper = mountInApp(PaymentDetails, {global: {stubs: {RouterLink: RouterLinkStub}}})
    await settle()

    expect(wrapper.getComponent({name: "AccountFrame"}).props("heading")).toBe("Payment details")
    expect(wrapper.findComponent({name: "OwnContributions"}).exists()).toBe(true)
    const row = wrapper.findAllComponents(RouterLinkStub).find(link => link.attributes("data-testid") === "payment-details-method-row")!
    expect(row.props("to")).toBe("/account/payment-details/payment-method")
    expect(row.text()).toContain("Bank transfer or cash")
    expect(wrapper.get("#how-to-pay").text()).toContain("How to pay")
    expect(wrapper.get("#how-to-pay").findComponent({name: "ManualPayment"}).exists()).toBe(true)
  })

  it("shows direct debit with the account it collects from and since when", async () => {
    api.findOwnMandate.mockResolvedValue({status: 200, data: {
      standing: IncassoStanding.MANDATE_RECORDED, ibanCountry: "NL", ibanLastTwo: "00", reference: "BLUESHELL-5-20261019", signedOn: "2026-10-19",
    }})
    const wrapper = mountInApp(PaymentDetails)
    await settle()

    const row = wrapper.get('[data-testid="payment-details-method-row"]')
    expect(row.text()).toContain("Direct debit from NL •• … ••00 since 19 Oct 2026.")
  })

  it("counts a paper mandate without an account as direct debit, waiting for the account", async () => {
    api.findOwnMandate.mockResolvedValue({status: 200, data: {standing: IncassoStanding.ON_INCASSO_WITHOUT_BANK_DETAILS}})
    const wrapper = mountInApp(PaymentDetails)
    await settle()

    const row = wrapper.get('[data-testid="payment-details-method-row"]')
    expect(row.text()).toContain("Direct debit")
    expect(row.text()).toContain("Add a bank account")
  })

  it("scrolls to the section a link names", async () => {
    mockRoute.hash = "#how-to-pay"
    const scrolled = vi.fn()
    const wrapper = mountInApp(PaymentDetails, {attachTo: document.body})
    Object.defineProperty(wrapper.get("#how-to-pay").element, "scrollIntoView", {value: scrolled})
    await settle()

    expect(scrolled).toHaveBeenCalledWith({block: "start"})
    wrapper.unmount()
  })

  it("shows nothing to somebody not signed in", () => {
    mockStore.getters.getLogin = null
    expect(mountInApp(PaymentDetails).findComponent({name: "OwnContributions"}).exists()).toBe(false)
  })
})
