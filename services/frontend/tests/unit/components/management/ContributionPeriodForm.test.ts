import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import ContributionPeriodForm from "@/components/management/ContributionPeriodForm.vue"

const {mockSaveNewPeriod, mockSavePeriod, mockApply, mockHandleNetworkError} = vi.hoisted(() => ({
  mockSaveNewPeriod: vi.fn(),
  mockSavePeriod: vi.fn(),
  mockApply: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/domains/contribution", () => ({
  saveNewPeriod: mockSaveNewPeriod,
  savePeriod: mockSavePeriod,
}))

vi.mock("@/plugins/validation.ts", () => ({
  apply: mockApply,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

vi.mock("@/components/form/fields/VvField.vue", () => ({
  default: {
    name: "VvField",
    template: "<div />",
  },
}))

describe("ContributionPeriodForm", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSaveNewPeriod.mockResolvedValue({id: 11})
    mockSavePeriod.mockResolvedValue({id: 22})
    mockApply.mockReturnValue(false)
  })

  it("creates and updates contribution periods", async () => {
    const createWrapper = mount(ContributionPeriodForm, {
      props: {
      },
      global: {
        stubs: {
          Form: true,
          VvField: true,
        },
      },
    })

    ;(createWrapper.vm as any).formRef = {
      validate: vi.fn().mockResolvedValue({valid: true}),
    }
    ;(createWrapper.vm as any).periodForm.startDate = "2026-01-01"
    ;(createWrapper.vm as any).periodForm.endDate = "2026-06-30"
    ;(createWrapper.vm as any).periodForm.halfYearCutoffDate = "2026-04-01"
    ;(createWrapper.vm as any).periodForm.halfYearFee = 10
    ;(createWrapper.vm as any).periodForm.fullYearFee = 20
    ;(createWrapper.vm as any).periodForm.alumniFee = 5

    await (createWrapper.vm as any).saveContributionPeriod()
    // The cutoff is set where the fees are set, so it travels with them.
    expect(mockSaveNewPeriod).toHaveBeenCalledWith(
      expect.objectContaining({halfYearCutoffDate: "2026-04-01", halfYearFee: 10}),
    )
    expect(createWrapper.emitted("changed")?.[0]).toEqual([{id: 11}])

    const updateWrapper = mount(ContributionPeriodForm, {
      props: {
        contributionPeriod: {
          id: 22,
          startDate: "2026-01-01",
          endDate: "2026-06-30",
          halfYearCutoffDate: "2026-04-01",
          halfYearFee: 10,
          fullYearFee: 20,
          alumniFee: 5,
          listId: "list",
          version: 1,
        },
      },
      global: {
        stubs: {
          Form: true,
          VvField: true,
        },
      },
    })

    ;(updateWrapper.vm as any).formRef = {
      validate: vi.fn().mockResolvedValue({valid: true}),
    }

    await (updateWrapper.vm as any).saveContributionPeriod()
    expect(mockSavePeriod).toHaveBeenCalledWith(
      22,
      expect.objectContaining({version: 1, halfYearCutoffDate: "2026-04-01"}),
    )
  })

  it("says it was cancelled, and refuses to save what does not validate", async () => {
    const wrapper = mount(ContributionPeriodForm, {global: {stubs: {Form: true, VvField: true}}})
    ;(wrapper.vm as any).formRef = {validate: vi.fn().mockResolvedValue({valid: false})}

    await wrapper.get('[data-testid="contribution-period-cancel-btn"]').trigger("click")
    await (wrapper.vm as any).saveContributionPeriod()

    expect(wrapper.emitted("cancelled")).toHaveLength(1)
    expect(mockSaveNewPeriod).not.toHaveBeenCalled()
  })
})
