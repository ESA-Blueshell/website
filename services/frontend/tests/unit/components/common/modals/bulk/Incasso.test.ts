import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import IncassoDialog from "@/components/common/modals/bulk/IncassoDialog.vue"
import type {BulkTarget} from "@/utils/bulkTarget"
import {MemberType, type ContributionPeriodResponse} from "@/services/api"
import {settle} from "../../../../helpers/testUtils"

// Mock the API call
const {mockSendIncasso} = vi.hoisted(() => ({mockSendIncasso: vi.fn()}))
vi.mock("@/services/api/blueshell/sdk.gen", () => ({
  sendIncasso: mockSendIncasso,
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

function period(): ContributionPeriodResponse {
  return {
    id: 1,
    startDate: "2025-01-01",
    endDate: "2025-12-31",
    fullYearFee: 20.0,
    halfYearFee: 10.0,
    alumniFee: 5.0,
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
    version: 0,
  }
}

function withIncassoTarget(userId: number): BulkTarget {
  return target(userId, {
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
      incasso: true,
    },
  })
}

function noIncassoTarget(userId: number): BulkTarget {
  return target(userId, {
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
      incasso: false,
    },
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

function noEmailTarget(userId: number): BulkTarget {
  return target(userId, {
    email: null,
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
      incasso: true,
    },
  })
}

describe("IncassoDialog", () => {
  it("renders the dialog with title and confirm button", () => {
    const wrapper = mount(IncassoDialog, {
      props: {
        modelValue: true,
        targets: [withIncassoTarget(1)],
        period: period(),
        cutoffDate: "2025-06-01",
      },
    })

    expect(wrapper.find('[data-testid="bulk-action-dialog"]').exists()).toBe(true)
  })

  it("marks member with incasso flag as INCLUDED", async () => {
    const wrapper = mount(IncassoDialog, {
      props: {
        modelValue: true,
        targets: [withIncassoTarget(1)],
        period: period(),
        cutoffDate: "2025-06-01",
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-1"]')
    expect(dispositionChip.text()).toContain("Included")
  })

  it("marks member without incasso flag as WARNING with INCASSO_MISMATCH reason", async () => {
    const wrapper = mount(IncassoDialog, {
      props: {
        modelValue: true,
        targets: [noIncassoTarget(2)],
        period: period(),
        cutoffDate: "2025-06-01",
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-2"]')
    expect(dispositionChip.text()).toContain("Warning")

    const noteCell = wrapper.find('[data-testid="bulk-preview-note-2"]')
    // INCASSO_MISMATCH renders via the bulkDisposition label.
    expect(noteCell.text()).toContain("Not marked for incasso")
  })

  it("marks honorary member as EXCLUDED", async () => {
    const wrapper = mount(IncassoDialog, {
      props: {
        modelValue: true,
        targets: [honoraryTarget(3)],
        period: period(),
        cutoffDate: "2025-06-01",
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-3"]')
    expect(dispositionChip.text()).toContain("Excluded")

    const noteCell = wrapper.find('[data-testid="bulk-preview-note-3"]')
    expect(noteCell.text()).toContain("Honorary")
  })

  it("marks member with no email as SKIPPED", async () => {
    const wrapper = mount(IncassoDialog, {
      props: {
        modelValue: true,
        targets: [noEmailTarget(4)],
        period: period(),
        cutoffDate: "2025-06-01",
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-4"]')
    expect(dispositionChip.text()).toContain("Skipped")

    const noteCell = wrapper.find('[data-testid="bulk-preview-note-4"]')
    expect(noteCell.text()).toContain("No email")
  })

  it("keeps a WARNING for an already-paid incasso-mismatch member", async () => {
    const wrapper = mount(IncassoDialog, {
      props: {
        modelValue: true,
        targets: [
          target(1, {
            mostRecentContribution: {paid: true},
            mostRecentMembership: {
              type: MemberType.REGULAR,
              startDate: "2024-01-01",
              endDate: null,
              incasso: false,
            },
          }),
        ],
        period: period(),
        cutoffDate: "2025-06-01",
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-1"]')
    expect(dispositionChip.text()).toContain("Warning")

    const noteCell = wrapper.find('[data-testid="bulk-preview-note-1"]')
    // The incasso check runs last, so INCASSO_MISMATCH is the surfaced reason; the
    // row is still a WARNING (never silently INCLUDED).
    expect(noteCell.text()).toContain("Not marked for incasso")
  })

  it("shows counts summary with included, warned, and excluded", async () => {
    const wrapper = mount(IncassoDialog, {
      props: {
        modelValue: true,
        targets: [
          withIncassoTarget(1),
          noIncassoTarget(2),
          honoraryTarget(3),
          noEmailTarget(4),
        ],
        period: period(),
        cutoffDate: "2025-06-01",
      },
    })

    await settle()

    const countsText = wrapper.find('[data-testid="bulk-action-counts"]').text()
    expect(countsText).toContain("4 selected")
    expect(countsText).toContain("1 will apply")
    expect(countsText).toContain("1 with warnings")
    expect(countsText).toContain("1 excluded")
    expect(countsText).toContain("1 skipped")
  })

  it("displays member fee type and amount", async () => {
    const wrapper = mount(IncassoDialog, {
      props: {
        modelValue: true,
        targets: [
          target(1, {
            mostRecentMembership: {
              type: MemberType.REGULAR,
              startDate: "2024-01-01",
              endDate: null,
              incasso: true,
            },
          }),
        ],
        period: period(),
        cutoffDate: "2025-06-01",
      },
    })

    await settle()

    const text = wrapper.text()
    expect(text).toContain("20")
  })

  it("handles edge case: mixed incasso flag", async () => {
    const wrapper = mount(IncassoDialog, {
      props: {
        modelValue: true,
        targets: [
          withIncassoTarget(1),
          noIncassoTarget(2),
        ],
        period: period(),
        cutoffDate: "2025-06-01",
      },
    })

    await settle()

    expect(wrapper.find('[data-testid="bulk-preview-disposition-1"]').text()).toContain("Included")
    expect(wrapper.find('[data-testid="bulk-preview-disposition-2"]').text()).toContain("Warning")
  })
})
