import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import {DateTime} from "luxon"
import ContributionPeriodComponent from "@/components/base/ContributionPeriodComponent.vue"
import {settle} from "../../helpers/testUtils"

const {
  mockFindCurrentContributionPeriod,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockFindCurrentContributionPeriod: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  findCurrentContributionPeriod: mockFindCurrentContributionPeriod,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

describe("ContributionPeriodComponent", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("renders membership fee values from current contribution period", async () => {
    const now = DateTime.now()
    mockFindCurrentContributionPeriod.mockResolvedValue({
      data: {
        startDate: now.minus({months: 1}).toISODate(),
        endDate: now.plus({months: 1}).toISODate(),
        fullYearFee: 123.45,
        halfYearFee: 67.89,
        alumniFee: 10.11,
      },
    })

    const wrapper = shallowMount(ContributionPeriodComponent)
    await settle()

    const text = wrapper.text().replace(/\u00A0/g, " ")
    expect(mockFindCurrentContributionPeriod).toHaveBeenCalledTimes(1)
    expect(text).toContain("membership fees for the academic year")
    expect(text).toContain("full year membership")
    expect(text).toContain("half-year membership")
    expect(text).toContain("Alumni membership")
    expect(text).toMatch(/€\s*123[,.]45/)
    expect(text).toMatch(/€\s*67[,.]89/)
    expect(text).toMatch(/€\s*10[,.]11/)
  })

  it("shows an error state when period retrieval fails", async () => {
    const error = new Error("network failure")
    mockFindCurrentContributionPeriod.mockRejectedValue(error)

    const wrapper = shallowMount(ContributionPeriodComponent)
    await settle()

    expect(mockHandleNetworkError).toHaveBeenCalledWith(error)
    expect(wrapper.text()).toContain("Error fetching contribution information")
  })
})
