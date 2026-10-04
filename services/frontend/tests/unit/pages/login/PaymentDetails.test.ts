import {describe, expect, it, vi} from "vitest"
import PaymentDetails from "@/pages/login/PaymentDetails.vue"
import {mountInApp, settle} from "../helpers"

const {mockStore} = vi.hoisted(() => ({
  mockStore: {getters: {getLogin: {userId: 5, addressId: 12} as {userId: number; addressId?: number} | null}, dispatch: vi.fn()},
}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../helpers/testUtils")
  return withVueRouter(importOriginal, {route: {path: "/account/payment-details", params: {}}, router: {replace: vi.fn()}})
})

vi.mock("@/components/account/IncassoSetUp.vue", async () => {
  const {defineComponent} = await import("vue")
  return {default: defineComponent({name: "IncassoSetUp", props: {addressId: {type: Number, default: null}}, setup: () => () => null})}
})

describe("the account's Payment details page", () => {
  it("is a page of its own under the account, carrying the address the mandate is written to", async () => {
    mockStore.getters.getLogin = {userId: 5, addressId: 12}
    const wrapper = mountInApp(PaymentDetails)
    await settle()

    expect(wrapper.getComponent({name: "AccountFrame"}).props("heading")).toBe("Payment details")
    expect(wrapper.getComponent({name: "IncassoSetUp"}).props("addressId")).toBe(12)
  })

  it("hands on no address where none is on file, and nothing to somebody not signed in", async () => {
    mockStore.getters.getLogin = {userId: 5}
    expect(mountInApp(PaymentDetails).getComponent({name: "IncassoSetUp"}).props("addressId")).toBeNull()

    mockStore.getters.getLogin = null
    expect(mountInApp(PaymentDetails).findComponent({name: "IncassoSetUp"}).exists()).toBe(false)
  })
})
