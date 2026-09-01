import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import ContributionEmailDialog from "@/components/common/modals/bulk/ContributionEmailDialog.vue"
import {
  BulkFeeType,
  BulkRowDisposition,
  BulkRowReason,
  ContributionEmailKind,
  MemberType,
  type BulkContributionEmailRowResponse,
  type ContributionPeriodResponse,
} from "@/services/api"
import {settle} from "../../../../helpers/testUtils"

const {mockPreview, mockSend, mockReadEmail} = vi.hoisted(() => ({
  mockPreview: vi.fn(),
  mockSend: vi.fn(),
  mockReadEmail: vi.fn(),
}))
vi.mock("@/services/api/blueshell/sdk.gen", () => ({
  previewBulkContributionEmail: mockPreview,
  sendPaymentEmails: mockSend,
  readContributionEmail: mockReadEmail,
}))

const period: ContributionPeriodResponse = {
  id: 3,
  startDate: "2025-09-01",
  endDate: "2026-08-31",
  halfYearCutoffDate: "2026-02-01",
  halfYearFee: 25,
  fullYearFee: 45,
  alumniFee: 10,
  version: 0,
  createdAt: "2025-09-01T00:00:00Z",
  updatedAt: "2025-09-01T00:00:00Z",
}

function apiRow(
  overrides: Partial<BulkContributionEmailRowResponse> = {},
): BulkContributionEmailRowResponse {
  return {
    userId: 1,
    name: "Ann Regular",
    memberType: MemberType.REGULAR,
    memberSince: "2025-09-01",
    disposition: BulkRowDisposition.INCLUDED,
    reason: null,
    defaultKind: ContributionEmailKind.REMINDER,
    feeType: BulkFeeType.FULL_YEAR_FEE,
    amount: 45,
    lastRemindedOn: null,
    lastNotifiedOn: null,
    ...overrides,
  }
}

async function openDialog(rows: BulkContributionEmailRowResponse[]) {
  mockPreview.mockResolvedValue({data: {contributionPeriodId: period.id, rows}})
  const wrapper = mount(ContributionEmailDialog, {
    props: {modelValue: true, period, userIds: rows.map((row) => row.userId)},
  })
  await settle()
  return wrapper
}

