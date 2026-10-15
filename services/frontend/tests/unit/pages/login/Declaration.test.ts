import {beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import {ref} from "vue"
import Declaration from "@/pages/login/Declaration.vue"
import {BookingKind, type Declaration as Held} from "@/domains/declarations"
import {mountInApp, settle} from "../helpers"

const domain = vi.hoisted(() => ({
  readOwnDeclaration: vi.fn(),
  saveDraft: vi.fn(),
  submitDeclaration: vi.fn(),
  withdrawDeclaration: vi.fn(),
  deleteDraft: vi.fn(),
}))
const readOwnBankAccount = vi.hoisted(() => vi.fn())
const readUser = vi.hoisted(() => vi.fn())
const route = vi.hoisted(() => ({params: {id: "new"} as Record<string, string>}))
const router = vi.hoisted(() => ({replace: vi.fn(), push: vi.fn()}))
const mockStore = vi.hoisted(() => ({commit: vi.fn(), getters: {getLogin: {userId: 12, username: "ann"} as {userId: number; username?: string}}}))

vi.mock("@/domains/declarations", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/domains/declarations")>()),
  ...domain,
  useBookingChoices: () => ({committees: ref([]), events: ref([])}),
}))
vi.mock("@/domains/banking", async (importOriginal) => ({...(await importOriginal<typeof import("@/domains/banking")>()), readOwnBankAccount}))
vi.mock("@/domains/user", async (importOriginal) => ({...(await importOriginal<typeof import("@/domains/user")>()), readUser}))
vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../helpers/testUtils")
  return withVueRouter(importOriginal, {route, router})
})
vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})
vi.mock("@/components/common/AccountFrame.vue", () => ({
  default: {name: "AccountFrame", props: ["heading", "crumb", "islandContent", "eyebrow"], template: "<div><slot name=\"actions\" /><slot /></div>"},
}))

const ReceiptPicker = {name: "ReceiptPicker", props: ["modelValue", "invalid", "wrong", "testid"], emits: ["update:modelValue"], template: "<div :data-testid=\"testid\"><p v-if=\"wrong\" :data-testid=\"`${testid}-receipts-wrong`\">{{ wrong }}</p></div>"}
const ConfirmDialog = {name: "ConfirmDialog", props: ["open", "testid", "title", "question", "confirmLabel", "workingLabel"], emits: ["confirm", "update:open"], template: "<div />"}
const ModalDialog = {name: "ModalDialog", props: ["open", "title", "testid", "wide", "cancel"], template: "<div v-if=\"open\" :data-testid=\"testid\"><slot /></div>"}

const receipt = {id: 501, path: "501.webp", name: "bill.webp", type: "image/webp", url: "/r/501"}
const at = "2026-10-02T10:00:00Z"
const held = (over: Partial<Held> = {}): Held => ({
  id: 40,
  submitter: "Ann Vos",
  state: "DRAFT",
  confirmed: true,
  createdAt: at,
  receipts: [],
  expenses: [{id: "41", paidOn: "2026-10-01", description: "Pizza", amount: "12.50", for: null, receipts: [receipt]}],
  steps: [{state: "DRAFT", at, by: "Ann Vos"}],
  ...over,
})

