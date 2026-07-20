import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import IncassoDialog from "@/components/common/modals/bulk/IncassoDialog.vue"
import type {BulkTarget} from "@/utils/bulkTarget"
import {MemberType, type ContributionPeriodResponse} from "@/services/api"
import {settle} from "../../../../helpers/testUtils"

// Mock the bulk executor the dialog calls on confirm and the email-preview endpoint.
const {mockExecuteBulkIncassoNotification, mockPreviewIncassoNotification} = vi.hoisted(() => ({
  mockExecuteBulkIncassoNotification: vi.fn(),
  mockPreviewIncassoNotification: vi.fn(),
}))
vi.mock("@/services/api/blueshell/sdk.gen", () => ({
  executeBulkIncassoNotification: mockExecuteBulkIncassoNotification,
  previewIncassoNotification: mockPreviewIncassoNotification,
}))

beforeEach(() => {
  mockExecuteBulkIncassoNotification.mockResolvedValue({data: {}})
  mockPreviewIncassoNotification.mockResolvedValue({
    data: {subject: "Membership Contribution Collection Notice - Blueshell Esports", html: "<p>Incasso body</p>"},
  })
})

const SERVER_TODAY = "2025-05-01"

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

function mountDialog(targets: BulkTarget[]) {
  return mount(IncassoDialog, {
    props: {
      modelValue: true,
      targets,
      period: period(),
      serverToday: SERVER_TODAY,
      latestPeriod: period(),
    },
  })
}

