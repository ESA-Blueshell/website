import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import ContributionPeriodList from "@/components/common/lists/ContributionPeriodList.vue"

const {mockListPeriods, mockDeletePeriod} = vi.hoisted(() => ({
  mockListPeriods: vi.fn(),
  mockDeletePeriod: vi.fn(),
}))

vi.mock("@/domains/contribution", () => ({
  listPeriods: mockListPeriods,
  deletePeriod: mockDeletePeriod,
}))

describe("ContributionPeriodList", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Deliberately unsorted so the test proves the component sorts by start
    // date and picks the latest itself, rather than trusting the backend order.
    mockListPeriods.mockResolvedValue([
      {id: 2, startDate: "2025-07-01", endDate: "2025-12-31"},
      {id: 3, startDate: "2026-01-01", endDate: "2026-06-30"},
      {id: 1, startDate: "2025-01-01", endDate: "2025-06-30"},
    ])
    mockDeletePeriod.mockResolvedValue({})
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
    expect(mockListPeriods).toHaveBeenCalled()
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
    mockListPeriods.mockRejectedValue(new Error("refused"))
    const wrapper = mount(ContributionPeriodList, {global: {stubs}})

    await flushPromises()

    expect((wrapper.vm as any).periodsUnread).toBe(true)
    expect(wrapper.find('[data-testid="contribution-period-list-unread"]').exists()).toBe(true)
    expect(wrapper.emitted("update:contribution-period")?.at(-1)?.[0]).toBeUndefined()
  })

  it("offers none, and says nothing, where there genuinely are none", async () => {
    mockListPeriods.mockResolvedValue([])
    const wrapper = mount(ContributionPeriodList, {global: {stubs}})

    await flushPromises()

    expect((wrapper.vm as any).periodsUnread).toBe(false)
    expect(wrapper.find('[data-testid="contribution-period-list-unread"]').exists()).toBe(false)
  })

  it("deletes the period whose edit dialog is open, which the confirm names, not the one selected in the strip", async () => {
    const PeriodDialog = {name: "ContributionPeriodDialog", props: ["contributionPeriod"], emits: ["delete"], template: "<div />"}
    const Confirm = {name: "DeleteConfirmationDialog", props: ["message"], emits: ["confirm"], template: "<div />"}
    const wrapper = mount(ContributionPeriodList, {global: {stubs: {...stubs, ContributionPeriodDialog: PeriodDialog, DeleteConfirmationDialog: Confirm}}})
    await flushPromises()

    await wrapper.get('[data-testid="contribution-period-select-btn-1"]').element.parentElement!.dispatchEvent(new MouseEvent("mouseover"))
    await flushPromises()
    await wrapper.get('[data-testid="contribution-period-edit-btn-1"]').trigger("click")
    wrapper.getComponent(PeriodDialog).vm.$emit("delete", 1)
    await flushPromises()

    expect(wrapper.getComponent(Confirm).props("message")).toContain("01/01/2025 - 30/06/2025")
    wrapper.getComponent(Confirm).vm.$emit("confirm")
    await flushPromises()

    expect(mockDeletePeriod).toHaveBeenCalledWith(1)
    expect(mockDeletePeriod).not.toHaveBeenCalledWith(3)
  })
})