describe("the declaration page", () => {
  const mount = async () => {
    const wrapper = mountInApp(Declaration, {global: {stubs: {ReceiptPicker, ConfirmDialog, ModalDialog}}})
    await settle()
    return wrapper
  }
  const press = async (wrapper: VueWrapper, testid: string) => {
    await wrapper.get(`[data-testid="${testid}"]`).trigger("click")
    await settle()
  }
  const fill = async (wrapper: VueWrapper) => {
    wrapper.getComponent({name: "ExpenseEditor"}).vm.$emit("update:modelValue", held().expenses[0])
    wrapper.getComponent({name: "CheckBox"}).vm.$emit("update:modelValue", true)
    await settle()
  }
  const dialog = (wrapper: VueWrapper, testid: string) =>
    wrapper.findAllComponents(ConfirmDialog).find(one => one.props("testid") === testid)!

  beforeEach(() => {
    vi.clearAllMocks()
    route.params = {id: "new"}
    readUser.mockResolvedValue({fullName: "Ann Vos"})
    readOwnBankAccount.mockResolvedValue({preview: "NL •• … ••00", addedAt: at})
  })

  it("starts a new declaration that says what it misses, and adds and removes expenses", async () => {
    readOwnBankAccount.mockResolvedValue(null)
    readUser.mockRejectedValue(new Error("down"))
    const wrapper = await mount()

    expect(wrapper.find('[data-testid="declaration-no-bank"]').exists()).toBe(true)
    await press(wrapper, "declaration-submit")
    expect(domain.saveDraft).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="declaration-expense-0-receipts-wrong"]').text()).toContain("Add at least one receipt")
    expect(wrapper.text()).toContain("Tick the box to confirm.")
    expect(wrapper.findAllComponents(ReceiptPicker).every(one => one.props("invalid"))).toBe(true)

    await press(wrapper, "declaration-add-expense")
    expect(wrapper.find('[data-testid="declaration-expense-1"]').exists()).toBe(true)
    wrapper.findAllComponents({name: "ExpenseEditor"})[1]!.vm.$emit("touch", "amount")
    await settle()
    expect(wrapper.text()).toContain("Enter an amount above zero.")
    wrapper.findAllComponents(ReceiptPicker).find(one => one.props("testid") === "declaration-whole")!.vm.$emit("update:modelValue", [receipt])
    await settle()
    expect(wrapper.findAllComponents(ReceiptPicker).every(one => !one.props("invalid"))).toBe(true)
    await press(wrapper, "declaration-expense-1-remove")
    expect(wrapper.find('[data-testid="declaration-expense-1"]').exists()).toBe(false)

    await press(wrapper, "declaration-preview-open")
    expect(wrapper.get('[data-testid="declaration-sheet"]').text()).toContain("ann")
    wrapper.getComponent(ModalDialog).vm.$emit("update:open", false)
    await settle()
    expect(wrapper.find('[data-testid="declaration-sheet"]').exists()).toBe(false)
  })

  it("names the writer by their account where nothing else is known", async () => {
    readUser.mockResolvedValue(null)
    mockStore.getters.getLogin = {userId: 12}
    const wrapper = await mount()

    await press(wrapper, "declaration-preview-open")
    expect(wrapper.get('[data-testid="declaration-sheet"]').text()).toContain("NL •• … ••00")
    mockStore.getters.getLogin = {userId: 12, username: "ann"}
  })

  it("saves a new draft under its id, says why a save was refused, and submits once complete", async () => {
    domain.saveDraft.mockResolvedValueOnce({ok: false, reason: "That committee or event no longer exists. Choose another one."})
    domain.saveDraft.mockResolvedValue({ok: true, saved: held()})
    domain.submitDeclaration.mockResolvedValueOnce({ok: false, reason: "Add your bank account before you submit."})
    domain.submitDeclaration.mockResolvedValueOnce({ok: true, saved: held({state: "SUBMITTED", steps: [{state: "SUBMITTED", at, by: "Ann Vos"}]})})
    window.scrollTo = vi.fn()
    const wrapper = await mount()

    await press(wrapper, "declaration-save")
    expect(wrapper.get('[data-testid="declaration-failure"]').text()).toContain("no longer exists")
    await press(wrapper, "declaration-save")
    expect(router.replace).toHaveBeenCalledWith("/account/declarations/40")
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Your draft is saved.")

    await fill(wrapper)
    await press(wrapper, "declaration-submit")
    expect(wrapper.get('[data-testid="declaration-failure"]').text()).toBe("Add your bank account before you submit.")
    await press(wrapper, "declaration-submit")
    expect(wrapper.get('[data-testid="declaration-state"]').text()).toBe("Submitted")
    expect(wrapper.get('[data-testid="declaration-next"]').text()).toContain("The board is checking it.")
  })

  it("stops a submit whose save was refused", async () => {
    domain.saveDraft.mockResolvedValue({ok: false, reason: "Refused."})
    const wrapper = await mount()
    await fill(wrapper)

    await press(wrapper, "declaration-submit")

    expect(domain.saveDraft).toHaveBeenCalled()
    expect(domain.submitDeclaration).not.toHaveBeenCalled()
  })

  it("shows a draft the board sent back with its reason, and deletes a draft once asked", async () => {
    route.params = {id: "40"}
    domain.readOwnDeclaration.mockResolvedValue(held({steps: [{state: "DRAFT", at, by: "B"}, {state: "DRAFT", at, by: "B", reason: "The receipt is missing."}]}))
    domain.deleteDraft.mockResolvedValueOnce({ok: false, reason: "We could not delete the draft. Try again."})
    domain.deleteDraft.mockResolvedValueOnce({ok: true})
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="declaration-sent-back"]').text()).toContain("The receipt is missing.")
    domain.saveDraft.mockResolvedValueOnce({ok: true, saved: held()})
    await press(wrapper, "declaration-save")
    expect(router.replace).not.toHaveBeenCalled()
    await press(wrapper, "declaration-delete")
    dialog(wrapper, "declaration-delete-dialog").vm.$emit("update:open", false)
    await settle()
    expect(dialog(wrapper, "declaration-delete-dialog").props("open")).toBe(false)
    dialog(wrapper, "declaration-delete-dialog").vm.$emit("confirm")
    await settle()
    expect(wrapper.get('[data-testid="declaration-failure"]').text()).toContain("could not delete")
    dialog(wrapper, "declaration-delete-dialog").vm.$emit("confirm")
    await settle()
    expect(router.push).toHaveBeenCalledWith("/account/declarations")
  })

  it("shows a submitted declaration with its receipts, and withdraws it to a draft once asked", async () => {
    route.params = {id: "40"}
    domain.readOwnDeclaration.mockResolvedValue(held({state: "SUBMITTED", receipts: [receipt, receipt], steps: [{state: "SUBMITTED", at, by: "Ann Vos"}]}))
    domain.withdrawDeclaration.mockResolvedValueOnce({ok: false, reason: "The board has already approved this declaration, so you cannot withdraw it."})
    domain.withdrawDeclaration.mockResolvedValueOnce({ok: true, saved: held({confirmed: false})})
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="declaration-expense-row-0"]').text()).toContain("1 receipt")
    expect(wrapper.get('[data-testid="declaration-whole-row"]').text()).toContain("2 receipts")
    await press(wrapper, "declaration-preview-open")
    expect(wrapper.find('[data-testid="declaration-sheet"]').exists()).toBe(true)
    await press(wrapper, "declaration-withdraw")
    dialog(wrapper, "declaration-withdraw-dialog").vm.$emit("update:open", false)
    await settle()
    await press(wrapper, "declaration-withdraw")
    expect(dialog(wrapper, "declaration-withdraw-dialog").props("open")).toBe(true)
    dialog(wrapper, "declaration-withdraw-dialog").vm.$emit("confirm")
    await settle()
    expect(wrapper.get('[data-testid="declaration-failure"]').text()).toContain("already approved")
    dialog(wrapper, "declaration-withdraw-dialog").vm.$emit("confirm")
    await settle()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Your declaration is a draft again.")
    expect(wrapper.find('[data-testid="declaration-edit"]').exists()).toBe(true)
  })

  it("says when a paid declaration was paid, and that one it cannot find is not there", async () => {
    route.params = {id: "40"}
    domain.readOwnDeclaration.mockResolvedValueOnce(held({
      state: "PAID",
      receipts: [receipt],
      expenses: [{id: "41", paidOn: "", description: "Pizza", amount: "", for: {kind: BookingKind.EVENT, id: 7, name: "LAN"}, receipts: [receipt, receipt]}],
      steps: [{state: "SUBMITTED", at, by: "A"}, {state: "PAID", at: "2026-10-09T10:00:00Z", by: "B"}],
    }))
    const paid = await mount()
    expect(paid.get('[data-testid="declaration-paid"]').text()).toContain("9 October 2026")
    expect(paid.get('[data-testid="declaration-expense-row-0"]').text()).toContain("2 receipts")
    expect(paid.find('[data-testid="declaration-withdraw"]').exists()).toBe(false)
    expect(paid.get('[data-testid="declaration-whole-row"]').text()).toContain("1 receipt")

    domain.readOwnDeclaration.mockResolvedValueOnce(held({state: "APPROVED", steps: []}))
    expect((await mount()).get('[data-testid="declaration-paid"]').text()).toContain("Not yet")

    domain.readOwnDeclaration.mockResolvedValueOnce(null)
    expect((await mount()).text()).toContain("This declaration does not exist, or it is not yours.")
  })
})
