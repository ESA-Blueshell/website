import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import ReminderDialog from "@/components/common/modals/bulk/ReminderDialog.vue"
import type {BulkTarget} from "@/utils/bulkTarget"
import {MemberType, type ContributionPeriodResponse} from "@/services/api"
import {settle} from "../../../../helpers/testUtils"

// Mock the API calls the dialog uses: the bulk executor and the reminder lookup
// (fetched on open to fill the "Last reminded at" column).
const {mockExecuteBulkReminder, mockFindContributionReminders, mockPreviewReminder} = vi.hoisted(() => ({
  mockExecuteBulkReminder: vi.fn(),
  mockFindContributionReminders: vi.fn(),
  mockPreviewReminder: vi.fn(),
}))
vi.mock("@/services/api/blueshell/sdk.gen", () => ({
  executeBulkReminder: mockExecuteBulkReminder,
  findContributionReminders: mockFindContributionReminders,
  previewReminder: mockPreviewReminder,
}))

// Vitest resets mock implementations between tests (mockReset: true), so restore the
// safe default before each test.
beforeEach(() => {
  mockFindContributionReminders.mockResolvedValue({data: []})
  mockExecuteBulkReminder.mockResolvedValue({data: {}})
  mockPreviewReminder.mockResolvedValue({
    data: {subject: "Please pay your Blueshell contribution (2025/2026)", html: "<p>Reminder body</p>"},
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
      incasso: false,
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

// A plain regular member: unpaid, NOT on incasso → INCLUDED.
function regularTarget(userId: number): BulkTarget {
  return target(userId)
}

function incassoPayerTarget(userId: number): BulkTarget {
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

function alreadyPaidTarget(userId: number): BulkTarget {
  return target(userId, {
    mostRecentContribution: {paid: true},
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
      incasso: false,
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

function mountDialog(targets: BulkTarget[]) {
  return mount(ReminderDialog, {
    props: {
      modelValue: true,
      targets,
      period: period(),
      serverToday: SERVER_TODAY,
      latestPeriod: period(),
    },
  })
}

describe("ReminderDialog", () => {
  it("renders the dialog with title and confirm button", () => {
    const wrapper = mountDialog([regularTarget(1)])
    expect(wrapper.find('[data-testid="bulk-action-dialog"]').exists()).toBe(true)
  })

  it("marks regular unpaid non-incasso member with email as INCLUDED", async () => {
    const wrapper = mountDialog([regularTarget(1)])
    await settle()
    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-1"]')
    expect(dispositionChip.text()).toContain("Included")
  })

  it("marks member with no email as SKIPPED with NO_EMAIL reason", async () => {
    const wrapper = mountDialog([noEmailTarget(2)])
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-disposition-2"]').text()).toContain("Skipped")
    expect(wrapper.find('[data-testid="bulk-preview-note-2"]').text()).toContain("No email")
  })

  it("marks honorary member as EXCLUDED with HONORARY reason", async () => {
    const wrapper = mountDialog([honoraryTarget(3)])
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-disposition-3"]').text()).toContain("Excluded")
    expect(wrapper.find('[data-testid="bulk-preview-note-3"]').text()).toContain("Honorary")
  })

  it("marks already-paid member as WARNING with ALREADY_PAID reason", async () => {
    const wrapper = mountDialog([alreadyPaidTarget(4)])
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-disposition-4"]').text()).toContain("Warning")
    expect(wrapper.find('[data-testid="bulk-preview-note-4"]').text()).toContain("Already paid")
  })

  it("marks an incasso-payer as WARNING(PAYS_VIA_INCASSO), off by default", async () => {
    const wrapper = mountDialog([incassoPayerTarget(5)])
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-disposition-5"]').text()).toContain("Warning")
    expect(wrapper.find('[data-testid="bulk-preview-note-5"]').text()).toContain("Pays via incasso")
    // Off by default: nobody will apply until the operator forcibly includes.
    const counts = wrapper.find('[data-testid="bulk-action-counts"]').text()
    expect(counts).toContain("0 will apply")
    expect(counts).toContain("1 with warnings")
  })

  it("strikes through the fee type and amount for an incasso-payer until re-included", async () => {
    const wrapper = mountDialog([incassoPayerTarget(5)])
    await settle()
    // Excluded-by-default: the fee type is shown struck through (not the "—" placeholder),
    // and no editable fee-type select is rendered.
    const struckFee = wrapper.find('[data-testid="bulk-preview-feetype-struck-5"]')
    expect(struckFee.exists()).toBe(true)
    expect(struckFee.classes()).toContain("bulk-struck")
    expect(wrapper.find('[data-testid="bulk-preview-feetype-5"]').exists()).toBe(false)
    // The amount is rendered struck through too.
    const amount = wrapper.find('[data-testid="bulk-preview-amount-5"]')
    expect(amount.exists()).toBe(true)
    expect(amount.classes()).toContain("bulk-struck")

    // Forcibly include → strikethrough is dropped and the editable select takes over.
    const scaffold = wrapper.findComponent({name: "BulkDialogScaffold"})
    scaffold.vm.$emit("update:reinclude-overrides", {5: true})
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-feetype-struck-5"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="bulk-preview-feetype-5"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="bulk-preview-amount-5"]').classes()).not.toContain("bulk-struck")
  })

  it("labels the WARNING re-include column as 'Forcibly include' and can include an incasso-payer", async () => {
    const wrapper = mountDialog([incassoPayerTarget(5)])
    await settle()
    // The include-column header reads "Forcibly include" for reminders.
    expect(wrapper.find('[data-testid="bulk-action-preview-table"]').text()).toContain("Forcibly include")
    // The re-include checkbox for the WARNING row is rendered.
    expect(wrapper.find('[data-testid="bulk-preview-reinclude-5"]').exists()).toBe(true)
    // Forcibly include via the reinclude-overrides v-model (what the checkbox drives).
    const scaffold = wrapper.findComponent({name: "BulkDialogScaffold"})
    scaffold.vm.$emit("update:reinclude-overrides", {5: true})
    await settle()
    // Once forcibly included, the row applies and the disposition reads Included.
    expect(wrapper.find('[data-testid="bulk-preview-disposition-5"]').text()).toContain("Included")
    expect(wrapper.find('[data-testid="bulk-action-counts"]').text()).toContain("1 will apply")
  })

  it("auto-selects FULL_YEAR_FEE and shows its amount for a member starting on/before cutoff", async () => {
    const wrapper = mountDialog([
      target(1, {
        mostRecentMembership: {
          type: MemberType.REGULAR,
          startDate: "2025-01-01",
          endDate: null,
          incasso: false,
        },
      }),
    ])
    await settle()
    // Default cutoff (mid-period +1 month) is after this start → FULL_YEAR_FEE (€ 20).
    expect(wrapper.find('[data-testid="bulk-preview-amount-1"]').text()).toContain("20")
  })

  it("auto-selects HALF_YEAR_FEE for a member starting strictly after cutoff", async () => {
    // Latest period midpoint (2025) +1 month, day 1 → 2025-07-01. A member starting
    // after that resolves to the half-year fee (€ 10).
    const wrapper = mountDialog([
      target(1, {
        mostRecentMembership: {
          type: MemberType.REGULAR,
          startDate: "2025-08-15",
          endDate: null,
          incasso: false,
        },
      }),
    ])
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-amount-1"]').text()).toContain("10")
  })

  it("auto-selects ALUMNI_FEE and shows the alumni amount", async () => {
    const wrapper = mountDialog([alumniTarget(1)])
    await settle()
    expect(wrapper.text()).toContain("Alumni")
    expect(wrapper.find('[data-testid="bulk-preview-amount-1"]').text()).toContain("5")
  })

  it("updates the amount when the operator changes a row's fee type", async () => {
    const wrapper = mountDialog([regularTarget(1)])
    await settle()
    // Starts at the full-year fee (€ 20).
    expect(wrapper.find('[data-testid="bulk-preview-amount-1"]').text()).toContain("20")
    // Switch the row to the half-year fee (what the fee-type v-select drives); the amount
    // re-derives to € 10.
    const vm = wrapper.vm as unknown as {feeTypeSelections: Record<number, string>}
    vm.feeTypeSelections[1] = "HALF_YEAR_FEE"
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-amount-1"]').text()).toContain("10")
  })

  it("sorts the Amount column by the LIVE amount, not the stale compute-time amount", async () => {
    // user1 starts after cutoff → auto HALF_YEAR_FEE (compute-time amount € 10).
    // user2 starts on/before cutoff → auto FULL_YEAR_FEE (compute-time amount € 20).
    const wrapper = mountDialog([
      target(1, {
        mostRecentMembership: {type: MemberType.REGULAR, startDate: "2025-08-15", endDate: null, incasso: false},
      }),
      target(2, {
        mostRecentMembership: {type: MemberType.REGULAR, startDate: "2025-01-01", endDate: null, incasso: false},
      }),
    ])
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-amount-1"]').text()).toContain("10")
    expect(wrapper.find('[data-testid="bulk-preview-amount-2"]').text()).toContain("20")

    // Flip the fee types so the LIVE amounts invert relative to the compute-time amounts:
    // user1 → FULL (live € 20), user2 → HALF (live € 10). row.amount is now stale.
    const vm = wrapper.vm as unknown as {feeTypeSelections: Record<number, string>}
    vm.feeTypeSelections[1] = "FULL_YEAR_FEE"
    vm.feeTypeSelections[2] = "HALF_YEAR_FEE"
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-amount-1"]').text()).toContain("20")
    expect(wrapper.find('[data-testid="bulk-preview-amount-2"]').text()).toContain("10")

    // Click the sortable "Amount" header to sort ascending by amount.
    const amountHeader = wrapper
      .findAll("thead th")
      .find((th) => th.text().includes("Amount"))
    expect(amountHeader).toBeTruthy()
    await amountHeader!.trigger("click")
    await settle()

    // Ascending by the LIVE amount ⇒ user2 (€ 10) before user1 (€ 20). If the comparator
    // still used the stale row.amount it would order user1 (10) before user2 (20).
    const rows = wrapper.findAll('[data-testid^="bulk-preview-row-"]')
    const order = rows.map((r) => r.attributes("data-testid"))
    expect(order.indexOf("bulk-preview-row-2")).toBeLessThan(order.indexOf("bulk-preview-row-1"))
  })

  it("renders the custom columns including a Last-reminded-at column", async () => {
    const wrapper = mountDialog([regularTarget(1)])
    await settle()
    const tableText = wrapper.find('[data-testid="bulk-action-preview-table"]').text()
    expect(tableText).toContain("Fee type")
    expect(tableText).toContain("Amount")
    expect(tableText).toContain("Last reminded at")
    // No reminders on file → "Never" placeholder (never an em-dash).
    expect(wrapper.find('[data-testid="bulk-preview-last-reminded-1"]').text()).toContain("Never")
  })

  it("shows the most-recent reminder date per user in the Last-reminded-at column", async () => {
    mockFindContributionReminders.mockResolvedValueOnce({
      data: [
        {contributionPeriodId: 1, userId: 1, remindedAt: "2025-02-01", createdAt: "2025-02-01", updatedAt: "2025-02-01", version: 0},
        {contributionPeriodId: 1, userId: 1, remindedAt: "2025-03-15", createdAt: "2025-03-15", updatedAt: "2025-03-15", version: 0},
      ],
    })
    const wrapper = mountDialog([regularTarget(1)])
    await settle()
    // Reduced to the most recent, formatted dd/MM/yyyy.
    expect(wrapper.find('[data-testid="bulk-preview-last-reminded-1"]').text()).toContain("15/03/2025")
  })

  it("shows counts summary with included, warned, and excluded", async () => {
    const wrapper = mountDialog([
      regularTarget(1),
      noEmailTarget(2),
      honoraryTarget(3),
      alreadyPaidTarget(4),
    ])
    await settle()
    const countsText = wrapper.find('[data-testid="bulk-action-counts"]').text()
    expect(countsText).toContain("4 selected")
    expect(countsText).toContain("1 will apply")
    expect(countsText).toContain("1 with warnings")
    expect(countsText).toContain("1 excluded")
    expect(countsText).toContain("1 skipped")
  })

  it("opens a help panel from the ? button", async () => {
    const wrapper = mountDialog([regularTarget(1)])
    await settle()
    expect(wrapper.find('[data-testid="bulk-action-help-panel"]').exists()).toBe(false)
    await wrapper.find('[data-testid="bulk-action-help-btn"]').trigger("click")
    await settle()
    const panel = wrapper.find('[data-testid="bulk-action-help-panel"]')
    expect(panel.exists()).toBe(true)
    // Help text must not contain em-dashes.
    expect(panel.text()).not.toContain("—")
  })

  it("blocks submit when the payment-due date is missing or the cutoff is out of range", async () => {
    const wrapper = mountDialog([regularTarget(1)])
    await settle()
    const scaffold = wrapper.findComponent({name: "BulkDialogScaffold"})
    const vm = wrapper.vm as unknown as {paymentDueDate: string; cutoffDate: string}

    // Payment due date is empty and the default cutoff is valid: confirming does nothing.
    scaffold.vm.$emit("confirm")
    await settle()
    expect(mockExecuteBulkReminder).not.toHaveBeenCalled()

    // Provide a valid payment-due date but push the cutoff outside the period.
    vm.paymentDueDate = "2025-09-01"
    vm.cutoffDate = "2030-01-01"
    await settle()
    scaffold.vm.$emit("confirm")
    await settle()
    expect(mockExecuteBulkReminder).not.toHaveBeenCalled()

    // With a valid payment-due date AND an in-range cutoff, the submit goes through.
    vm.cutoffDate = "2025-08-01"
    await settle()
    scaffold.vm.$emit("confirm")
    await settle()
    expect(mockExecuteBulkReminder).toHaveBeenCalledTimes(1)
  })

  it("exposes the validation rules with clear messages", async () => {
    const wrapper = mountDialog([regularTarget(1)])
    await settle()
    const vm = wrapper.vm as unknown as {
      paymentDueRules: Array<(v: string) => true | string>
      cutoffRules: Array<(v: string) => true | string>
    }
    // Required + must-be-after-today for the payment due date.
    expect(vm.paymentDueRules[0]!("")).toContain("required")
    expect(vm.paymentDueRules[1]!("2025-01-01")).toContain("after today")
    // Required + within-period for the cutoff.
    expect(vm.cutoffRules[0]!("")).toContain("required")
    expect(vm.cutoffRules[1]!("2030-01-01")).toContain("within the selected contribution period")
  })

  it("handles edge case: no email and already paid → NO_EMAIL precedence", async () => {
    const wrapper = mountDialog([
      target(1, {
        email: null,
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
    expect(wrapper.find('[data-testid="bulk-preview-disposition-1"]').text()).toContain("Skipped")
    expect(wrapper.find('[data-testid="bulk-preview-note-1"]').text()).toContain("No email")
  })

  it("handles edge case: no membership → EXCLUDED(HONORARY)", async () => {
    const wrapper = mountDialog([target(1, {mostRecentMembership: null})])
    await settle()
    expect(wrapper.find('[data-testid="bulk-preview-disposition-1"]').text()).toContain("Excluded")
    expect(wrapper.find('[data-testid="bulk-preview-note-1"]').text()).toContain("Honorary")
  })

  it("previews the email for the selected included user with the right body and renders the subject", async () => {
    const wrapper = mountDialog([regularTarget(1)])
    await settle()
    // Fill the payment-due date so the preview inputs are ready.
    const vm = wrapper.vm as unknown as {paymentDueDate: string}
    vm.paymentDueDate = "2025-09-01"
    await settle()

    const btn = wrapper.find('[data-testid="bulk-email-preview-btn"]')
    expect(btn.exists()).toBe(true)
    await btn.trigger("click")
    await settle()

    // Calls previewReminder with the selected user, period, fee type and due date.
    expect(mockPreviewReminder).toHaveBeenCalledTimes(1)
    expect(mockPreviewReminder).toHaveBeenCalledWith({
      body: {
        userId: 1,
        contributionPeriodId: 1,
        feeType: "FULL_YEAR_FEE",
        paymentDueDate: "2025-09-01",
      },
    })
    // Renders the returned subject in the nested preview dialog.
    expect(wrapper.find('[data-testid="bulk-email-preview-subject"]').text())
      .toContain("Please pay your Blueshell contribution")
  })

  it("disables the preview button when nothing is included", async () => {
    // A single incasso-payer is a WARNING excluded by default → nobody included.
    const wrapper = mountDialog([incassoPayerTarget(5)])
    await settle()
    const vm = wrapper.vm as unknown as {paymentDueDate: string}
    vm.paymentDueDate = "2025-09-01"
    await settle()
    const btn = wrapper.find('[data-testid="bulk-email-preview-btn"]')
    expect(btn.attributes("disabled")).toBe("true")
    expect(mockPreviewReminder).not.toHaveBeenCalled()
  })

  it("keeps the preview button clickable when the date is missing (validates on click instead)", async () => {
    // There is an included recipient but no payment-due date: the button must NOT be
    // disabled for invalid/missing inputs; clicking it runs form validation, and with the
    // date still empty the request is aborted (no API call).
    const wrapper = mountDialog([regularTarget(1)])
    await settle()
    const btn = wrapper.find('[data-testid="bulk-email-preview-btn"]')
    // Bound :disabled resolves to false (enabled) despite the missing date.
    expect(btn.attributes("disabled")).toBe("false")

    await btn.trigger("click")
    await settle()
    expect(mockPreviewReminder).not.toHaveBeenCalled()
  })
})
