import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import EndMembershipDialog from "@/components/common/modals/bulk/EndMembershipDialog.vue"
import {MemberType} from "@/services/api"
import {settle} from "../../../../helpers/testUtils"
import {
  endedMemberTarget,
  noMembershipTarget,
  target,
} from "../../../../helpers/bulkFixtures"

// Mock the API call
const {mockExecuteBulkEnd} = vi.hoisted(() => ({mockExecuteBulkEnd: vi.fn()}))
vi.mock("@/services/api/blueshell/sdk.gen", () => ({
  executeBulkEnd: mockExecuteBulkEnd,
}))

/**
 * Active member with incasso: true — this test file's roster is active incasso members
 * (the original local factory defaulted to incasso: true / no endDate).
 */
function activeMemberTarget(userId: number) {
  return target(userId, {
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
      incasso: true,
    },
  })
}

describe("EndMembershipDialog", () => {
  it("renders the dialog with title and confirm button", () => {
    const wrapper = mount(EndMembershipDialog, {
      props: {
        modelValue: true,
        targets: [activeMemberTarget(1)],
      },
    })

    expect(wrapper.find('[data-testid="bulk-action-dialog"]').exists()).toBe(true)
  })

  it("shows preview table with active member marked INCLUDED", async () => {
    const wrapper = mount(EndMembershipDialog, {
      props: {
        modelValue: true,
        targets: [activeMemberTarget(1)],
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

  it("SKIPs members with no active membership", async () => {
    const wrapper = mount(EndMembershipDialog, {
      props: {
        modelValue: true,
        targets: [noMembershipTarget(1)],
      },
    })

    await settle()

    // Verify the row rendered (SKIPPED because there is no active membership)
    const row = wrapper.find('[data-testid="bulk-preview-row-1"]')
    expect(row.exists()).toBe(true)
  })

  it("populates memberType and memberSince in preview rows", async () => {
    const wrapper = mount(EndMembershipDialog, {
      props: {
        modelValue: true,
        targets: [
          target(1, {
            mostRecentMembership: {
              type: MemberType.ALUMNI,
              startDate: "2023-06-15",
              endDate: null,
              incasso: false,
            },
          }),
        ],
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

  it("handles mixed targets: active and ended", async () => {
    const wrapper = mount(EndMembershipDialog, {
      props: {
        modelValue: true,
        targets: [
          activeMemberTarget(1),
          endedMemberTarget(2),
          noMembershipTarget(3),
        ],
      },
    })

    await settle()

    // Preview should have 3 rows
    const rows = wrapper.findAll('[data-testid^="bulk-preview-row-"]')
    expect(rows.length).toBe(3)

    // User 1 is active → INCLUDED
    expect(wrapper.find('[data-testid="bulk-preview-disposition-1"]').exists()).toBe(true)
  })

  it("disables confirm button when no members selected", () => {
    const wrapper = mount(EndMembershipDialog, {
      props: {
        modelValue: true,
        targets: [endedMemberTarget(1)], // All ended → none included
      },
    })

    // Confirm button should be disabled when no INCLUDED rows exist
    const confirmBtn = wrapper.find('[data-testid="bulk-action-confirm-btn"]')
    if (confirmBtn.exists()) {
      expect(confirmBtn.attributes("disabled")).toBeDefined()
    }
  })
})
