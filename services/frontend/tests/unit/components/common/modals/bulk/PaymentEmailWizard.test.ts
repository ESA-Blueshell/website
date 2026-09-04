import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
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

const {mockPreview, mockSend, mockReadEmail, mockLgAndUp} = vi.hoisted(() => ({
  mockPreview: vi.fn(),
  mockSend: vi.fn(),
  mockReadEmail: vi.fn(),
  // The wizard turns on the breakpoint, and no display is injected here. Wide by default,
  // so these cases read the table; the narrow cases say so.
  mockLgAndUp: {value: true},
}))
vi.mock("vuetify", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vuetify")>()
  const {computed} = await import("vue")
  // A real ref, so the template unwraps it: a plain {value: false} is an object, and
  // `v-if` on an object is always true, which would mean the breakpoint never turns.
  return {
    ...(actual as Record<string, unknown>),
    useDisplay: () => ({lgAndUp: computed(() => mockLgAndUp.value)}),
  }
})
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
  // The only Vuetify behaviour the wizard leans on: a dialog shows nothing while closed,
  // which is how the confirmation being dismissed is visible at all.
  VDialog: {
    props: ["modelValue"],
    template: `<div v-if="modelValue"><slot /></div>`,
  },
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
  // The narrow layout's row. Its prepend slot holds the Send-to box, and an unresolved
  // component renders no named slots at all, so the box would be missing rather than absent.
  VListItem: {
    template: `<div><slot name="prepend" /><slot /><slot name="append" /></div>`,
  },
  VCheckboxBtn: {
    props: ["modelValue"],
    emits: ["update:modelValue"],
    template: `<input type="checkbox" :checked="modelValue"
      @change="$emit('update:modelValue', !modelValue)">`,
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

async function openWizard(
  rows: BulkContributionEmailRowResponse[],
  unknownUserIds: number[] = [],
): Promise<Wizard> {
  mockPreview.mockResolvedValue({data: {contributionPeriodId: period.id, rows, unknownUserIds}})
  const wrapper = mount(PaymentEmailWizard, {
    global: {stubs},
    props: {
      modelValue: true,
      period,
      userIds: [...rows.map((row) => row.userId), ...unknownUserIds],
    },
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

  it("counts an id that is no longer a user rather than losing it", async () => {
    const wrapper = await openWizard([apiRow({userId: 1})], [77])

    expect(wrapper.find('[data-testid="payment-emails-count-unknown"]').text())
      .toContain("1 no longer exists")
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

  // The step the include-or-not decision is made on, so the date has to be readable here.
  it("says when each member was last told about money", async () => {
    const wrapper = await openWizard([
      apiRow({userId: 1, name: "Ann Asked", lastRemindedOn: "2026-01-05"}),
      apiRow({userId: 2, name: "Ben Debit", lastNotifiedOn: "2026-02-09"}),
      apiRow({userId: 3, name: "Cara New"}),
    ])

    expect(wrapper.find('[data-testid="payment-emails-last-ask-1"]').text()).toBe("05/01/2026")
    expect(wrapper.find('[data-testid="payment-emails-last-ask-2"]').text()).toBe("09/02/2026")
    expect(wrapper.find('[data-testid="payment-emails-last-ask-3"]').text()).toBe("—")
  })

  it("sorts the members nobody has asked to the top", async () => {
    const wrapper = await openWizard([
      apiRow({userId: 1, name: "Ann Asked", lastRemindedOn: "2026-02-09"}),
      apiRow({userId: 2, name: "Ben Early", lastRemindedOn: "2026-01-05"}),
      apiRow({userId: 3, name: "Cara New"}),
    ])

    await wrapper.findAll("thead th")[4]!.trigger("click")

    const order = wrapper
      .findAll('[data-testid^="payment-emails-row-"]')
      .map((row) => row.attributes("data-testid"))
    expect(order).toEqual([
      "payment-emails-row-3",
      "payment-emails-row-2",
      "payment-emails-row-1",
    ])
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

  // A member moved onto direct debit has been asked by transfer, never pre-notified. Reading
  // the chosen email's own history called that member untouched.
  it("keeps last payment email when the row switches email", async () => {
    const wrapper = await openWizard([
      apiRow({
        userId: 2,
        name: "Ben Moved",
        defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION,
        lastRemindedOn: "2026-03-04",
      }),
    ])
    await next(wrapper)
    expect(wrapper.find('[data-testid="payment-emails-last-ask-2"]').text()).toBe("04/03/2026")

    await chooseKind(wrapper, 2, ContributionEmailKind.REMINDER)

    expect(wrapper.find('[data-testid="payment-emails-last-ask-2"]').text()).toBe("04/03/2026")
  })

  it("shows the later of the two asks, and a dash for a member nobody asked", async () => {
    const wrapper = await openWizard([
      apiRow({userId: 2, name: "Ben Both", lastRemindedOn: "2026-03-04", lastNotifiedOn: "2026-05-01"}),
      apiRow({userId: 3, name: "Cara New"}),
    ])
    await next(wrapper)

    expect(wrapper.find('[data-testid="payment-emails-last-ask-2"]').text()).toBe("01/05/2026")
    expect(wrapper.find('[data-testid="payment-emails-last-ask-3"]').text()).toBe("—")
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
      .toContain("Nobody here is on direct debit")
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
    expect(overrides.text()).toContain("1 member is included despite a warning")
    expect(overrides.text()).toContain("1 member is charged a fee that does not apply to them")
    expect(overrides.text()).toContain("1 member has had this same email for this period before")
    expect(wrapper.find('[data-testid="payment-emails-confirm-summary"]').text())
      .toContain("cannot be undone")
  })

  it("lists an email already sent without calling it an override", async () => {
    const wrapper = await openWizard([apiRow({userId: 1, lastRemindedOn: "2026-01-05"})])

    await next(wrapper)
    await next(wrapper)
    await typeDate(wrapper, "payment-emails-payment-due-date", soon)
    await next(wrapper)

    const overrides = wrapper.find('[data-testid="payment-emails-confirm-overrides"]')
    expect(overrides.text()).toContain("1 member has had this same email for this period before")
    expect(wrapper.find('[data-testid="payment-emails-confirm-forced"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="payment-emails-confirm-switched"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="payment-emails-confirm-recharged"]').exists()).toBe(false)
  })

  it("does not call the other email an already sent one", async () => {
    const wrapper = await openWizard([apiRow({userId: 1, lastNotifiedOn: "2026-01-05"})])

    await next(wrapper)
    await next(wrapper)
    await typeDate(wrapper, "payment-emails-payment-due-date", soon)
    await next(wrapper)

    expect(wrapper.find('[data-testid="payment-emails-confirm-overrides"]').exists()).toBe(false)
  })

  it("does not block on a date the request leaves out", async () => {
    mockSend.mockResolvedValue({data: {remindersSent: 1, incassoNotificationsSent: 0}})
    const wrapper = await openWizard([apiRow({userId: 1})])

    await next(wrapper)
    await next(wrapper)
    await typeDate(wrapper, "payment-emails-payment-due-date", soon)
    // Nobody is on direct debit, so this one is stripped from the request.
    await typeDate(wrapper, "payment-emails-debit-date", "1999-01-01")
    await sendFromSummary(wrapper)

    expect(wrapper.find('[data-testid="payment-emails-debit-date"] .field-error').text()).toBe("")
    expect(mockSend).toHaveBeenCalledWith(
      expect.objectContaining({body: expect.objectContaining({debitDate: undefined})}),
    )
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

/**
 * The api refuses whole, naming the request field at fault. The wizard's job is to put the
 * treasurer back where that field is edited, with the rows or the input it named marked.
 */
describe("PaymentEmailWizard a refused send", () => {
  function refusal(status: number, errors: unknown[]) {
    return {response: {status}, error: {errors}}
  }

  async function sendTwoMembers(wrapper: Wizard) {
    await next(wrapper)
    await next(wrapper)
    await typeDate(wrapper, "payment-emails-payment-due-date", soon)
    await sendFromSummary(wrapper)
  }

  const twoMembers = () => [apiRow({userId: 1}), apiRow({userId: 2, name: "Ben Gone"})]

  it("lands on the members step with the rows the api named marked", async () => {
    mockSend.mockResolvedValue(refusal(409, [{
      code: "UnknownUserIds",
      field: "userIds",
      message: "1 of the selected users no longer exist.",
      values: [2],
    }]))
    const wrapper = await openWizard(twoMembers())

    await sendTwoMembers(wrapper)

    expect(wrapper.find('[data-testid="payment-emails-members-table"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="payment-emails-refusal-2"]').text())
      .toContain("no longer exist")
    expect(wrapper.find('[data-testid="payment-emails-refusal-1"]').exists()).toBe(false)
  })

  it("lands on the fees step when the refusal is about an override", async () => {
    mockSend.mockResolvedValue(refusal(409, [{
      code: "NonRecipientFeeTypeUserIds",
      field: "feeTypeOverrides",
      message: "1 of the fee types name members this send does not write to.",
      values: [2],
    }]))
    const wrapper = await openWizard(twoMembers())

    await sendTwoMembers(wrapper)

    expect(wrapper.find('[data-testid="payment-emails-fees-table"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="payment-emails-members-table"]').exists()).toBe(false)
  })

  it("lands on the last step with the date the api refused flagged", async () => {
    mockSend.mockResolvedValue(refusal(400, [{
      code: "DateOutsideContributionPeriod",
      field: "paymentDueDate",
      message: "A date must fall within the contribution period, or shortly after it ends.",
    }]))
    const wrapper = await openWizard([apiRow({userId: 1})])

    await sendTwoMembers(wrapper)

    expect(wrapper.find('[data-testid="payment-emails-recipient-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="payment-emails-payment-due-date"] .field-error').text())
      .toContain("must fall within the contribution period")
  })

  it("clears a refused date once that date is changed", async () => {
    mockSend.mockResolvedValue(refusal(400, [{
      code: "DateRequired",
      field: "paymentDueDate",
      message: "A date is required: somebody in this batch gets an email that states one.",
    }]))
    const wrapper = await openWizard([apiRow({userId: 1})])
    await sendTwoMembers(wrapper)

    await typeDate(wrapper, "payment-emails-payment-due-date", alsoSoon)

    expect(wrapper.find('[data-testid="payment-emails-payment-due-date"] .field-error').text())
      .toBe("")
  })

  it("goes back to the earliest step a refusal names, not the last", async () => {
    mockSend.mockResolvedValue(refusal(409, [
      {
        code: "NonRecipientFeeTypeUserIds",
        field: "feeTypeOverrides",
        message: "1 of the fee types name members this send does not write to.",
        values: [2],
      },
      {code: "DuplicateUserIds", field: "userIds", message: "", values: [1]},
    ]))
    const wrapper = await openWizard(twoMembers())

    await sendTwoMembers(wrapper)

    expect(wrapper.find('[data-testid="payment-emails-members-table"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="payment-emails-refusal-1"]').text())
      .toBe("The selection names the same member more than once.")
  })

  it("closes the confirmation and reports that nothing was sent", async () => {
    mockSend.mockResolvedValue(refusal(409, [
      {code: "DuplicateUserIds", field: "userIds", message: "", values: [1]},
    ]))
    const wrapper = await openWizard(twoMembers())

    await sendTwoMembers(wrapper)

    expect(wrapper.find('[data-testid="payment-emails-confirm-summary"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="payment-emails-rejection"]').text())
      .toContain("Nothing was sent")
    expect(wrapper.emitted("done")).toBeUndefined()
  })

  it("keeps the choices a conflict did not contradict, and drops the ones it did", async () => {
    mockSend.mockResolvedValue(refusal(409, [{
      code: "UnknownUserIds",
      field: "userIds",
      message: "1 of the selected users no longer exist.",
      values: [2],
    }]))
    const wrapper = await openWizard([
      apiRow({userId: 1}),
      apiRow({userId: 2, name: "Ben Gone"}),
      apiRow({
        userId: 3,
        name: "Cara Paid",
        disposition: BulkRowDisposition.WARNING,
        reason: BulkRowReason.ALREADY_PAID,
      }),
    ])
    await tick(wrapper, 3)
    await next(wrapper)
    await chooseFee(wrapper, 1, BulkFeeType.ALUMNI_FEE)
    await chooseFee(wrapper, 2, BulkFeeType.ALUMNI_FEE)
    await next(wrapper)
    await typeDate(wrapper, "payment-emails-payment-due-date", soon)
    await sendFromSummary(wrapper)

    mockSend.mockResolvedValue({data: {remindersSent: 3, incassoNotificationsSent: 0}})
    await next(wrapper)
    await next(wrapper)
    await sendFromSummary(wrapper)

    expect(mockSend).toHaveBeenLastCalledWith(
      expect.objectContaining({
        body: expect.objectContaining({
          userIds: [1, 2, 3],
          forciblyIncludedUserIds: [3],
          // Ann and Cara keep what was chosen; Ben was named by the refusal and does not.
          feeTypeOverrides: {"1": BulkFeeType.ALUMNI_FEE},
        }),
      }),
    )
  })

  // A conflict means the plan the send read is not the one the table shows.
  it("re-reads the plan on a conflict, and leaves it alone on a bad field", async () => {
    mockSend.mockResolvedValue(refusal(409, [
      {code: "DuplicateUserIds", field: "userIds", message: "", values: [1]},
    ]))
    const conflicted = await openWizard(twoMembers())
    await sendTwoMembers(conflicted)
    expect(mockPreview).toHaveBeenCalledTimes(2)

    mockPreview.mockClear()
    mockSend.mockResolvedValue(refusal(400, [
      {code: "DateRequired", field: "debitDate", message: ""},
    ]))
    const refused = await openWizard([apiRow({userId: 1})])
    await sendTwoMembers(refused)

    expect(mockPreview).toHaveBeenCalledTimes(1)
  })
})

describe("PaymentEmailWizard below the lg breakpoint", () => {
  // The table's columns do not fit a phone, and a clipped reason is the one a treasurer
  // needs most, so the narrow layout carries every fact the columns carry.
  beforeEach(() => {
    mockLgAndUp.value = false
  })
  afterEach(() => {
    mockLgAndUp.value = true
  })

  it("reads the members as a list rather than a table", async () => {
    const wrapper = await openWizard([
      apiRow({userId: 1, name: "Ann Asked", lastRemindedOn: "2026-01-05"}),
      apiRow({
        userId: 3,
        name: "Cara Honorary",
        disposition: BulkRowDisposition.EXCLUDED,
        reason: BulkRowReason.HONORARY,
      }),
    ])

    expect(wrapper.find('[data-testid="payment-emails-members-list"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="payment-emails-members-table"]').exists()).toBe(false)

    expect(wrapper.find('[data-testid="payment-emails-last-ask-1"]').text()).toBe("05/01/2026")
    expect(wrapper.find('[data-testid="payment-emails-reason-3"]').text())
      .toContain("Owes no contribution")
  })

  it("offers no send-to box to a member it cannot reach", async () => {
    const wrapper = await openWizard([
      apiRow({userId: 1}),
      apiRow({
        userId: 3,
        disposition: BulkRowDisposition.EXCLUDED,
        reason: BulkRowReason.NO_EMAIL,
      }),
    ])

    expect(wrapper.find('[data-testid="payment-emails-send-to-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="payment-emails-send-to-3"]').exists()).toBe(false)
  })

  // An absent icon says nothing, so the narrow layout states the mandate either way.
  it("says whether each member pays by direct debit, in words", async () => {
    const wrapper = await openWizard([
      apiRow({userId: 1, defaultKind: ContributionEmailKind.REMINDER}),
      apiRow({userId: 2, defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION}),
    ])
    await next(wrapper)

    expect(wrapper.find('[data-testid="payment-emails-mandate-1"]').text())
      .toBe("No direct-debit mandate")
    expect(wrapper.find('[data-testid="payment-emails-mandate-2"]').text())
      .toBe("Pays by direct debit")
  })

  it("carries the fee and email choices into the narrow second step", async () => {
    const wrapper = await openWizard([apiRow({userId: 1, feeType: BulkFeeType.FULL_YEAR_FEE})])
    await next(wrapper)

    expect(wrapper.find('[data-testid="payment-emails-fees-list"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="payment-emails-fees-table"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="payment-emails-amount-1"]').text()).toContain("45.00")
  })
})