describe("ContributionEmailDialog", () => {
  it("asks the api what the selection would be sent", async () => {
    await openDialog([apiRow({userId: 1}), apiRow({userId: 2, name: "Ben Debit"})])

    expect(mockPreview).toHaveBeenCalledWith({
      body: {contributionPeriodId: 3, userIds: [1, 2]},
    })
  })

  it("seeds each row with the email its flag chose", async () => {
    const wrapper = await openDialog([
      apiRow({userId: 1, defaultKind: ContributionEmailKind.REMINDER}),
      apiRow({userId: 2, name: "Ben Debit", defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION}),
    ])

    expect(wrapper.vm.kindSelections).toEqual({
      1: ContributionEmailKind.REMINDER,
      2: ContributionEmailKind.INCASSO_NOTIFICATION,
    })
  })

  it("counts each kind, and follows a row switched onto the other", async () => {
    const wrapper = await openDialog([
      apiRow({userId: 1, defaultKind: ContributionEmailKind.REMINDER}),
      apiRow({userId: 2, name: "Ben Debit", defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION}),
    ])
    expect(wrapper.vm.kindCounts).toEqual({REMINDER: 1, INCASSO_NOTIFICATION: 1})

    wrapper.vm.kindSelections[2] = ContributionEmailKind.REMINDER
    await settle()

    expect(wrapper.vm.kindCounts).toEqual({REMINDER: 2, INCASSO_NOTIFICATION: 0})
  })

  it("flags a switched row in its note", async () => {
    const wrapper = await openDialog([
      apiRow({userId: 2, name: "Ben Debit", defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION}),
    ])
    expect(wrapper.find('[data-testid="payment-emails-switched-2"]').exists()).toBe(false)

    wrapper.vm.kindSelections[2] = ContributionEmailKind.REMINDER
    await settle()

    expect(wrapper.find('[data-testid="payment-emails-switched-2"]').text())
      .toContain("Pays by direct debit")
  })

  it("reads last sent for the email the row is set to", async () => {
    const wrapper = await openDialog([
      apiRow({
        userId: 2,
        name: "Ben Moved",
        defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION,
        lastRemindedOn: "2026-03-04",
      }),
    ])
    expect(wrapper.find('[data-testid="payment-emails-last-sent-2"]').text()).toBe("Never")

    wrapper.vm.kindSelections[2] = ContributionEmailKind.REMINDER
    await settle()

    expect(wrapper.find('[data-testid="payment-emails-last-sent-2"]').text()).toBe("04/03/2026")
  })

  it("shows a hard-excluded member with the reason, and offers them no choices", async () => {
    const wrapper = await openDialog([
      apiRow({
        userId: 4,
        name: "Cara Honorary",
        memberType: MemberType.HONORARY,
        disposition: BulkRowDisposition.EXCLUDED,
        reason: BulkRowReason.HONORARY,
        feeType: null,
        amount: null,
      }),
    ])

    expect(wrapper.find('[data-testid="bulk-preview-note-4"]').text()).toContain("Honorary")
    expect(wrapper.find('[data-testid="payment-emails-kind-4"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="payment-emails-feetype-4"]').exists()).toBe(false)
  })

  // They may yet be ticked back in, and the fee is part of that decision.
  it("keeps a warned member's choices editable", async () => {
    const wrapper = await openDialog([
      apiRow({
        userId: 2,
        name: "Ben Paid",
        disposition: BulkRowDisposition.WARNING,
        reason: BulkRowReason.ALREADY_PAID,
      }),
    ])

    expect(wrapper.find('[data-testid="bulk-preview-note-2"]').text()).toContain("Already paid")
    expect(wrapper.find('[data-testid="payment-emails-kind-2"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="payment-emails-feetype-2"]').exists()).toBe(true)
  })

  it("re-prices a row when its fee type changes, without asking the api again", async () => {
    const wrapper = await openDialog([apiRow({feeType: BulkFeeType.FULL_YEAR_FEE, amount: 45})])
    expect(wrapper.find('[data-testid="payment-emails-amount-1"]').text()).toContain("45.00")

    wrapper.vm.feeTypeSelections[1] = BulkFeeType.ALUMNI_FEE
    await settle()

    expect(wrapper.find('[data-testid="payment-emails-amount-1"]').text()).toContain("10.00")
    expect(mockPreview).toHaveBeenCalledTimes(1)
  })

  it("posts the selection, the dates it needs and only what was changed", async () => {
    mockSend.mockResolvedValue({data: {remindersSent: 2, incassoNotificationsSent: 0, notWrittenTo: 0}})
    const wrapper = await openDialog([
      apiRow({userId: 1}),
      apiRow({userId: 2, name: "Ben Debit", defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION}),
    ])

    wrapper.vm.paymentDueDate = "2026-04-01"
    wrapper.vm.kindSelections[2] = ContributionEmailKind.REMINDER
    wrapper.vm.feeTypeSelections[2] = BulkFeeType.ALUMNI_FEE
    await settle()
    await wrapper.find('[data-testid="bulk-action-confirm-btn"]').trigger("click")
    await settle()

    expect(mockSend).toHaveBeenCalledWith({
      body: {
        contributionPeriodId: 3,
        userIds: [1, 2],
        forciblyIncludedUserIds: [],
        kindOverrides: {"2": ContributionEmailKind.REMINDER},
        paymentDueDate: "2026-04-01",
        debitDate: undefined,
        feeTypeOverrides: {"2": BulkFeeType.ALUMNI_FEE},
      },
    })
  })

  it("names only the warned rows the operator ticked back in", async () => {
    mockSend.mockResolvedValue({data: {remindersSent: 2, incassoNotificationsSent: 0, notWrittenTo: 1}})
    const wrapper = await openDialog([
      apiRow({userId: 1}),
      apiRow({
        userId: 2,
        name: "Ben Paid",
        disposition: BulkRowDisposition.WARNING,
        reason: BulkRowReason.ALREADY_PAID,
      }),
      apiRow({
        userId: 3,
        name: "Cara Honorary",
        disposition: BulkRowDisposition.EXCLUDED,
        reason: BulkRowReason.HONORARY,
        feeType: null,
        amount: null,
      }),
    ])

    wrapper.vm.paymentDueDate = "2026-04-01"
    await wrapper.findComponent({name: "BulkDialogScaffold"})
      .vm.$emit("update:reincludeOverrides", {2: true})
    await settle()
    await wrapper.find('[data-testid="bulk-action-confirm-btn"]').trigger("click")
    await settle()

    expect(mockSend).toHaveBeenCalledWith(
      expect.objectContaining({body: expect.objectContaining({forciblyIncludedUserIds: [2]})}),
    )
  })

  /** The generated client hands a refusal back rather than throwing. */
  it("reports a refused send rather than closing on it", async () => {
    mockSend.mockResolvedValue({
      response: {status: 409},
      error: {
        errors: [
          {
            code: "NonRecipientFeeTypeUserIds",
            field: "feeTypeOverrides",
            message: "1 of the fee types name members this send does not write to.",
            values: [4],
          },
        ],
      },
    })
    const wrapper = await openDialog([apiRow()])

    wrapper.vm.paymentDueDate = "2026-04-01"
    await settle()
    await wrapper.find('[data-testid="bulk-action-confirm-btn"]').trigger("click")
    await settle()

    const refusal = wrapper.find('[data-testid="payment-emails-rejection"]')
    expect(refusal.exists()).toBe(true)
    expect(refusal.text()).toContain("Nothing was sent")
    expect(wrapper.emitted("done")).toBeUndefined()
  })

  it("says so when the selection cannot be read", async () => {
    mockPreview.mockResolvedValue({data: undefined})
    const wrapper = mount(ContributionEmailDialog, {
      props: {modelValue: true, period, userIds: [1]},
    })
    await settle()

    expect(wrapper.find('[data-testid="payment-emails-load-error"]').exists()).toBe(true)
  })

  it("asks the api for nothing without a selection or a period", async () => {
    mount(ContributionEmailDialog, {props: {modelValue: true, period, userIds: []}})
    mount(ContributionEmailDialog, {props: {modelValue: true, period: null, userIds: [1]}})
    await settle()

    expect(mockPreview).not.toHaveBeenCalled()
  })
})