describe("IncassoDialog", () => {
  it("renders the dialog with title and confirm button", () => {
    const wrapper = mountDialog([withIncassoTarget(1)])
    expect(wrapper.find('[data-testid="bulk-action-dialog"]').exists()).toBe(true)
  })

  it("marks member with incasso flag as INCLUDED", async () => {
    const wrapper = mountDialog([withIncassoTarget(1)])
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-disposition-1"]').text()).toContain("Included")
  })

  it("marks member without incasso flag as WARNING with INCASSO_MISMATCH reason", async () => {
    const wrapper = mountDialog([noIncassoTarget(2)])
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-disposition-2"]').text()).toContain("Warning")
    // INCASSO_MISMATCH renders via the bulkDisposition label.
    expect(wrapper.find('[data-testid="bulk-preview-note-2"]').text()).toContain("Not marked for incasso")
  })

  it("marks honorary member as EXCLUDED", async () => {
    const wrapper = mountDialog([honoraryTarget(3)])
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-disposition-3"]').text()).toContain("Excluded")
    expect(wrapper.find('[data-testid="bulk-preview-note-3"]').text()).toContain("Honorary")
  })

  it("marks member with no email as SKIPPED", async () => {
    const wrapper = mountDialog([noEmailTarget(4)])
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-disposition-4"]').text()).toContain("Skipped")
    expect(wrapper.find('[data-testid="bulk-preview-note-4"]').text()).toContain("No email")
  })

  it("keeps a WARNING for an already-paid incasso-mismatch member", async () => {
    const wrapper = mountDialog([
      target(1, {
        mostRecentContribution: {paid: true},
        mostRecentMembership: {
          type: MemberType.REGULAR,
          startDate: "2024-01-01",
          endDate: null,
          incasso: false,
        },
      }),
    ])
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-disposition-1"]').text()).toContain("Warning")
    // The incasso check runs last, so INCASSO_MISMATCH is the surfaced reason; the row
    // stays a WARNING (never silently INCLUDED).
    expect(wrapper.find('[data-testid="bulk-preview-note-1"]').text()).toContain("Not marked for incasso")
  })

  it("does NOT flag incasso-payers as PAYS_VIA_INCASSO (that is reminder-only)", async () => {
    const wrapper = mountDialog([withIncassoTarget(1)])
    await settle()
    // An incasso member here is INCLUDED, not a PAYS_VIA_INCASSO warning.
    expect(wrapper.find('[data-testid="bulk-preview-disposition-1"]').text()).toContain("Included")
    expect(wrapper.find('[data-testid="bulk-preview-note-1"]').text()).not.toContain("Pays via incasso")
  })

  it("shows counts summary with included, warned, and excluded", async () => {
    const wrapper = mountDialog([
      withIncassoTarget(1),
      noIncassoTarget(2),
      honoraryTarget(3),
      noEmailTarget(4),
    ])
    await settle()
    const countsText = wrapper.find('[data-testid="bulk-action-counts"]').text()
    expect(countsText).toContain("4 selected")
    expect(countsText).toContain("1 will apply")
    expect(countsText).toContain("1 with warnings")
    expect(countsText).toContain("1 excluded")
    expect(countsText).toContain("1 skipped")
  })

  it("renders the custom columns with a dedicated Amount column", async () => {
    const wrapper = mountDialog([withIncassoTarget(1)])
    await settle()
    const tableText = wrapper.find('[data-testid="bulk-action-preview-table"]').text()
    expect(tableText).toContain("Fee type")
    expect(tableText).toContain("Amount")
    // Default cutoff is after the 2024 start → FULL_YEAR_FEE (€ 20).
    expect(wrapper.find('[data-testid="bulk-preview-amount-1"]').text()).toContain("20")
  })

  it("auto-selects HALF_YEAR_FEE for a member starting strictly after cutoff", async () => {
    const wrapper = mountDialog([
      withIncassoTarget(1),
    ])
    // Override the row's membership to start after the default cutoff (2025-08-01).
    await wrapper.setProps({
      targets: [
        target(1, {
          mostRecentMembership: {
            type: MemberType.REGULAR,
            startDate: "2025-08-15",
            endDate: null,
            incasso: true,
          },
        }),
      ],
    })
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-amount-1"]').text()).toContain("10")
  })

  it("updates the amount when the operator changes a row's fee type", async () => {
    const wrapper = mountDialog([withIncassoTarget(1)])
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-amount-1"]').text()).toContain("20")
    const vm = wrapper.vm as unknown as {feeTypeSelections: Record<number, string>}
    vm.feeTypeSelections[1] = "HALF_YEAR_FEE"
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-amount-1"]').text()).toContain("10")
  })

  it("labels the WARNING re-include column as 'Forcibly include' and can include a mismatch", async () => {
    const wrapper = mountDialog([noIncassoTarget(2)])
    await settle()
    expect(wrapper.find('[data-testid="bulk-action-preview-table"]').text()).toContain("Forcibly include")
    expect(wrapper.find('[data-testid="bulk-preview-reinclude-2"]').exists()).toBe(true)
    const scaffold = wrapper.findComponent({name: "BulkDialogScaffold"})
    scaffold.vm.$emit("update:reinclude-overrides", {2: true})
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-disposition-2"]').text()).toContain("Included")
    expect(wrapper.find('[data-testid="bulk-action-counts"]').text()).toContain("1 will apply")
  })

  it("opens a help panel from the ? button with no em-dashes", async () => {
    const wrapper = mountDialog([withIncassoTarget(1)])
    await settle()
    expect(wrapper.find('[data-testid="bulk-action-help-panel"]').exists()).toBe(false)
    await wrapper.find('[data-testid="bulk-action-help-btn"]').trigger("click")
    await settle()
    const panel = wrapper.find('[data-testid="bulk-action-help-panel"]')
    expect(panel.exists()).toBe(true)
    expect(panel.text()).not.toContain("—")
  })

  it("blocks submit when the expected-incasso date is missing or the cutoff is out of range", async () => {
    const wrapper = mountDialog([withIncassoTarget(1)])
    await settle()
    const scaffold = wrapper.findComponent({name: "BulkDialogScaffold"})
    const vm = wrapper.vm as unknown as {expectedIncassoDate: string; cutoffDate: string}

    // Expected incasso date is empty: confirming does nothing.
    scaffold.vm.$emit("confirm")
    await settle()
    expect(mockExecuteBulkIncassoNotification).not.toHaveBeenCalled()

    // Valid date but cutoff pushed outside the period.
    vm.expectedIncassoDate = "2025-09-01"
    vm.cutoffDate = "2030-01-01"
    await settle()
    scaffold.vm.$emit("confirm")
    await settle()
    expect(mockExecuteBulkIncassoNotification).not.toHaveBeenCalled()

    // Valid date and in-range cutoff: submit goes through.
    vm.cutoffDate = "2025-08-01"
    await settle()
    scaffold.vm.$emit("confirm")
    await settle()
    expect(mockExecuteBulkIncassoNotification).toHaveBeenCalledTimes(1)
  })

  it("exposes the validation rules with clear messages", async () => {
    const wrapper = mountDialog([withIncassoTarget(1)])
    await settle()
    const vm = wrapper.vm as unknown as {
      incassoDateRules: Array<(v: string) => true | string>
      cutoffRules: Array<(v: string) => true | string>
    }
    expect(vm.incassoDateRules[0]!("")).toContain("required")
    expect(vm.incassoDateRules[1]!("2025-01-01")).toContain("after today")
    expect(vm.cutoffRules[0]!("")).toContain("required")
    expect(vm.cutoffRules[1]!("2030-01-01")).toContain("within the selected contribution period")
  })

  it("handles edge case: mixed incasso flag", async () => {
    const wrapper = mountDialog([withIncassoTarget(1), noIncassoTarget(2)])
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-disposition-1"]').text()).toContain("Included")
    expect(wrapper.find('[data-testid="bulk-preview-disposition-2"]').text()).toContain("Warning")
  })

  it("previews the email for the selected included user with the right body and renders the subject", async () => {
    const wrapper = mountDialog([withIncassoTarget(1)])
    await settle()
    // Fill the expected-incasso date so the preview inputs are ready.
    const vm = wrapper.vm as unknown as {expectedIncassoDate: string}
    vm.expectedIncassoDate = "2025-09-01"
    await settle()

    const btn = wrapper.find('[data-testid="bulk-email-preview-btn"]')
    expect(btn.exists()).toBe(true)
    await btn.trigger("click")
    await settle()

    // Calls previewIncassoNotification with the selected user, period, fee type and date.
    expect(mockPreviewIncassoNotification).toHaveBeenCalledTimes(1)
    expect(mockPreviewIncassoNotification).toHaveBeenCalledWith({
      body: {
        userId: 1,
        contributionPeriodId: 1,
        feeType: "FULL_YEAR_FEE",
        expectedIncassoDate: "2025-09-01",
      },
    })
    expect(wrapper.find('[data-testid="bulk-email-preview-subject"]').text())
      .toContain("Membership Contribution Collection Notice - Blueshell Esports")
  })

  it("disables the preview button when nothing is included", async () => {
    // A single incasso-mismatch is a WARNING excluded by default → nobody included.
    const wrapper = mountDialog([noIncassoTarget(2)])
    await settle()
    const vm = wrapper.vm as unknown as {expectedIncassoDate: string}
    vm.expectedIncassoDate = "2025-09-01"
    await settle()
    const btn = wrapper.find('[data-testid="bulk-email-preview-btn"]')
    expect(btn.attributes("disabled")).toBeDefined()
    expect(mockPreviewIncassoNotification).not.toHaveBeenCalled()
  })
})
