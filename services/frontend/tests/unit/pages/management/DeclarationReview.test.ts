import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import DeclarationReview from "@/pages/management/DeclarationReview.vue"
import {DeclarationState} from "@/services/api"
import {mountInApp, settle, unmountAll} from "../helpers"
import {answered} from "./declarationFixtures"

const api = vi.hoisted(() => ({findDeclaration: vi.fn(), approveDeclaration: vi.fn(), sendBackDeclaration: vi.fn(), rejectDeclaration: vi.fn()}))
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
})
