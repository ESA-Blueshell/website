import {describe, expect, it, vi} from "vitest"
import {mount, type VueWrapper} from "@vue/test-utils"
import PaymentEmailWizard from "@/components/common/modals/bulk/paymentEmail/PaymentEmailWizard.vue"
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

/**
 * Vuetify is not installed in the unit environment, so its inputs would render as unresolved
 * elements that emit nothing. These stubs are the smallest thing that behaves like the real
 * control from the point of view of the wizard: a value in, an event out.
 */
const stubs = {
  VCheckbox: {
    props: ["modelValue"],
    emits: ["update:modelValue"],
    template: `<input type="checkbox" :checked="modelValue"
      @change="$emit('update:modelValue', !modelValue)">`,
  },
  VSelect: {
    props: ["modelValue", "items"],
    emits: ["update:modelValue"],
    template: `<select :value="modelValue"
      @change="$emit('update:modelValue', $event.target.value)">
      <option v-for="item in items" :key="item.value" :value="item.value">{{ item.title }}</option>
    </select>`,
  },
  VTextField: {
    props: ["modelValue", "errorMessages", "hint"],
    emits: ["update:modelValue"],
    template: `<div><input :value="modelValue"
      @input="$emit('update:modelValue', $event.target.value)">
      <span class="field-error">{{ errorMessages }}</span>
      <span class="field-hint">{{ hint }}</span></div>`,
  },
}

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

/** Every date the wizard accepts must be after today, so the fixtures move with the clock. */
const soon = new Date(Date.now() + 30 * 24 * 3_600_000).toISOString().slice(0, 10)
const alsoSoon = new Date(Date.now() + 45 * 24 * 3_600_000).toISOString().slice(0, 10)

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

type Wizard = VueWrapper<InstanceType<typeof PaymentEmailWizard>>

async function openWizard(rows: BulkContributionEmailRowResponse[]): Promise<Wizard> {
  mockPreview.mockResolvedValue({data: {contributionPeriodId: period.id, rows}})
  const wrapper = mount(PaymentEmailWizard, {
    global: {stubs},
    props: {modelValue: true, period, userIds: rows.map((row) => row.userId)},
  })
  await settle()
  return wrapper
}

async function next(wrapper: Wizard) {
  await wrapper.find('[data-testid="payment-emails-next-btn"]').trigger("click")
  await settle()
}

async function back(wrapper: Wizard) {
  await wrapper.find('[data-testid="payment-emails-back-btn"]').trigger("click")
  await settle()
}

async function tick(wrapper: Wizard, userId: number) {
  await wrapper.find(`[data-testid="payment-emails-send-to-${userId}"]`).trigger("change")
  await settle()
}

async function typeDate(wrapper: Wizard, testid: string, value: string) {
  const field = wrapper.find(`[data-testid="${testid}"] input`)
  await field.setValue(value)
  await settle()
}

async function chooseKind(wrapper: Wizard, userId: number, kind: ContributionEmailKind) {
  const select = wrapper.find(`[data-testid="payment-emails-kind-${userId}"]`)
  await select.setValue(kind)
  await settle()
}

async function chooseFee(wrapper: Wizard, userId: number, fee: BulkFeeType) {
  const select = wrapper.find(`[data-testid="payment-emails-feetype-${userId}"]`)
  await select.setValue(fee)
  await settle()
}

/** Step 3's Send opens the summary; the summary sends. */
async function sendFromSummary(wrapper: Wizard) {
  await next(wrapper)
  await wrapper.find('[data-testid="payment-emails-confirm-send-btn"]').trigger("click")
  await settle()
}

