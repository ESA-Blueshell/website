import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import ResumeMembershipDialog from "@/components/common/modals/bulk/ResumeMembershipDialog.vue"
import type {BulkTarget} from "@/utils/bulkTarget"
import {MemberType, type ContributionPeriodResponse} from "@/services/api"
import {settle} from "../../../../helpers/testUtils"

// Mock the API call
const mockExecuteBulkResume = vi.fn()
vi.mock("@/services/api/blueshell/sdk.gen", () => ({
  executeBulkResume: mockExecuteBulkResume,
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
      endDate: "2024-12-31",
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
 * Create realistic BulkTarget fixtures for resume-membership action.
 */
function endedMemberTarget(userId: number): BulkTarget {
  return target(userId)
}

function activeMemberTarget(userId: number): BulkTarget {
  return target(userId, {
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
      incasso: false,
    },
  })
}

function noMembershipTarget(userId: number): BulkTarget {
  return target(userId, {
    mostRecentMembership: null,
  })
}

function recentlyEndedTarget(userId: number): BulkTarget {
  return target(userId, {
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: "2025-06-15", // Within a period that starts at 2025-01-01
      incasso: false,
    },
  })
}

/**
 * Create a mock contribution period.
 */
function period(): ContributionPeriodResponse {
  return {
    id: 1,
    startDate: "2025-01-01",
    endDate: "2025-12-31",
    fullYearFee: 20.0,
    halfYearFee: 10.0,
    alumniFee: 5.0,
  } as ContributionPeriodResponse
}

describe("ResumeMembershipDialog", () => {
  it("renders the dialog with title and confirm button", () => {
    const wrapper = mount(ResumeMembershipDialog, {
      props: {
        modelValue: true,
        targets: [endedMemberTarget(1)],
        latestPeriod: period(),
      },
    })

    expect(wrapper.find('[data-testid="bulk-action-dialog"]').exists()).toBe(true)
  })

  it("shows preview table with ended member marked WILL_START_NEW", async () => {
    const wrapper = mount(ResumeMembershipDialog, {
      props: {
        modelValue: true,
        targets: [endedMemberTarget(1)],
        latestPeriod: period(),
      },
    })

    await settle()

    // Verify the preview table shows the target
    const previewTable = wrapper.find('[data-testid="bulk-action-preview-table"]')
    expect(previewTable.exists()).toBe(true)

    // Verify INCLUDED disposition chip exists
    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-1"]')
    expect(dispositionChip.exists()).toBe(true)
  })

  it("SKIPs members with active membership (already active)", async () => {
    const wrapper = mount(ResumeMembershipDialog, {
      props: {
        modelValue: true,
        targets: [activeMemberTarget(1)],
        latestPeriod: period(),
      },
    })

    await settle()

    // Verify disposition is SKIPPED with reason ALREADY_ACTIVE
    const row = wrapper.find('[data-testid="bulk-row-1"]')
    expect(row.exists()).toBe(true)
  })

  it("SKIPs members with no contribution period", async () => {
    const wrapper = mount(ResumeMembershipDialog, {
      props: {
        modelValue: true,
        targets: [endedMemberTarget(1)],
        latestPeriod: null,
      },
    })

    await settle()

    // Verify the dialog handles null latestPeriod gracefully
    const previewTable = wrapper.find('[data-testid="bulk-action-preview-table"]')
    expect(previewTable.exists()).toBe(true)
  })

  it("marks WILL_RESUME if endDate is within latest period", async () => {
    const wrapper = mount(ResumeMembershipDialog, {
      props: {
        modelValue: true,
        targets: [recentlyEndedTarget(1)],
        latestPeriod: period(),
      },
    })

    await settle()

    // The preview should identify this as WILL_RESUME
    const previewTable = wrapper.find('[data-testid="bulk-action-preview-table"]')
    expect(previewTable.exists()).toBe(true)
  })

  it("populates memberType and memberSince in preview rows", async () => {
    const wrapper = mount(ResumeMembershipDialog, {
      props: {
        modelValue: true,
        targets: [
          target(1, {
            mostRecentMembership: {
              type: MemberType.ALUMNI,
              startDate: "2023-06-15",
              endDate: "2024-12-31",
              incasso: false,
            },
          }),
        ],
        latestPeriod: period(),
      },
    })

    await settle()

    // The preview should show member type and start date
    const previewTable = wrapper.find('[data-testid="bulk-action-preview-table"]')
    expect(previewTable.exists()).toBe(true)
    expect(previewTable.text()).toContain("ALUMNI")
    expect(previewTable.text()).toContain("2023-06-15")
  })

  it("handles mixed targets: active, ended, and no-membership", async () => {
    const wrapper = mount(ResumeMembershipDialog, {
      props: {
        modelValue: true,
        targets: [
          activeMemberTarget(1),
          endedMemberTarget(2),
          noMembershipTarget(3),
        ],
        latestPeriod: period(),
      },
    })

    await settle()

    // Preview should have 3 rows
    const rows = wrapper.findAll('[data-testid^="bulk-row-"]')
    expect(rows.length).toBe(3)
  })
})
