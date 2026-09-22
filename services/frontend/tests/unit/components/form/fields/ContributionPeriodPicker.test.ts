import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import ContributionPeriodPicker from "@/components/form/fields/ContributionPeriodPicker.vue"

const {mockListPeriods, mockHandleNetworkError} = vi.hoisted(() => ({
  mockListPeriods: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/domains/contribution", () => ({listPeriods: mockListPeriods}))
vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockHandleNetworkError}))

const older = {id: 1, startDate: "2025-01-01", endDate: "2025-12-31"}
const newer = {id: 2, startDate: "2026-01-01", endDate: "2026-12-31"}

const settled = () => new Promise((resolve) => setTimeout(resolve))

describe("ContributionPeriodPicker", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockListPeriods.mockResolvedValue([older, newer])
  })

  it("names a period by the years it runs over", () => {
    const vm = shallowMount(ContributionPeriodPicker).vm as any

    expect(vm.itemTitle(newer)).toBe("2026–2026")
    expect(vm.itemTitle({id: 3, startDate: "2027-01-01"})).toBe("2027")
    expect(vm.itemTitle({id: 4})).toBe("Period #4")
    expect(vm.itemTitle(undefined)).toBe("")
  })

  it("puts the period that starts last first", async () => {
    const wrapper = shallowMount(ContributionPeriodPicker)
    await settled()

    expect((wrapper.vm as any).items.map((p: {id: number}) => p.id)).toEqual([2, 1])
    expect((wrapper.vm as any).loading).toBe(false)
  })

  it("reports a refusal and stops loading, rather than drawing an empty choice", async () => {
    mockListPeriods.mockRejectedValue(new Error("500"))
    const wrapper = shallowMount(ContributionPeriodPicker)
    await settled()

    expect(mockHandleNetworkError).toHaveBeenCalled()
    expect((wrapper.vm as any).items).toEqual([])
    expect((wrapper.vm as any).loading).toBe(false)
  })
})