describe("PaymentEmailWizard step 1, the members", () => {
  it("asks the api what the selection would be sent", async () => {
    await openWizard([apiRow({userId: 1}), apiRow({userId: 2, name: "Ben Debit"})])

    expect(mockPreview).toHaveBeenCalledWith({
      body: {contributionPeriodId: 3, userIds: [1, 2]},
    })
  })

  it("ticks a member the api would write to and leaves a warned one unticked", async () => {
    const wrapper = await openWizard([
      apiRow({userId: 1}),
      apiRow({
        userId: 2,
        name: "Ben Paid",
        disposition: BulkRowDisposition.WARNING,
        reason: BulkRowReason.ALREADY_PAID,
      }),
    ])

    expect(wrapper.find('[data-testid="payment-emails-send-to-1"]').element)
      .toHaveProperty("checked", true)
    expect(wrapper.find('[data-testid="payment-emails-send-to-2"]').element)
      .toHaveProperty("checked", false)
  })

  it("offers no box at all to a member it cannot email, and says why", async () => {
    const wrapper = await openWizard([
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

    expect(wrapper.find('[data-testid="payment-emails-send-to-4"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="payment-emails-reason-4"]').text())
      .toBe("Owes no contribution")
  })

  it("counts the members it cannot email in the words the treasurer reads", async () => {
    const wrapper = await openWizard([
      apiRow({userId: 1}),
      apiRow({
        userId: 4,
        name: "Cara Honorary",
        disposition: BulkRowDisposition.EXCLUDED,
        reason: BulkRowReason.HONORARY,
        feeType: null,
        amount: null,
      }),
    ])

    expect(wrapper.find('[data-testid="payment-emails-count-excluded"]').text())
      .toContain("1 cannot be emailed")
  })

  it("drops an unticked member from the batch", async () => {
    const wrapper = await openWizard([apiRow({userId: 1}), apiRow({userId: 2, name: "Ben Debit"})])

    await tick(wrapper, 2)
    await next(wrapper)

    expect(wrapper.find('[data-testid="payment-emails-fee-row-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="payment-emails-fee-row-2"]').exists()).toBe(false)
  })

  it("goes no further with nobody ticked", async () => {
    const wrapper = await openWizard([apiRow({userId: 1})])

    await tick(wrapper, 1)
    await next(wrapper)

    expect(wrapper.find('[data-testid="payment-emails-members-table"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="payment-emails-fees-table"]').exists()).toBe(false)
  })
})

describe("PaymentEmailWizard step 2, the fees and emails", () => {
  it("shows only the members still ticked", async () => {
    const wrapper = await openWizard([
      apiRow({userId: 1}),
      apiRow({
        userId: 2,
        name: "Ben Paid",
        disposition: BulkRowDisposition.WARNING,
        reason: BulkRowReason.ALREADY_PAID,
      }),
    ])

    await next(wrapper)

    expect(wrapper.find('[data-testid="payment-emails-fee-row-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="payment-emails-fee-row-2"]').exists()).toBe(false)
  })

  it("warns by name when a member is moved onto the other email", async () => {
    const wrapper = await openWizard([
      apiRow({userId: 2, name: "Ben Debit", defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION}),
    ])
    await next(wrapper)
    expect(wrapper.find('[data-testid="payment-emails-kind-warning"]').exists()).toBe(false)

    await chooseKind(wrapper, 2, ContributionEmailKind.REMINDER)

    const warning = wrapper.find('[data-testid="payment-emails-kind-warning"]')
    expect(warning.text()).toContain("Ben Debit")
    expect(warning.text()).toContain("pay twice")
  })

  it("warns separately, by name, when a member's fee type is changed", async () => {
    const wrapper = await openWizard([apiRow({userId: 1, feeType: BulkFeeType.FULL_YEAR_FEE})])
    await next(wrapper)
    expect(wrapper.find('[data-testid="payment-emails-fee-warning"]').exists()).toBe(false)

    await chooseFee(wrapper, 1, BulkFeeType.ALUMNI_FEE)

    expect(wrapper.find('[data-testid="payment-emails-fee-warning"]').text())
      .toContain("Ann Regular")
    expect(wrapper.find('[data-testid="payment-emails-kind-warning"]').exists()).toBe(false)
  })

  it("re-prices a row from the period, without asking the api again", async () => {
    const wrapper = await openWizard([apiRow({feeType: BulkFeeType.FULL_YEAR_FEE, amount: 45})])
    await next(wrapper)
    expect(wrapper.find('[data-testid="payment-emails-amount-1"]').text()).toContain("45.00")

    await chooseFee(wrapper, 1, BulkFeeType.ALUMNI_FEE)

    expect(wrapper.find('[data-testid="payment-emails-amount-1"]').text()).toContain("10.00")
    expect(mockPreview).toHaveBeenCalledTimes(1)
  })

  // A member moved onto direct debit has been asked by transfer, never pre-notified.
  it("reads last sent for the email the row is set to", async () => {
    const wrapper = await openWizard([
      apiRow({
        userId: 2,
        name: "Ben Moved",
        defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION,
        lastRemindedOn: "2026-03-04",
      }),
    ])
    await next(wrapper)
    expect(wrapper.find('[data-testid="payment-emails-last-sent-2"]').text()).toBe("Never")

    await chooseKind(wrapper, 2, ContributionEmailKind.REMINDER)

    expect(wrapper.find('[data-testid="payment-emails-last-sent-2"]').text()).toBe("04/03/2026")
  })
})

describe("PaymentEmailWizard step 3, what will be sent", () => {
  it("lists each recipient with the email and the amount they get", async () => {
    const wrapper = await openWizard([apiRow({userId: 1, feeType: BulkFeeType.FULL_YEAR_FEE})])
    await next(wrapper)
    await next(wrapper)

    const recipient = wrapper.find('[data-testid="payment-emails-recipient-1"]')
    expect(recipient.text()).toContain("Ann Regular")
    expect(recipient.text()).toContain("Contribution reminder")
    expect(recipient.text()).toContain("Full-year fee")
    expect(recipient.text()).toContain("45.00")
  })

  it("says a date nobody needs is optional", async () => {
    const wrapper = await openWizard([apiRow({userId: 1})])
    await next(wrapper)
    await next(wrapper)

    expect(wrapper.find('[data-testid="payment-emails-debit-date"]').text())
      .toContain("nobody in this batch is on direct debit")
  })

  it("marks a date the api would refuse", async () => {
    const wrapper = await openWizard([apiRow({userId: 1})])
    await next(wrapper)
    await next(wrapper)

    await typeDate(wrapper, "payment-emails-payment-due-date", "2030-01-01")

    expect(wrapper.find('[data-testid="payment-emails-payment-due-date"] .field-error').text())
      .toContain("The date must fall between 01/09/2025 and 30/11/2026.")
  })

  it("renders one member's own email, with the date and fee type their row shows", async () => {
    mockReadEmail.mockResolvedValue({
      data: {
        kind: ContributionEmailKind.REMINDER,
        subject: "Please pay your Blueshell contribution",
        html: "<p>Amount due</p>",
        recipientEmail: "ann@example.com",
        recipientName: "Ann Regular",
      },
    })
    const wrapper = await openWizard([apiRow({userId: 1})])
    await next(wrapper)
    await next(wrapper)
    await typeDate(wrapper, "payment-emails-payment-due-date", soon)

    await wrapper.find('[data-testid="payment-emails-preview-1"]').trigger("click")
    await settle()

    expect(mockReadEmail).toHaveBeenCalledWith({
      query: {
        kind: ContributionEmailKind.REMINDER,
        contributionPeriodId: 3,
        userId: 1,
        date: soon,
        feeType: BulkFeeType.FULL_YEAR_FEE,
      },
    })
    expect(mockSend).not.toHaveBeenCalled()
  })
})

describe("PaymentEmailWizard moving between steps", () => {
  it("keeps every choice made on a later step when the member list is revisited", async () => {
    const wrapper = await openWizard([apiRow({userId: 1}), apiRow({userId: 2, name: "Ben Debit"})])
    await next(wrapper)
    await chooseFee(wrapper, 2, BulkFeeType.ALUMNI_FEE)
    await next(wrapper)
    await typeDate(wrapper, "payment-emails-payment-due-date", soon)

    await back(wrapper)
    await back(wrapper)
    expect(wrapper.find('[data-testid="payment-emails-members-table"]').exists()).toBe(true)

    await next(wrapper)
    expect(wrapper.find('[data-testid="payment-emails-amount-2"]').text()).toContain("10.00")
    await next(wrapper)
    expect(
      wrapper.find('[data-testid="payment-emails-payment-due-date"] input').element,
    ).toHaveProperty("value", soon)
  })
})

describe("PaymentEmailWizard sending", () => {
  it("posts the ticked members, the dates they need and only what was changed", async () => {
    mockSend.mockResolvedValue({data: {remindersSent: 2, incassoNotificationsSent: 0}})
    const wrapper = await openWizard([
      apiRow({userId: 1}),
      apiRow({userId: 2, name: "Ben Debit", defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION}),
      apiRow({userId: 3, name: "Cara Dropped"}),
    ])

    await tick(wrapper, 3)
    await next(wrapper)
    await chooseKind(wrapper, 2, ContributionEmailKind.REMINDER)
    await chooseFee(wrapper, 2, BulkFeeType.ALUMNI_FEE)
    await next(wrapper)
    await typeDate(wrapper, "payment-emails-payment-due-date", soon)
    await sendFromSummary(wrapper)

    expect(mockSend).toHaveBeenCalledWith({
      body: {
        contributionPeriodId: 3,
        userIds: [1, 2],
        forciblyIncludedUserIds: [],
        kindOverrides: {"2": ContributionEmailKind.REMINDER},
        paymentDueDate: soon,
        debitDate: undefined,
        feeTypeOverrides: {"2": BulkFeeType.ALUMNI_FEE},
      },
    })
  })

  it("names the warned members ticked back in, so the api overrules its own warning", async () => {
    mockSend.mockResolvedValue({data: {remindersSent: 2, incassoNotificationsSent: 0}})
    const wrapper = await openWizard([
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

    await tick(wrapper, 2)
    await next(wrapper)
    await next(wrapper)
    await typeDate(wrapper, "payment-emails-payment-due-date", soon)
    await sendFromSummary(wrapper)

    expect(mockSend).toHaveBeenCalledWith(
      expect.objectContaining({
        body: expect.objectContaining({userIds: [1, 2], forciblyIncludedUserIds: [2]}),
      }),
    )
  })

  it("sends both dates when the batch needs both", async () => {
    mockSend.mockResolvedValue({data: {remindersSent: 1, incassoNotificationsSent: 1}})
    const wrapper = await openWizard([
      apiRow({userId: 1}),
      apiRow({userId: 2, name: "Ben Debit", defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION}),
    ])

    await next(wrapper)
    await next(wrapper)
    await typeDate(wrapper, "payment-emails-payment-due-date", soon)
    await typeDate(wrapper, "payment-emails-debit-date", alsoSoon)
    await sendFromSummary(wrapper)

    expect(mockSend).toHaveBeenCalledWith(
      expect.objectContaining({
        body: expect.objectContaining({paymentDueDate: soon, debitDate: alsoSoon}),
      }),
    )
  })

  it("opens the summary rather than sending, and counts every override in it", async () => {
    const wrapper = await openWizard([
      apiRow({userId: 1, lastRemindedOn: "2026-01-05"}),
      apiRow({
        userId: 2,
        name: "Ben Paid",
        disposition: BulkRowDisposition.WARNING,
        reason: BulkRowReason.ALREADY_PAID,
      }),
    ])

    await tick(wrapper, 2)
    await next(wrapper)
    await chooseFee(wrapper, 1, BulkFeeType.ALUMNI_FEE)
    await next(wrapper)
    await typeDate(wrapper, "payment-emails-payment-due-date", soon)
    await next(wrapper)

    expect(mockSend).not.toHaveBeenCalled()
    const overrides = wrapper.find('[data-testid="payment-emails-confirm-overrides"]')
    expect(overrides.text()).toContain("1 ticked back in despite a warning")
    expect(overrides.text()).toContain("1 charged a fee type other than the one that applies")
    expect(overrides.text()).toContain("1 already had this email for this period")
    expect(wrapper.find('[data-testid="payment-emails-confirm-summary"]').text())
      .toContain("cannot be undone")
  })

  it("backing out of the summary sends nothing and keeps the batch intact", async () => {
    const wrapper = await openWizard([apiRow({userId: 1})])
    await next(wrapper)
    await next(wrapper)
    await typeDate(wrapper, "payment-emails-payment-due-date", soon)
    await next(wrapper)

    await wrapper.find('[data-testid="payment-emails-confirm-back-btn"]').trigger("click")
    await settle()

    expect(mockSend).not.toHaveBeenCalled()
    expect(wrapper.emitted("done")).toBeUndefined()
    expect(wrapper.find('[data-testid="payment-emails-recipient-1"]').exists()).toBe(true)
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
    const wrapper = await openWizard([apiRow({userId: 1})])
    await next(wrapper)
    await next(wrapper)
    await typeDate(wrapper, "payment-emails-payment-due-date", soon)
    await sendFromSummary(wrapper)

    expect(wrapper.find('[data-testid="payment-emails-rejection"]').text())
      .toContain("Nothing was sent")
    expect(wrapper.emitted("done")).toBeUndefined()
  })

  it("says so when the selection cannot be read", async () => {
    mockPreview.mockResolvedValue({data: undefined})
    const wrapper = mount(PaymentEmailWizard, {
      global: {stubs},
      props: {modelValue: true, period, userIds: [1]},
    })
    await settle()

    expect(wrapper.find('[data-testid="payment-emails-load-error"]').exists()).toBe(true)
  })

  it("asks the api for nothing without a selection or a period", async () => {
    mount(PaymentEmailWizard, {global: {stubs}, props: {modelValue: true, period, userIds: []}})
    mount(PaymentEmailWizard, {global: {stubs}, props: {modelValue: true, period: null, userIds: [1]}})
    await settle()

    expect(mockPreview).not.toHaveBeenCalled()
  })
})
