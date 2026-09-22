import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import {DateTime} from "luxon"
import ContributionPeriodComponent from "@/components/base/ContributionPeriodComponent.vue"
import {settle} from "../../helpers/testUtils"

const {
  mockReadCurrentPeriod,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockReadCurrentPeriod: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/domains/contribution", () => ({
  readCurrentPeriod: mockReadCurrentPeriod,
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
    mockReadCurrentPeriod.mockResolvedValue({
      startDate: now.minus({months: 1}).toISODate(),
      endDate: now.plus({months: 1}).toISODate(),
      fullYearFee: 123.45,
      halfYearFee: 67.89,
      alumniFee: 10.11,
    })

    const wrapper = shallowMount(ContributionPeriodComponent)
    await settle()

    const text = wrapper.text().replace(/\u00A0/g, " ")
    expect(mockReadCurrentPeriod).toHaveBeenCalledTimes(1)
    expect(text).toContain("membership fees for the academic year")
    expect(text).toContain("full year membership")
    expect(text).toContain("half-year membership")
    expect(text).toContain("Alumni membership")
    expect(text).toMatch(/€\s*123[,.]45/)
    expect(text).toMatch(/€\s*67[,.]89/)
    expect(text).toMatch(/€\s*10[,.]11/)
  })

  // A period that has not started is still what the fees are, and the star says it is not the
  // one being charged over yet.
  it("stars a period the association is not inside", async () => {
    const now = DateTime.now()
    mockReadCurrentPeriod.mockResolvedValue({
      startDate: now.plus({months: 1}).toISODate(),
      endDate: now.plus({months: 13}).toISODate(),
      fullYearFee: 1,
      halfYearFee: 1,
      alumniFee: 1,
    })

    const wrapper = shallowMount(ContributionPeriodComponent, {props: {isForm: true}})
    await settle()

    const text = wrapper.text().replace(/\u00A0/g, " ")
    expect(text).toContain("*")
    expect(text).toContain("The undersigned understands")
  })

  it("says there is no period rather than a period of nothing", async () => {
    mockReadCurrentPeriod.mockResolvedValue(null)

    const wrapper = shallowMount(ContributionPeriodComponent)
    await settle()

    expect(wrapper.text()).toContain("N/A")
  })

  it("shows an error state when period retrieval fails", async () => {
    const error = new Error("network failure")
    mockReadCurrentPeriod.mockRejectedValue(error)

    const wrapper = shallowMount(ContributionPeriodComponent)
    await settle()

    expect(mockHandleNetworkError).toHaveBeenCalledWith(error)
    expect(wrapper.text()).toContain("Error fetching contribution information")
  })
})
