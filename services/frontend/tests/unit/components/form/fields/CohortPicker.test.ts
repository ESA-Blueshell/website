import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import CohortPicker from "@/components/form/fields/CohortPicker.vue"

const {mockFetchCohortOptions, mockHandleNetworkError} = vi.hoisted(() => ({
  mockFetchCohortOptions: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/domains/cohorts", () => ({fetchCohortOptions: mockFetchCohortOptions}))
vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockHandleNetworkError}))

const cohort = {id: 1, label: "Members", system: "BREVO", kind: "LIST", memberCount: 12}

describe("CohortPicker", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetchCohortOptions.mockResolvedValue([cohort])
  })

  it("names a cohort by its label, where it lives and how many it holds", () => {
    const vm = shallowMount(CohortPicker).vm as any

    expect(vm.itemTitle(cohort)).toBe("Members (BREVO LIST, 12)")
    expect(vm.itemTitle(undefined)).toBe("")
  })

  it("holds what the domain answered, and stops loading", async () => {
    const wrapper = shallowMount(CohortPicker)
    await new Promise((resolve) => setTimeout(resolve))

    expect((wrapper.vm as any).items).toEqual([cohort])
    expect((wrapper.vm as any).loading).toBe(false)
  })

  it("reports a list it could not read", async () => {
    mockFetchCohortOptions.mockRejectedValue(new Error("500"))

    const wrapper = shallowMount(CohortPicker)
    await new Promise((resolve) => setTimeout(resolve))

    expect(mockHandleNetworkError).toHaveBeenCalled()
    expect((wrapper.vm as any).loading).toBe(false)
  })
})