describe("ContributionEmailDialog previewing an email", () => {
  function givenRenderedEmail(overrides: Record<string, unknown> = {}) {
    mockReadEmail.mockResolvedValue({
      data: {
        kind: ContributionEmailKind.REMINDER,
        feeType: BulkFeeType.FULL_YEAR_FEE,
        subject: "Please pay your Blueshell contribution (2025/2026)",
        html: "<p>Amount due: &euro;45,00</p>",
        recipientEmail: "ann@example.com",
        recipientName: "Ann Regular",
        ...overrides,
      },
    })
  }

  it("cannot preview until the date that member's email needs is given", async () => {
    const wrapper = await openDialog([apiRow()])

    expect(
      wrapper.find('[data-testid="payment-emails-preview-btn"]').attributes("disabled"),
    ).toBeDefined()
  })

  it("renders the email the row is set to, with the date and fee type it shows", async () => {
    givenRenderedEmail()
    const wrapper = await openDialog([apiRow({feeType: BulkFeeType.FULL_YEAR_FEE})])
    wrapper.vm.paymentDueDate = "2026-04-01"
    await settle()

    await wrapper.find('[data-testid="payment-emails-preview-btn"]').trigger("click")
    await settle()

    expect(mockReadEmail).toHaveBeenCalledWith({
      query: {
        kind: ContributionEmailKind.REMINDER,
        contributionPeriodId: 3,
        userId: 1,
        date: "2026-04-01",
        feeType: BulkFeeType.FULL_YEAR_FEE,
      },
    })
    expect(wrapper.find('[data-testid="email-preview-subject"]').text())
      .toContain("Please pay your Blueshell contribution")
  })

  it("sends a switched row's own email and date", async () => {
    givenRenderedEmail({kind: ContributionEmailKind.INCASSO_NOTIFICATION})
    const wrapper = await openDialog([apiRow({userId: 1})])
    wrapper.vm.debitDate = "2026-04-15"
    wrapper.vm.kindSelections[1] = ContributionEmailKind.INCASSO_NOTIFICATION
    await settle()

    await wrapper.find('[data-testid="payment-emails-preview-btn"]').trigger("click")
    await settle()

    expect(mockReadEmail).toHaveBeenCalledWith(
      expect.objectContaining({
        query: expect.objectContaining({
          kind: ContributionEmailKind.INCASSO_NOTIFICATION,
          date: "2026-04-15",
        }),
      }),
    )
  })

  it("names each recipient with the email they get", async () => {
    givenRenderedEmail()
    const wrapper = await openDialog([
      apiRow({userId: 1, name: "Ann Regular"}),
      apiRow({userId: 2, name: "Ben Debit", defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION}),
      apiRow({
        userId: 4,
        name: "Cara Honorary",
        disposition: BulkRowDisposition.EXCLUDED,
        reason: BulkRowReason.HONORARY,
        feeType: null,
        amount: null,
      }),
    ])

    expect(wrapper.vm.previewRecipients).toEqual([
      {value: 1, title: "Ann Regular — Contribution reminder"},
      {value: 2, title: "Ben Debit — Incasso notification"},
    ])
  })

  it("sends nothing when an email is previewed", async () => {
    givenRenderedEmail()
    const wrapper = await openDialog([apiRow()])
    wrapper.vm.paymentDueDate = "2026-04-01"
    await settle()

    await wrapper.find('[data-testid="payment-emails-preview-btn"]').trigger("click")
    await settle()

    expect(mockSend).not.toHaveBeenCalled()
  })
})
