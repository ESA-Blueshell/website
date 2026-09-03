import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import ContributionPeriodList from "@/components/common/lists/ContributionPeriodList.vue"

const {mockFindContributionPeriods, mockDeleteContributionPeriodById} = vi.hoisted(() => ({
  mockFindContributionPeriods: vi.fn(),
  mockDeleteContributionPeriodById: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  findContributionPeriods: mockFindContributionPeriods,
  deleteContributionPeriodById: mockDeleteContributionPeriodById,
}))

describe("ContributionPeriodList", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Deliberately unsorted so the test proves the component sorts by start
    // date and picks the latest itself, rather than trusting the backend order.
    mockFindContributionPeriods.mockResolvedValue({
      data: [
        {id: 2, startDate: "2025-07-01", endDate: "2025-12-31"},
        {id: 3, startDate: "2026-01-01", endDate: "2026-06-30"},
        {id: 1, startDate: "2025-01-01", endDate: "2025-06-30"},
      ],
    })
    mockDeleteContributionPeriodById.mockResolvedValue({})
  })

  it("loads periods and emits the period with the latest start date", async () => {
    const wrapper = mount(ContributionPeriodList, {
      global: {
        stubs: {
          ContributionPeriodDialog: true,
          DeleteConfirmationDialog: true,
          "v-slide-group": {
            template: "<div><slot /></div>",
          },
          "v-slide-group-item": {
            template: "<div><slot :toggle=\"() => {}\" :selectedClass=\"''\" :isSelected=\"false\" /></div>",
          },
        },
      },
    })

    await flushPromises()
    expect(mockFindContributionPeriods).toHaveBeenCalled()
    expect(wrapper.emitted("update:contribution-period")?.at(-1)?.[0]).toEqual({
      id: 3,
      startDate: "2026-01-01",
      endDate: "2026-06-30",
    })
  })

  const stubs = {
    ContributionPeriodDialog: true,
    DeleteConfirmationDialog: true,
    "v-slide-group": {template: "<div><slot /></div>"},
    "v-slide-group-item": {
      template: "<div><slot :toggle=\"() => {}\" :selectedClass=\"''\" :isSelected=\"false\" /></div>",
    },
  }

  it("says the periods could not be read rather than offering none", async () => {
    mockFindContributionPeriods.mockResolvedValue({error: {status: 500}, data: undefined})
    const wrapper = mount(ContributionPeriodList, {global: {stubs}})

    await flushPromises()

    expect((wrapper.vm as any).periodsUnread).toBe(true)
    expect(wrapper.find('[data-testid="contribution-period-list-unread"]').exists()).toBe(true)
    expect(wrapper.emitted("update:contribution-period")?.at(-1)?.[0]).toBeUndefined()
  })

  it("offers none, and says nothing, where there genuinely are none", async () => {
    mockFindContributionPeriods.mockResolvedValue({data: []})
    const wrapper = mount(ContributionPeriodList, {global: {stubs}})

    await flushPromises()

    expect((wrapper.vm as any).periodsUnread).toBe(false)
    expect(wrapper.find('[data-testid="contribution-period-list-unread"]').exists()).toBe(false)
  })

  it("deletes selected contribution period on confirm", async () => {
    const wrapper = mount(ContributionPeriodList, {
      global: {
        stubs: {
          ContributionPeriodDialog: true,
          DeleteConfirmationDialog: true,
          "v-slide-group": {
            template: "<div><slot /></div>",
          },
          "v-slide-group-item": {
            template: "<div><slot :toggle=\"() => {}\" :selectedClass=\"''\" :isSelected=\"false\" /></div>",
          },
        },
      },
    })

    ;(wrapper.vm as any).selectedPeriodId = 2
    await (wrapper.vm as any).confirmDeleteContributionPeriod()
    expect(mockDeleteContributionPeriodById).toHaveBeenCalledWith({path: {id: 2}})
  })
})
