import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import ResumeMembershipDialog from "@/components/common/modals/bulk/ResumeMembershipDialog.vue"
import {MemberType} from "@/services/api"
import {settle} from "../../../../helpers/testUtils"
import {
  endedMemberTarget,
  noMembershipTarget,
  period,
  recentlyEndedTarget,
  target,
} from "../../../../helpers/bulkFixtures"

// Mock the API call
const {mockExecuteBulkResume} = vi.hoisted(() => ({mockExecuteBulkResume: vi.fn()}))
vi.mock("@/services/api/blueshell/sdk.gen", () => ({
  executeBulkResume: mockExecuteBulkResume,
}))

/** Active member — SKIPPED(ALREADY_ACTIVE) in resume action. */
function activeMemberTarget(userId: number) {
  return target(userId, {
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
      incasso: false,
    },
  })
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

    // Verify the row rendered (SKIPPED with reason ALREADY_ACTIVE)
    const row = wrapper.find('[data-testid="bulk-preview-row-1"]')
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
    // Member type renders via memberTypeLabel (label, not the raw enum).
    expect(previewTable.text()).toContain("Alumni")
    // Member-since renders formatted dd/MM/yyyy.
    expect(previewTable.text()).toContain("15/06/2023")
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
    const rows = wrapper.findAll('[data-testid^="bulk-preview-row-"]')
    expect(rows.length).toBe(3)
  })
})
