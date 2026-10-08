import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import ContributionPeriodDialog from "@/components/common/modals/ContributionPeriodDialog.vue"
import {clearEveryField, saidByLabel} from "../../../helpers/fields"

const {mockSaveNewPeriod, mockSavePeriod, mockHandleNetworkError} = vi.hoisted(() => ({
  mockSaveNewPeriod: vi.fn(),
  mockSavePeriod: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/domains/contribution", () => ({
  saveNewPeriod: mockSaveNewPeriod,
  savePeriod: mockSavePeriod,
}))

vi.mock("@/plugins/handleNetworkError", () => ({
  $handleNetworkError: mockHandleNetworkError,
  $showStatusMessage: vi.fn(),
}))

const period = {
  id: 22,
  startDate: "2026-01-01",
  endDate: "2026-06-30",
  halfYearCutoffDate: "2026-04-01",
  halfYearFee: 10,
  fullYearFee: 20,
  alumniFee: 5,
  listId: "list",
  version: 1,
}

const mountDialog = (props: Record<string, unknown> = {}) => mount(ContributionPeriodDialog, {
  props: {showDialog: true, ...props},
  global: {stubs: {VDialog: {template: "<div><slot /></div>"}}},
})

const fieldLabelled = (wrapper: ReturnType<typeof mount>, label: string) =>
  wrapper.findAllComponents({name: "FormControl"}).find(field => field.props("label") === label)!

describe("ContributionPeriodDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSaveNewPeriod.mockResolvedValue({id: 11})
    mockSavePeriod.mockResolvedValue({id: 22})
  })

  it("creates a period from what is typed", async () => {
    const wrapper = mountDialog()
    const typed: Record<string, string> = {
      "Start Date": "2026-01-01",
      "End Date": "2026-06-30",
      "Half Year Cutoff Date": "2026-04-01",
      "Half Year Fee": "10",
      "Full Year Fee": "20",
      "Alumni Fee": "5",
    }
    for (const [label, value] of Object.entries(typed)) fieldLabelled(wrapper, label).vm.$emit("update:modelValue", value)
    await wrapper.vm.$nextTick()

    await (wrapper.vm as any).saveContributionPeriod()

    // The cutoff is set where the fees are set, so it travels with them.
    expect(mockSaveNewPeriod).toHaveBeenCalledWith(
      expect.objectContaining({halfYearCutoffDate: "2026-04-01", halfYearFee: 10, fullYearFee: 20, alumniFee: 5}),
    )
    expect(wrapper.emitted("changed")?.[0]).toEqual([{id: 11}])
  })

  it("updates the period it was opened on", async () => {
    const wrapper = mountDialog({contributionPeriod: period})

    await (wrapper.vm as any).saveContributionPeriod()

    expect(mockSavePeriod).toHaveBeenCalledWith(
      22,
      expect.objectContaining({version: 1, halfYearCutoffDate: "2026-04-01"}),
    )
  })

  it("keeps the dates in order and the cutoff inside them", async () => {
    const wrapper = mountDialog({
      contributionPeriod: {...period, endDate: "2025-12-01", halfYearCutoffDate: "2027-01-01"},
    })

    await (wrapper.vm as any).saveContributionPeriod()
    await wrapper.vm.$nextTick()

    expect(mockSavePeriod).not.toHaveBeenCalled()
    expect(fieldLabelled(wrapper, "Start Date").props("errorMessages")).toEqual(["Date must be before 2025-12-01"])
    expect(fieldLabelled(wrapper, "End Date").props("errorMessages")).toEqual(["Date must be after 2026-01-01"])
    expect(fieldLabelled(wrapper, "Half Year Cutoff Date").props("errorMessages"))
      .toEqual(["Date must be at most 2025-12-01"])
  })

  it("puts the api's refusal on the field it names", async () => {
    mockSavePeriod.mockRejectedValue({
      response: {status: 400, data: {errors: [{field: "alumniFee", message: "is too high"}]}},
    })
    const wrapper = mountDialog({contributionPeriod: period})

    await (wrapper.vm as any).saveContributionPeriod()
    await flushPromises()

    expect(fieldLabelled(wrapper, "Alumni Fee").props("errorMessages")).toEqual(["is too high"])
    expect(mockHandleNetworkError).not.toHaveBeenCalled()
  })

  it("emits delete intent for selected period", () => {
    const wrapper = mountDialog({contributionPeriod: {...period, id: 55}})

    ;(wrapper.vm as any).confirmDeletePeriod()
    expect(wrapper.emitted("delete")?.[0]).toEqual([55])
  })

  it("says a date left empty is required once it is left", async () => {
    const wrapper = mountDialog()

    await clearEveryField(wrapper)

    expect(saidByLabel(wrapper)).toMatchObject({"Start Date": ["This field is required"], "End Date": ["This field is required"], "Half Year Cutoff Date": ["This field is required"], "Alumni Fee": []})
  })
})
