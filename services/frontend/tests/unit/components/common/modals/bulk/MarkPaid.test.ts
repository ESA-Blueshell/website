import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import MarkPaidDialog from "@/components/common/modals/bulk/MarkPaidDialog.vue"
import type {BulkTarget} from "@/utils/bulkTarget"
import {MemberType} from "@/services/api"
import {settle} from "../../../../helpers/testUtils"

// Mock the API call
const mockMarkPaid = vi.fn()
vi.mock("@/services/api/blueshell/sdk.gen", () => ({
  markPaid: mockMarkPaid,
}))

/**
 * Create a minimal BulkTarget with sensible defaults.
 */
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

/**
 * Create a set of realistic BulkTarget fixtures.
 */
function unpaidRegularTarget(userId: number): BulkTarget {
  return target(userId)
}

function alreadyPaidTarget(userId: number): BulkTarget {
  return target(userId, {
    mostRecentContribution: {paid: true},
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

describe("MarkPaidDialog", () => {
  it("renders the dialog with title and confirm button", () => {
    const wrapper = mount(MarkPaidDialog, {
      props: {
        modelValue: true,
        targets: [unpaidRegularTarget(1)],
        contributionPeriodId: 1,
      },
    })

    expect(wrapper.find('[data-testid="bulk-action-dialog"]').exists()).toBe(true)
  })

  it("shows preview table with single unpaid member marked INCLUDED", async () => {
    const wrapper = mount(MarkPaidDialog, {
      props: {
        modelValue: true,
        targets: [unpaidRegularTarget(1)],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-1"]')
    expect(dispositionChip.exists()).toBe(true)
    expect(dispositionChip.text()).toContain("Included")
  })

  it("marks already-paid member as SKIPPED with ALREADY_PAID note", async () => {
    const wrapper = mount(MarkPaidDialog, {
      props: {
        modelValue: true,
        targets: [alreadyPaidTarget(2)],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-2"]')
    expect(dispositionChip.text()).toContain("Skipped")

    const noteCell = wrapper.find('[data-testid="bulk-preview-note-2"]')
    expect(noteCell.text()).toContain("Already paid")
  })

  it("marks honorary member as SKIPPED with HONORARY note", async () => {
    const wrapper = mount(MarkPaidDialog, {
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

  it("displays member type and member-since date in preview", async () => {
    const wrapper = mount(MarkPaidDialog, {
      props: {
        modelValue: true,
        targets: [
          target(1, {
            mostRecentMembership: {
              type: MemberType.STUDENT,
              startDate: "2024-09-15",
              endDate: null,
              incasso: true,
            },
          }),
        ],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const typeCell = wrapper.text()
    expect(typeCell).toContain("Student")

    const memberSinceCell = wrapper.find('[data-testid="bulk-preview-member-since-1"]')
    expect(memberSinceCell.text()).toContain("2024-09-15")
  })

  it("shows counts summary with included and skipped", async () => {
    const wrapper = mount(MarkPaidDialog, {
      props: {
        modelValue: true,
        targets: [
          unpaidRegularTarget(1),
          alreadyPaidTarget(2),
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

  it("closes dialog when modelValue becomes false", async () => {
    const wrapper = mount(MarkPaidDialog, {
      props: {
        modelValue: true,
        targets: [unpaidRegularTarget(1)],
        contributionPeriodId: 1,
      },
    })

    expect(wrapper.find('[data-testid="bulk-action-dialog"]').exists()).toBe(true)

    await wrapper.setProps({modelValue: false})
    await settle()

    // The dialog should emit update:modelValue when closed
    expect(wrapper.emitted("update:modelValue")).toBeTruthy()
  })

  it("updates preview rows when targets prop changes", async () => {
    const wrapper = mount(MarkPaidDialog, {
      props: {
        modelValue: true,
        targets: [unpaidRegularTarget(1)],
        contributionPeriodId: 1,
      },
    })

    await settle()

    // Change targets to include a paid user
    await wrapper.setProps({
      targets: [unpaidRegularTarget(1), alreadyPaidTarget(2)],
    })

    await settle()

    // Both users should be visible in the table
    expect(wrapper.find('[data-testid="bulk-preview-row-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="bulk-preview-row-2"]').exists()).toBe(true)
  })

  it("handles edge case: empty targets list", async () => {
    const wrapper = mount(MarkPaidDialog, {
      props: {
        modelValue: true,
        targets: [],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const countsText = wrapper.find('[data-testid="bulk-action-counts"]').text()
    expect(countsText).toContain("0 selected")
  })

  it("handles edge case: user with no membership", async () => {
    const wrapper = mount(MarkPaidDialog, {
      props: {
        modelValue: true,
        targets: [
          target(1, {
            mostRecentMembership: null,
          }),
        ],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const memberSinceCell = wrapper.find('[data-testid="bulk-preview-member-since-1"]')
    expect(memberSinceCell.text()).toBe("–")
  })
})
