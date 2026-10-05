import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import RecoveryManager from "@/pages/management/RecoveryManager.vue"
import {aUser} from "../../helpers/apiFixtures"
import {mountInApp, settle, sortByEveryHead, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  findUsers: vi.fn(),
  findDeletedUsers: vi.fn(),
  pendingActivations: vi.fn(),
  lastRecoveryEmails: vi.fn(),
}))
const {mockHandleNetworkError} = vi.hoisted(() => ({mockHandleNetworkError: vi.fn()}))

vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockHandleNetworkError}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const days = (count: number) => new Date(Date.now() + count * 86_400_000).toISOString()

describe("the Account recovery page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(RecoveryManager)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  const rowIds = (wrapper: VueWrapper) =>
    wrapper.findAll('[data-testid^="recovery-user-row-"]').map((row) => Number(row.attributes("data-testid")!.split("-").at(-1)))

  beforeEach(() => {
    vi.clearAllMocks()
    api.findUsers.mockResolvedValue({status: 200, data: {content: [
      aUser({id: 1, fullName: "Ines Inactive", username: "ines", enabled: false, discordId: "1", addressId: 1}),
      aUser({id: 2, fullName: "Ada Active", username: "ada", enabled: true, discordId: null, addressId: 1}),
    ]}})
    api.findDeletedUsers.mockResolvedValue({status: 200, data: {content: [
      aUser({id: 3, fullName: "Dirk Deleted", username: "dirk", enabled: false, restoreUntilAt: days(3), discordId: "1", addressId: 1}),
      aUser({id: 4, fullName: "Gone Late", username: "late", enabled: false, restoreUntilAt: days(-1), discordId: "1", addressId: 1}),
    ]}})
    api.pendingActivations.mockResolvedValue({status: 200, data: {activations: [{userId: 1, purpose: "USER_ACTIVATION"}]}})
    api.lastRecoveryEmails.mockResolvedValue({status: 200, data: {emails: [
      {userId: 1, sentAt: "2026-09-01T10:00:00Z"},
      {userId: 2, sentAt: "2026-09-20T10:00:00Z"},
    ]}})
  })

  afterEach(() => {
    unmountAll(wrappers, "RecoveryManager")
  })

  it("lists every account with the one thing it can be sent or given, and when it was last written to", async () => {
    const wrapper = await mount()

    expect(rowIds(wrapper)).toEqual([2, 3, 4, 1])
    expect(wrapper.get('[data-testid="recovery-state-1"]').text()).toBe("Not activated")
    expect(wrapper.get('[data-testid="recovery-state-3"]').text()).toBe("Deleted")
    expect(wrapper.get('[data-testid="recovery-user-row-3"]').text()).toContain("Restorable for 3 more days")
    expect(wrapper.get('[data-testid="recovery-user-row-4"]').text()).toContain("Window passed")
    expect(wrapper.get('[data-testid="recovery-last-email-3"]').text()).toBe("None yet")
    expect(wrapper.get('[data-testid="recovery-fact-deleted"]').text()).toContain("1 can still be restored")
    expect(wrapper.get('[data-testid="recovery-fact-not-activated"]').text()).toContain("1")
    expect(wrapper.find('[data-testid="recovery-user-send-btn-USER_ACTIVATION-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="recovery-user-send-btn-PASSWORD_RESET-2"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="recovery-user-action-btn-restore-4"]').attributes("disabled")).toBeDefined()
  })

  it("draws each account as a row on a phone, its one act at the end", async () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const wrapper = await mount()
    vi.unstubAllGlobals()

    expect(wrapper.findAllComponents({name: "ManagementRow"})).toHaveLength(4)
    expect(wrapper.get('[data-testid="recovery-user-row-3"]').text()).toContain("Restorable for 3 more days")
    expect(wrapper.get('[data-testid="recovery-user-row-4"]').find('[data-testid="recovery-user-action-btn-restore-4"]').exists()).toBe(true)
  })

  it("sorts the last recovery email as a date, newest first, and back", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="recovery-sort-last-email"]').trigger("click")
    await settle()
    expect(rowIds(wrapper).slice(0, 2)).toEqual([2, 1])
    await wrapper.get('[data-testid="recovery-sort-last-email"]').trigger("click")
    await settle()
    expect(rowIds(wrapper).slice(-2)).toEqual([1, 2])
    await wrapper.get('[data-testid="recovery-sort-name"]').trigger("click")
    await settle()
    expect(rowIds(wrapper)).toEqual([2, 3, 4, 1])
    expect(await sortByEveryHead(wrapper)).toBe(3)
  })

  it("narrows by search, state and Needs a look, and clears them", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="recovery-search"]').setValue("dirk")
    expect(rowIds(wrapper)).toEqual([3])
    await wrapper.get('[data-testid="recovery-search"]').setValue("")
    const [state, needs] = wrapper.findAllComponents({name: "FilterPicker"})
    await state!.vm.$emit("update:modelValue", "deleted")
    await settle()
    expect(rowIds(wrapper)).toEqual([3, 4])
    await state!.vm.$emit("update:modelValue", null)
    await needs!.vm.$emit("update:modelValue", "no-discord")
    await settle()
    expect(rowIds(wrapper)).toEqual([2])
    await needs!.vm.$emit("update:modelValue", "any")
    await settle()
    expect(rowIds(wrapper)).toEqual([2])

    await wrapper.get('[data-testid="recovery-filters-clear"]').trigger("click")
    await settle()
    expect(rowIds(wrapper)).toHaveLength(4)
  })

  it("reads everything again once an action is done", async () => {
    const wrapper = await mount()

    wrapper.findAllComponents({name: "RecoveryAction"})[0]!.vm.$emit("done")
    await settle()

    expect(api.findUsers).toHaveBeenCalledTimes(2)
  })

  it("says so when nobody matches, and when the accounts could not be read", async () => {
    api.findUsers.mockRejectedValue(new Error("offline"))
    const wrapper = await mount()

    expect(wrapper.find('[data-testid="recovery-empty"]').exists()).toBe(true)
    expect(mockHandleNetworkError).toHaveBeenCalled()
  })

  it("says Deleted plainly for an account with no window on it", async () => {
    api.findDeletedUsers.mockResolvedValue({status: 200, data: {content: [aUser({id: 5, fullName: "Old", restoreUntilAt: null})]}})
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="recovery-state-5"]').text()).toBe("Deleted")
  })
})
