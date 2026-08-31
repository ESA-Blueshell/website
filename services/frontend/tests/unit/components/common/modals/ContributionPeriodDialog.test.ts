import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import ContributionPeriodDialog from "@/components/common/modals/ContributionPeriodDialog.vue"

const {mockCreateContributionPeriod, mockUpdateContributionPeriod, mockApply, mockHandleNetworkError} = vi.hoisted(() => ({
  mockCreateContributionPeriod: vi.fn(),
  mockUpdateContributionPeriod: vi.fn(),
  mockApply: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  createContributionPeriod: mockCreateContributionPeriod,
  updateContributionPeriod: mockUpdateContributionPeriod,
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

describe("ContributionPeriodDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCreateContributionPeriod.mockResolvedValue({data: {id: 11}})
    mockUpdateContributionPeriod.mockResolvedValue({data: {id: 22}})
    mockApply.mockReturnValue(false)
  })

  it("creates and updates contribution periods", async () => {
    const createWrapper = mount(ContributionPeriodDialog, {
      props: {
        showDialog: true,
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
    expect(mockCreateContributionPeriod).toHaveBeenCalledWith({
      body: expect.objectContaining({halfYearCutoffDate: "2026-04-01", halfYearFee: 10}),
      throwOnError: true,
    })
    expect(createWrapper.emitted("changed")?.[0]).toEqual([{id: 11}])

    const updateWrapper = mount(ContributionPeriodDialog, {
      props: {
        showDialog: true,
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
    expect(mockUpdateContributionPeriod).toHaveBeenCalledWith({
      body: expect.objectContaining({version: 1, halfYearCutoffDate: "2026-04-01"}),
      path: {id: 22},
      throwOnError: true,
    })
  })

  it("emits delete intent for selected period", () => {
    const wrapper = mount(ContributionPeriodDialog, {
      props: {
        showDialog: true,
        contributionPeriod: {
          id: 55,
          startDate: "2026-01-01",
          endDate: "2026-06-30",
          halfYearFee: 10,
          fullYearFee: 20,
          alumniFee: 5,
        },
      },
      global: {
        stubs: {
          Form: true,
          VvField: true,
        },
      },
    })

    ;(wrapper.vm as any).confirmDeletePeriod()
    expect(wrapper.emitted("delete")?.[0]).toEqual([55])
  })
})
