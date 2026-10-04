import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import ContributionsPage from "@/pages/management/ContributionsPage.vue"
import {BulkFeeType, ContributionEmailKind} from "@/services/api"
import {aContributionPeriod} from "../../helpers/apiFixtures"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  findContributionPeriods: vi.fn(),
  findPeriodContributions: vi.fn(),
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
  })

  afterEach(() => {
    unmountAll(wrappers, "ContributionsPage")
  })

  it("lays the periods out oldest first, marks the one that has begun, and shows its money", async () => {
    const wrapper = await mount()

    const tiles = wrapper.findAll('[data-testid="contribution-periods"] a').map((one) => one.attributes("data-testid"))
    expect(tiles).toEqual(["contribution-period-1", "contribution-period-2", "contribution-period-3", "contribution-period-new"])
    expect(wrapper.get('[data-testid="contribution-period-2"]').attributes("aria-current")).toBe("page")
    expect(api.findPeriodContributions).toHaveBeenCalledWith({path: {periodId: 2}})
    expect(wrapper.get('[data-testid="contribution-paid-count"]').text()).toContain("1 of 3")
    expect(wrapper.get('[data-testid="contribution-incasso-count"]').text()).toContain("1")
    expect(wrapper.get('[data-testid="contribution-last-email-3"]').text()).toBe("None yet")
    expect(wrapper.get('[data-testid="contribution-last-email-2"]').text().toLowerCase()).toContain("contribution reminder")
    expect(wrapper.get('[data-testid="contribution-row-3"]').text()).toContain("Owes nothing")
    expect(wrapper.get('[data-testid="contribution-runs"]').text()).toContain("Sent")
    expect(wrapper.get('[data-testid="contribution-runs"] a').attributes("to")).toBe("/management/mail/sent")
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

  it("lists the period's incassos, a run not yet in ING as waiting for upload", async () => {
    api.findPeriodContributions.mockResolvedValue({status: 200, data: {
      periodId: 2,
      members: [],
      runs: [],
      incassoRuns: [
        {id: 7, collectionDate: "2026-11-01", collections: 2, total: 50, submittedAt: null},
        {id: 6, collectionDate: "2025-11-01", collections: 1, total: 25, submittedAt: "2025-10-20T10:00:00Z"},
      ],
    }})
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="contribution-incasso-run"]').attributes("to")).toBe("/management/contributions/2/incasso")
    expect(wrapper.get('[data-testid="contribution-incasso-7"]').text()).toContain("Waiting for upload to ING")
    expect(wrapper.get('[data-testid="contribution-incasso-7"]').text()).toContain("€ 50.00")
    expect(wrapper.get('[data-testid="contribution-incasso-6"]').text()).toContain("Submitted to ING 20 Oct 2025")
  })

  it("sends reminders for the people selected, and marks payments on the task page", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="contribution-checkbox-2"]').trigger("change")
    await settle()
    await wrapper.get('[data-testid="bulk-action-send-payment-reminders"]').trigger("click")
    expect(mockPush).toHaveBeenCalledWith({path: "/management/contributions/2/reminders", query: {ids: "2"}})
    await wrapper.get('[data-testid="contribution-checkbox-2"]').trigger("change")
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

  it("selects every member shown from the head of the list, then every member, and lets go", async () => {
    const wrapper = await mount()
    const selection = () => wrapper.find('[data-testid="contribution-selection"]')

    await wrapper.get('[data-testid="contribution-search"]').setValue("ann")
    await wrapper.get('[data-testid="contribution-list-select-shown"]').trigger("change")
    await settle()
    expect(selection().text()).toContain("1 selected")
    await wrapper.get('[data-testid="contribution-list-select-all"]').trigger("click")
    await settle()
    expect(selection().text()).toContain("3 selected")
    await wrapper.get('[data-testid="contribution-list-select-all"]').trigger("click")
    await settle()
    expect(selection().exists()).toBe(false)
  })

  it("marks one member's payment from their own row, or withdraws it where they paid", async () => {
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="contribution-mark-1"]').text()).toBe("Withdraw")
    await wrapper.get('[data-testid="contribution-mark-1"]').trigger("click")
    expect(mockPush).toHaveBeenLastCalledWith({path: "/management/users/bulk/unpaid", query: {ids: "1", period: "2", back: "/management/contributions/2"}})
    await wrapper.get('[data-testid="contribution-mark-2"]').trigger("click")
    expect(mockPush).toHaveBeenLastCalledWith(expect.objectContaining({path: "/management/users/bulk/paid", query: expect.objectContaining({ids: "2"})}))
  })

  it("draws each member as a row on a phone, and names a period inside one year by that year", async () => {
    api.findContributionPeriods.mockResolvedValue({status: 200, data: [aContributionPeriod({id: 2, startDate: "2025-01-01", endDate: "2025-12-31"})]})
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const wrapper = await mount()
    vi.unstubAllGlobals()

    expect(wrapper.get('[data-testid="contribution-period-2"]').text()).toContain("20251 Jan 2025 to 31 Dec 2025")
    expect(wrapper.findAllComponents({name: "ManagementRow"}).length).toBeGreaterThan(0)
    await wrapper.get('[data-testid="contribution-checkbox-2"]').trigger("change")
    await settle()
    expect(wrapper.get('[data-testid="contribution-selection"]').text()).toContain("1 selected")
  })

  it("sends New period and Edit period to the period's own page", async () => {
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="contribution-period-new"]').attributes("to")).toBe("/management/contributions/periods/new")
    expect(wrapper.get('[data-testid="contribution-period-edit"]').attributes("to")).toBe("/management/contributions/periods/2")
  })

  it("says so when there is no period, when nobody matches, and when the periods could not be read", async () => {
    api.findContributionPeriods.mockResolvedValue({status: 200, data: []})
    expect((await mount()).find('[data-testid="contributions-no-period"]').exists()).toBe(true)

    api.findContributionPeriods.mockResolvedValue({status: 200, data: [aContributionPeriod({id: 2, startDate: "2025-09-01"})]})
    api.findPeriodContributions.mockResolvedValue({status: 200, data: {periodId: 2, members: [], runs: []}})
    const nobody = await mount()
    expect(nobody.find('[data-testid="contributions-empty"]').exists()).toBe(true)
    expect(nobody.find('[data-testid="contribution-runs-empty"]').exists()).toBe(true)

    api.findContributionPeriods.mockRejectedValue(new Error("offline"))
    await mount()
    expect(mockHandleNetworkError).toHaveBeenCalled()
  })
})
