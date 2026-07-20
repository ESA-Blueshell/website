import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import ReminderDialog from "@/components/common/modals/bulk/ReminderDialog.vue"
import type {BulkTarget} from "@/utils/bulkTarget"
import {MemberType, type ContributionPeriodResponse} from "@/services/api"
import {settle} from "../../../helpers/testUtils"

// Mock the API call
const mockSendReminder = vi.fn()
vi.mock("@/services/api/blueshell/sdk.gen", () => ({
  sendReminder: mockSendReminder,
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

function regularTarget(userId: number): BulkTarget {
  return target(userId, {
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
      incasso: true,
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

function alreadyPaidTarget(userId: number): BulkTarget {
  return target(userId, {
    mostRecentContribution: {paid: true},
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
      incasso: true,
    },
  })
}

function alumniTarget(userId: number): BulkTarget {
  return target(userId, {
    mostRecentMembership: {
      type: MemberType.ALUMNI,
      startDate: "2024-01-01",
      endDate: null,
      incasso: false,
    },
  })
}

describe("ReminderDialog", () => {
  it("renders the dialog with title and confirm button", () => {
    const wrapper = mount(ReminderDialog, {
      props: {
        modelValue: true,
        targets: [regularTarget(1)],
        period: period(),
        cutoffDate: "2025-06-01",
      },
    })

    expect(wrapper.find('[data-testid="bulk-action-dialog"]').exists()).toBe(true)
  })

  it("marks regular unpaid member with email as INCLUDED", async () => {
    const wrapper = mount(ReminderDialog, {
      props: {
        modelValue: true,
        targets: [regularTarget(1)],
        period: period(),
        cutoffDate: "2025-06-01",
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-1"]')
    expect(dispositionChip.text()).toContain("Included")
  })

  it("marks member with no email as SKIPPED with NO_EMAIL reason", async () => {
    const wrapper = mount(ReminderDialog, {
      props: {
        modelValue: true,
        targets: [noEmailTarget(2)],
        period: period(),
        cutoffDate: "2025-06-01",
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-2"]')
    expect(dispositionChip.text()).toContain("Skipped")

    const noteCell = wrapper.find('[data-testid="bulk-preview-note-2"]')
    expect(noteCell.text()).toContain("No email")
  })

  it("marks honorary member as EXCLUDED with HONORARY reason", async () => {
    const wrapper = mount(ReminderDialog, {
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

  it("marks already-paid member as WARNING with ALREADY_PAID reason", async () => {
    const wrapper = mount(ReminderDialog, {
      props: {
        modelValue: true,
        targets: [alreadyPaidTarget(4)],
        period: period(),
        cutoffDate: "2025-06-01",
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-4"]')
    expect(dispositionChip.text()).toContain("Warning")

    const noteCell = wrapper.find('[data-testid="bulk-preview-note-4"]')
    expect(noteCell.text()).toContain("Already paid")
  })

  it("displays FULL_YEAR_FEE for member starting before cutoff", async () => {
    const wrapper = mount(ReminderDialog, {
      props: {
        modelValue: true,
        targets: [
          target(1, {
            mostRecentMembership: {
              type: MemberType.REGULAR,
              startDate: "2024-12-01",
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

  it("displays HALF_YEAR_FEE for member starting on or after cutoff", async () => {
    const wrapper = mount(ReminderDialog, {
      props: {
        modelValue: true,
        targets: [
          target(1, {
            mostRecentMembership: {
              type: MemberType.REGULAR,
              startDate: "2025-06-15",
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
    expect(text).toContain("10")
  })

  it("displays ALUMNI_FEE for alumni member", async () => {
    const wrapper = mount(ReminderDialog, {
      props: {
        modelValue: true,
        targets: [alumniTarget(1)],
        period: period(),
        cutoffDate: "2025-06-01",
      },
    })

    await settle()

    const text = wrapper.text()
    expect(text).toContain("Alumni")
    expect(text).toContain("5")
  })

  it("shows counts summary with included, warned, and excluded", async () => {
    const wrapper = mount(ReminderDialog, {
      props: {
        modelValue: true,
        targets: [
          regularTarget(1),
          noEmailTarget(2),
          honoraryTarget(3),
          alreadyPaidTarget(4),
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

  it("handles edge case: no email and already paid", async () => {
    const wrapper = mount(ReminderDialog, {
      props: {
        modelValue: true,
        targets: [
          target(1, {
            email: null,
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

    // NO_EMAIL takes precedence
    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-1"]')
    expect(dispositionChip.text()).toContain("Skipped")

    const noteCell = wrapper.find('[data-testid="bulk-preview-note-1"]')
    expect(noteCell.text()).toContain("No email")
  })

  it("handles edge case: no membership", async () => {
    const wrapper = mount(ReminderDialog, {
      props: {
        modelValue: true,
        targets: [
          target(1, {
            mostRecentMembership: null,
          }),
        ],
        period: period(),
        cutoffDate: "2025-06-01",
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-1"]')
    expect(dispositionChip.text()).toContain("Excluded")

    const noteCell = wrapper.find('[data-testid="bulk-preview-note-1"]')
    expect(noteCell.text()).toContain("Honorary")
  })
})
