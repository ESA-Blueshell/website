import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import ContributionPeriodForm from "@/components/management/ContributionPeriodForm.vue"

const {mockSaveNewPeriod, mockSavePeriod, mockStatus, mockNetwork} = vi.hoisted(() => ({
  mockSaveNewPeriod: vi.fn(),
  mockSavePeriod: vi.fn(),
  mockStatus: vi.fn(),
  mockNetwork: vi.fn(),
}))

vi.mock("@/domains/contribution", () => ({
  saveNewPeriod: mockSaveNewPeriod,
  savePeriod: mockSavePeriod,
}))

vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockNetwork, $showStatusMessage: mockStatus}))

const period = {
  id: 22,
  startDate: "2026-01-01",
  endDate: "2026-06-30",
  halfYearCutoffDate: "2026-04-01",
  halfYearFee: 10,
  fullYearFee: 20,
  alumniFee: 5,
  contactListId: 9,
  version: 1,
}

describe("ContributionPeriodForm", () => {
  const input = (wrapper: ReturnType<typeof mount>, name: string) => wrapper.get(`[data-testid="contribution-period-${name}-field"] input`)
  const error = (wrapper: ReturnType<typeof mount>, name: string) => wrapper.get(`[data-testid="contribution-period-${name}-field"]`).text()
  const submit = async (wrapper: ReturnType<typeof mount>) => {
    await wrapper.get("form").trigger("submit")
    await flushPromises()
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockSaveNewPeriod.mockResolvedValue({id: 11})
    mockSavePeriod.mockResolvedValue({id: 22})
  })

  it("creates a period from what is typed, with an emptied fee as no fee", async () => {
    const wrapper = mount(ContributionPeriodForm)

    expect((input(wrapper, "full-year-fee").element as HTMLInputElement).value).toBe("0.00")
    await input(wrapper, "start-date").setValue("2026-01-01")
    await input(wrapper, "end-date").setValue("2026-06-30")
    await input(wrapper, "half-year-cutoff").setValue("2026-04-01")
    await input(wrapper, "half-year-fee").setValue("10")
    await input(wrapper, "full-year-fee").setValue("20,5")
    await input(wrapper, "alumni-fee").setValue("")
    await submit(wrapper)

    // The cutoff is set where the fees are set, so it travels with them.
    expect(mockSaveNewPeriod).toHaveBeenCalledWith({
      startDate: "2026-01-01", endDate: "2026-06-30", halfYearCutoffDate: "2026-04-01", halfYearFee: 10, fullYearFee: 20.5, alumniFee: 0, contactListId: undefined,
    })
    expect(wrapper.emitted("changed")?.[0]).toEqual([{id: 11}])
  })

  it("updates the period it was opened on, with that period's version", async () => {
    const wrapper = mount(ContributionPeriodForm, {props: {contributionPeriod: period}})

    expect((input(wrapper, "start-date").element as HTMLInputElement).value).toBe("01/01/2026")
    expect((input(wrapper, "half-year-fee").element as HTMLInputElement).value).toBe("10.00")
    expect(wrapper.get('[data-testid="contribution-period-submit-btn"]').attributes("data-submit-mode")).toBe("update")
    await submit(wrapper)

    expect(mockSavePeriod).toHaveBeenCalledWith(22, expect.objectContaining({version: 1, halfYearCutoffDate: "2026-04-01", contactListId: 9}))
    expect(wrapper.emitted("changed")?.[0]).toEqual([{id: 22}])
  })

  it("says which dates are missing or out of order, and saves nothing until they are right", async () => {
    const wrapper = mount(ContributionPeriodForm)

    expect(error(wrapper, "start-date")).not.toContain("Fill in")
    await submit(wrapper)
    expect(error(wrapper, "start-date")).toContain("Fill in the start date.")
    expect(error(wrapper, "end-date")).toContain("Fill in the end date.")
    expect(error(wrapper, "half-year-cutoff")).toContain("Fill in the half-year cutoff date.")

    await input(wrapper, "start-date").setValue("2026-07-01")
    await input(wrapper, "end-date").setValue("2026-06-30")
    await input(wrapper, "half-year-cutoff").setValue("2026-08-01")
    expect(error(wrapper, "start-date")).toContain("The start date cannot be after the end date.")
    expect(error(wrapper, "half-year-cutoff")).toContain("The cutoff date has to be inside the period.")
    await input(wrapper, "start-date").setValue("2026-01-01")
    await input(wrapper, "half-year-cutoff").setValue("2025-12-01")
    expect(error(wrapper, "half-year-cutoff")).toContain("The cutoff date has to be inside the period.")
    await submit(wrapper)
    expect(mockSaveNewPeriod).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="contribution-period-cancel-btn"]').trigger("click")
    expect(wrapper.emitted("cancelled")).toHaveLength(1)
  })

  it("puts what the api refuses on the field it names, and says the rest out loud", async () => {
    mockSavePeriod.mockRejectedValue({
      response: {
        status: 400,
        data: {
          errors: [
            {objectName: "period", field: "endDate", message: "Overlaps another period."},
            {objectName: "period", field: "endDate", message: "Pick a later date."},
            {objectName: "period", field: "brevoList", message: "The Brevo list is gone."},
          ],
        },
      },
    })
    const wrapper = mount(ContributionPeriodForm, {props: {contributionPeriod: period}})

    await submit(wrapper)

    expect(error(wrapper, "end-date")).toContain("Overlaps another period. Pick a later date.")
    expect(mockStatus).toHaveBeenCalledWith("The Brevo list is gone.")
    expect(wrapper.emitted("changed")).toBeUndefined()
  })

  it("leaves a failure that is no refusal to the network handler", async () => {
    const failure = new Error("offline")
    mockSavePeriod.mockRejectedValue(failure)
    const wrapper = mount(ContributionPeriodForm, {props: {contributionPeriod: period}})

    await submit(wrapper)

    expect(mockNetwork).toHaveBeenCalledWith(failure)
    expect(wrapper.emitted("changed")).toBeUndefined()
  })
})
