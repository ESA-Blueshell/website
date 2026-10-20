import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import DeclarationManager from "@/pages/management/DeclarationManager.vue"
import {DeclarationState} from "@/services/api"
import {mountInApp, settle, sortByEveryHead, unmountAll} from "../helpers"
import {answered} from "./declarationFixtures"

const api = vi.hoisted(() => ({listDeclarations: vi.fn(), approveDeclaration: vi.fn(), rejectDeclaration: vi.fn()}))
const mockStore = vi.hoisted(() => ({commit: vi.fn(), getters: {}}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))
vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

const list = [
  answered(1, DeclarationState.SUBMITTED),
  answered(2, DeclarationState.APPROVED, {submitterId: 13, submitter: "Bob Smit"}),
  answered(4, DeclarationState.PAID, {expenses: []}),
  answered(5, DeclarationState.REJECTED),
]

describe("the Declarations page in Management", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(DeclarationManager, {attachTo: document.body})
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }
  const rowIds = (wrapper: VueWrapper) =>
    wrapper.findAll('[data-testid^="declarations-manage-"]').map(row => Number(row.attributes("data-testid")!.split("-").at(-1)))
  const pick = async (wrapper: VueWrapper, testid: string, value: string | null) => {
    wrapper.findAllComponents({name: "FilterPicker"}).find(one => one.props("testid") === testid)!.vm.$emit("update:modelValue", value)
    await settle()
  }
  const tick = async (wrapper: VueWrapper, id: number) => {
    wrapper.findAllComponents({name: "RowCheck"}).find(one => one.props("testid") === `declarations-check-${id}`)!.vm.$emit("toggle")
    await settle()
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.listDeclarations.mockResolvedValue({status: 200, data: list})
    api.approveDeclaration.mockResolvedValue({status: 200, data: answered(1, DeclarationState.APPROVED)})
    api.rejectDeclaration.mockResolvedValue({status: 200, data: answered(1, DeclarationState.REJECTED)})
  })
  afterEach(() => unmountAll(wrappers))

  it("opens on what waits to be checked, with the sums to check and to pay", async () => {
    const wrapper = await mount()

    expect(rowIds(wrapper)).toEqual([1])
    expect(wrapper.get('[data-testid="declarations-to-check"]').text()).toContain("€ 12.50")
    expect(wrapper.get('[data-testid="declarations-to-pay"]').text()).toContain("1 declaration")
    expect(wrapper.get('[data-testid="declarations-manage-1"]').text()).toContain("Autumn LAN party")
  })

  it("filters by status and member and searches by name, expense and event, and clears them all", async () => {
    const wrapper = await mount()
    await pick(wrapper, "declarations-status", null)
    expect(rowIds(wrapper)).toHaveLength(4)

    await pick(wrapper, "declarations-member", "13")
    expect(rowIds(wrapper)).toEqual([2])
    await pick(wrapper, "declarations-member", null)
    for (const [key, id] of [["pay", 2], ["paid", 4], ["rejected", 5]] as const) {
      await pick(wrapper, "declarations-status", key)
      expect(rowIds(wrapper)).toEqual([id])
    }
    await pick(wrapper, "declarations-status", null)

    wrapper.getComponent({name: "SearchBox"}).vm.$emit("update:modelValue", "bob")
    await settle()
    expect(rowIds(wrapper)).toEqual([2])
    wrapper.getComponent({name: "SearchBox"}).vm.$emit("update:modelValue", "lan party")
    await settle()
    expect(rowIds(wrapper)).toHaveLength(3)
    wrapper.getComponent({name: "FilterBar"}).vm.$emit("clear")
    await settle()
    expect(rowIds(wrapper)).toHaveLength(4)
  })

  it("approves the ticked declarations waiting to be checked, and skips the rest", async () => {
    const wrapper = await mount()
    await pick(wrapper, "declarations-status", null)
    await tick(wrapper, 1)
    await tick(wrapper, 2)
    await wrapper.get('[data-testid="declarations-approve"]').trigger("click")
    const dialog = wrapper.getComponent({name: "ConfirmDialog"})
    expect(dialog.props("question")).toContain("1 other is skipped")

    dialog.vm.$emit("confirm")
    await settle()
    expect(api.approveDeclaration).toHaveBeenCalledTimes(1)
    expect(api.approveDeclaration).toHaveBeenCalledWith({path: {id: 1}})
    expect(api.listDeclarations).toHaveBeenCalledTimes(2)
  })

  it("rejects the ticked open ones with a reason, and says how many could not be changed", async () => {
    api.rejectDeclaration.mockResolvedValueOnce({status: 409, error: {code: "DeclarationStateChanged", state: "PAID"}, response: {status: 409}})
    const wrapper = await mount()
    await pick(wrapper, "declarations-status", null)
    wrapper.getComponent({name: "ManagementTable"}).vm.$emit("select-all")
    await settle()
    await wrapper.get('[data-testid="declarations-reject"]').trigger("click")
    expect(document.querySelector('[data-testid="declarations-reject-dialog"]')?.textContent).toContain("2 others are skipped")

    const reason = wrapper.findAllComponents({name: "FormControl"}).find(one => one.props("testid") === "declarations-reject-reason")!
    reason.vm.$emit("update:modelValue", "Not for the association.")
    await settle()
    await wrapper.findAllComponents({name: "CutButton"}).find(one => one.props("testid") === "declarations-reject-confirm")!.trigger("click")
    await settle()

    expect(api.rejectDeclaration).toHaveBeenCalledWith({path: {id: 1}, body: {reason: "Not for the association."}})
    expect(api.rejectDeclaration).toHaveBeenCalledWith({path: {id: 2}, body: {reason: "Not for the association."}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "1 declaration could not be changed.")
  })

  it("ticks and unticks what is shown from the head, and clears the selection", async () => {
    const wrapper = await mount()
    const table = wrapper.getComponent({name: "ManagementTable"})
    table.vm.$emit("toggle-shown")
    await settle()
    expect(table.props("headerState")).toBe("checked")
    table.vm.$emit("toggle-shown")
    await settle()
    expect(table.props("headerState")).toBe("unchecked")
    await tick(wrapper, 1)
    await pick(wrapper, "declarations-status", null)
    expect(table.props("headerState")).toBe("indeterminate")
    await tick(wrapper, 1)
    await tick(wrapper, 1)
    table.vm.$emit("clear-selection")
    await settle()
    expect(table.props("selectedCount")).toBe(0)
  })

  it("is reached from its route, orders by every head, and closes its dialogs when asked", async () => {
    const router = (await import("@/plugins/router")).default
    for (const [path, name] of [["/management/declarations", "declarationManager"], ["/management/declarations/1", "declarationReview"]]) {
      const route = router.resolve(path)
      expect(route.name).toBe(name)
      const load = route.matched.at(-1)!.components!.default as () => Promise<{default: unknown}>
      expect((await load()).default).toBeTruthy()
    }

    const wrapper = await mount()
    await pick(wrapper, "declarations-status", null)
    expect(await sortByEveryHead(wrapper)).toBe(5)
    await tick(wrapper, 1)
    await wrapper.get('[data-testid="declarations-approve"]').trigger("click")
    wrapper.getComponent({name: "ConfirmDialog"}).vm.$emit("update:open", false)
    await settle()
    expect(wrapper.getComponent({name: "ConfirmDialog"}).props("open")).toBe(false)
    await wrapper.get('[data-testid="declarations-reject"]').trigger("click")
    const reject = wrapper.findAllComponents({name: "ModalDialog"}).find(one => one.props("testid") === "declarations-reject-dialog")!
    reject.vm.$emit("update:open", false)
    await settle()
    expect(reject.props("open")).toBe(false)
  })

  it("says when the declarations could not be read, and when nothing matches", async () => {
    api.listDeclarations.mockResolvedValue({status: 500, error: {}, response: {status: 500}})
    expect((await mount()).get('[data-testid="declarations-unread"]').exists()).toBe(true)

    api.listDeclarations.mockResolvedValue({status: 200, data: [answered(2, DeclarationState.APPROVED)]})
    const several = [answered(6, DeclarationState.SUBMITTED, {expenses: [...answered(6, DeclarationState.SUBMITTED).expenses, ...answered(7, DeclarationState.SUBMITTED).expenses]})]
    expect((await mount()).get('[data-testid="declarations-empty"]').exists()).toBe(true)
    api.listDeclarations.mockResolvedValue({status: 200, data: several})
    expect((await mount()).get('[data-testid="declarations-manage-6"]').text()).toContain("2 expenses")
  })
})
