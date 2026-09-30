import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import ContributionsPage from "@/pages/management/ContributionsPage.vue"
import {BulkFeeType, ContributionEmailKind} from "@/services/api"
import {aContributionPeriod} from "../../helpers/apiFixtures"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  findContributionPeriods: vi.fn(),
  findPeriodContributions: vi.fn(),
  deleteContributionPeriodById: vi.fn(),
}))
const {mockRoute, mockPush, mockHandleNetworkError} = vi.hoisted(() => ({
  mockRoute: {params: {} as Record<string, string>},
  mockPush: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => mockRoute,
  useRouter: () => ({push: mockPush}),
}))

vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockHandleNetworkError}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const member = (userId: number, name: string, fields: Record<string, unknown> = {}) => ({
  userId, name, username: name.toLowerCase(), feeType: BulkFeeType.FULL_YEAR_FEE, fee: 30, incasso: false,
  paid: false, paidAt: null, lastEmailAt: null, lastEmailKind: null, ...fields,
})

describe("the Contributions page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async (periodId?: string) => {
    mockRoute.params = periodId ? {periodId} : {}
    const wrapper = mountInApp(ContributionsPage)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  const rowIds = (wrapper: VueWrapper) =>
    wrapper.findAll('[data-testid^="contribution-row-"]').map((row) => Number(row.attributes("data-testid")!.split("-").at(-1)))

  beforeEach(() => {
    vi.clearAllMocks()
    api.findContributionPeriods.mockResolvedValue({status: 200, data: [
      aContributionPeriod({id: 2, startDate: "2025-09-01", endDate: "2026-08-31", fullYearFee: 30, halfYearFee: 15, alumniFee: 5}),
      aContributionPeriod({id: 1, startDate: "2024-09-01", endDate: "2025-08-31"}),
      aContributionPeriod({id: 3, startDate: "2099-09-01", endDate: "2100-08-31"}),
    ]})
    api.findPeriodContributions.mockResolvedValue({status: 200, data: {
      periodId: 2,
      members: [
        member(1, "Bea", {paid: true, paidAt: "2025-10-01T10:00:00Z", incasso: true}),
        member(2, "Ann", {lastEmailAt: "2025-09-20T10:00:00Z", lastEmailKind: ContributionEmailKind.REMINDER}),
        member(3, "Hon", {feeType: null, fee: null}),
      ],
      runs: [{kind: ContributionEmailKind.REMINDER, sentAt: "2025-09-20T10:00:00Z", recipients: 1}],
    }})
    api.deleteContributionPeriodById.mockResolvedValue({status: 204, data: undefined})
  })

  afterEach(() => {
    unmountAll(wrappers, "ContributionsPage")
  })

  it("lays the periods out oldest first, marks the one that has begun, and shows its money", async () => {
    const wrapper = await mount()

    const tiles = wrapper.findAll('[data-testid^="contribution-period-"]').map((one) => one.attributes("data-testid"))
    expect(tiles.slice(0, 3)).toEqual(["contribution-period-1", "contribution-period-2", "contribution-period-3"])
    expect(wrapper.get('[data-testid="contribution-period-2"]').attributes("aria-current")).toBe("page")
    expect(api.findPeriodContributions).toHaveBeenCalledWith({path: {periodId: 2}})
    expect(wrapper.get('[data-testid="contribution-paid-count"]').text()).toBe("1 of 3")
    expect(wrapper.get('[data-testid="contribution-incasso-count"]').text()).toBe("1")
    expect(wrapper.get('[data-testid="contribution-last-email-3"]').text()).toBe("None yet")
    expect(wrapper.get('[data-testid="contribution-last-email-2"]').text()).toContain("contribution reminder")
    expect(wrapper.get('[data-testid="contribution-row-3"]').text()).toContain("Owes nothing")
    expect(wrapper.get('[data-testid="contribution-runs"]').text()).toContain("Sent to 1")
  })

  it("opens the period its address names", async () => {
    await mount("1")

    expect(api.findPeriodContributions).toHaveBeenCalledWith({path: {periodId: 1}})
  })

  it("sorts the last payment email as a date, and narrows by search and paid", async () => {
    const wrapper = await mount()

    expect(rowIds(wrapper)).toEqual([2, 1, 3])
    await wrapper.get('[data-testid="contribution-sort-last-email"]').trigger("click")
    await settle()
    expect(rowIds(wrapper)[0]).toBe(2)
    await wrapper.get('[data-testid="contribution-sort-last-email"]').trigger("click")
    await settle()
    expect(rowIds(wrapper).at(-1)).toBe(2)
    await wrapper.get('[data-testid="contribution-sort-name"]').trigger("click")
    await settle()

    await wrapper.findComponent({name: "FilterPicker"}).vm.$emit("update:modelValue", "yes")
    await settle()
    expect(rowIds(wrapper)).toEqual([1])
    await wrapper.findComponent({name: "FilterPicker"}).vm.$emit("update:modelValue", null)
    await wrapper.get('[data-testid="contribution-search"]').setValue("ann")
    expect(rowIds(wrapper)).toEqual([2])
    await wrapper.get('[data-testid="contribution-filters-clear"]').trigger("click")
    await settle()
    expect(rowIds(wrapper)).toHaveLength(3)
  })

  it("sends payment emails for the people selected, and marks payments on the task page", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="contribution-checkbox-2"]').trigger("change")
    await settle()
    await wrapper.get('[data-testid="bulk-action-send-payment-emails"]').trigger("click")
    await settle()
    const wizard = wrapper.findComponent({name: "PaymentEmailWizard"})
    expect(wizard.props("userIds")).toEqual([2])
    wizard.vm.$emit("update:modelValue", false)
    wizard.vm.$emit("done")
    await settle()
    expect(api.findPeriodContributions).toHaveBeenCalledTimes(2)

    await wrapper.get('[data-testid="contribution-checkbox-1"]').trigger("change")
    await settle()
    await wrapper.get('[data-testid="bulk-action-mark-paid"]').trigger("click")
    expect(mockPush).toHaveBeenCalledWith({
      path: "/management/users/bulk/paid",
      query: {ids: "1", period: "2", back: "/management/contributions/2"},
    })
    await wrapper.get('[data-testid="bulk-action-mark-unpaid"]').trigger("click")
    expect(mockPush).toHaveBeenLastCalledWith(expect.objectContaining({path: "/management/users/bulk/unpaid"}))
  })

  it("adds, edits and deletes a period", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="contribution-period-new"]').trigger("click")
    await settle()
    const dialog = () => wrapper.findComponent({name: "ContributionPeriodDialog"})
    expect(dialog().props("contributionPeriod")).toBeUndefined()
    dialog().vm.$emit("changed", aContributionPeriod({id: 4}))
    await settle()
    expect(mockPush).toHaveBeenCalledWith("/management/contributions/4")
    dialog().vm.$emit("update:showDialog", false)
    await settle()
    expect(dialog().exists()).toBe(false)

    await wrapper.get('[data-testid="contribution-period-edit"]').trigger("click")
    await settle()
    expect(dialog().props("contributionPeriod")).toMatchObject({id: 2})
    dialog().vm.$emit("changed")
    dialog().vm.$emit("delete", 2)
    await settle()
    wrapper.findComponent({name: "DeletionConfirmationDialog"}).vm.$emit("update:modelValue", true)
    wrapper.findComponent({name: "DeletionConfirmationDialog"}).vm.$emit("confirm")
    await settle()
    expect(api.deleteContributionPeriodById).toHaveBeenCalled()
    expect(mockPush).toHaveBeenLastCalledWith("/management/contributions")
  })

  it("says so when there is no period, when nobody matches, and when a period cannot be deleted", async () => {
    api.findContributionPeriods.mockResolvedValue({status: 200, data: []})
    expect((await mount()).find('[data-testid="contributions-no-period"]').exists()).toBe(true)

    api.findContributionPeriods.mockResolvedValue({status: 200, data: [aContributionPeriod({id: 2, startDate: "2025-09-01"})]})
    api.findPeriodContributions.mockResolvedValue({status: 200, data: {periodId: 2, members: [], runs: []}})
    api.deleteContributionPeriodById.mockRejectedValue(new Error("in use"))
    const wrapper = await mount()
    expect(wrapper.find('[data-testid="contributions-empty"]').exists()).toBe(true)
    wrapper.findComponent({name: "DeletionConfirmationDialog"}).vm.$emit("confirm")
    await settle()
    expect(mockHandleNetworkError).toHaveBeenCalled()

    api.findContributionPeriods.mockRejectedValue(new Error("offline"))
    await mount()
    expect(mockHandleNetworkError).toHaveBeenCalledTimes(2)
  })
})
