import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import MarkUnpaidDialog from "@/components/common/modals/bulk/MarkUnpaidDialog.vue"
import type {BulkTarget} from "@/utils/bulkTarget"
import {MemberType} from "@/services/api"
import {settle} from "../../../../helpers/testUtils"

// Mock the API call
const {mockMarkUnpaid} = vi.hoisted(() => ({mockMarkUnpaid: vi.fn()}))
vi.mock("@/services/api/blueshell/sdk.gen", () => ({
  markUnpaid: mockMarkUnpaid,
}))

function target(userId: number, overrides?: Partial<BulkTarget>): BulkTarget {
  return {
    userId,
    name: `User ${userId}`,
    email: `user${userId}@example.com`,
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
      incasso: true,
    },
    mostRecentContribution: {
      paid: false,
    },
    isHonorary: false,
    ...overrides,
  }
}

function paidTarget(userId: number): BulkTarget {
  return target(userId, {
    mostRecentContribution: {paid: true},
  })
}

function unpaidTarget(userId: number): BulkTarget {
  return target(userId, {
    mostRecentContribution: {paid: false},
  })
}

function honoraryTarget(userId: number): BulkTarget {
  return target(userId, {
    isHonorary: true,
    mostRecentMembership: {
      type: MemberType.HONORARY,
      startDate: "2024-01-01",
      endDate: null,
      incasso: false,
    },
  })
}

describe("MarkUnpaidDialog", () => {
  it("renders the dialog with title and confirm button", () => {
    const wrapper = mount(MarkUnpaidDialog, {
      props: {
        modelValue: true,
        targets: [paidTarget(1)],
        contributionPeriodId: 1,
      },
    })

    expect(wrapper.find('[data-testid="bulk-action-dialog"]').exists()).toBe(true)
  })

  it("marks paid member as INCLUDED", async () => {
    const wrapper = mount(MarkUnpaidDialog, {
      props: {
        modelValue: true,
        targets: [paidTarget(1)],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-1"]')
    expect(dispositionChip.text()).toContain("Included")
  })

  it("marks unpaid member as SKIPPED with NOT_PAID note", async () => {
    const wrapper = mount(MarkUnpaidDialog, {
      props: {
        modelValue: true,
        targets: [unpaidTarget(2)],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-2"]')
    expect(dispositionChip.text()).toContain("Skipped")

    const noteCell = wrapper.find('[data-testid="bulk-preview-note-2"]')
    expect(noteCell.text()).toContain("Not paid")
  })

  it("marks honorary member as SKIPPED with HONORARY note", async () => {
    const wrapper = mount(MarkUnpaidDialog, {
      props: {
        modelValue: true,
        targets: [honoraryTarget(3)],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-3"]')
    expect(dispositionChip.text()).toContain("Skipped")

    const noteCell = wrapper.find('[data-testid="bulk-preview-note-3"]')
    expect(noteCell.text()).toContain("Honorary")
  })

  it("displays counts summary with paid and unpaid", async () => {
    const wrapper = mount(MarkUnpaidDialog, {
      props: {
        modelValue: true,
        targets: [
          paidTarget(1),
          unpaidTarget(2),
          honoraryTarget(3),
        ],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const countsText = wrapper.find('[data-testid="bulk-action-counts"]').text()
    expect(countsText).toContain("3 selected")
    expect(countsText).toContain("1 will apply")
    expect(countsText).toContain("2 skipped")
  })

  it("populates member type and member-since", async () => {
    const wrapper = mount(MarkUnpaidDialog, {
      props: {
        modelValue: true,
        targets: [
          target(1, {
            mostRecentMembership: {
              type: MemberType.ALUMNI,
              startDate: "2023-06-01",
              endDate: null,
              incasso: false,
            },
            mostRecentContribution: {paid: true},
          }),
        ],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const typeText = wrapper.text()
    expect(typeText).toContain("Alumni")

    const memberSinceCell = wrapper.find('[data-testid="bulk-preview-member-since-1"]')
    // Member-since renders formatted dd/MM/yyyy.
    expect(memberSinceCell.text()).toContain("01/06/2023")
  })

  it("handles mixed roster: paid, unpaid, honorary", async () => {
    const wrapper = mount(MarkUnpaidDialog, {
      props: {
        modelValue: true,
        targets: [
          paidTarget(1),
          unpaidTarget(2),
          honoraryTarget(3),
        ],
        contributionPeriodId: 1,
      },
    })

    await settle()

    expect(wrapper.find('[data-testid="bulk-preview-disposition-1"]').text()).toContain("Included")
    expect(wrapper.find('[data-testid="bulk-preview-disposition-2"]').text()).toContain("Skipped")
    expect(wrapper.find('[data-testid="bulk-preview-disposition-3"]').text()).toContain("Skipped")
  })
})
