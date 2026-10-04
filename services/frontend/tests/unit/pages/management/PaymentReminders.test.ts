import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import PaymentReminders from "@/pages/management/PaymentReminders.vue"
import {BulkFeeType, BulkRowDisposition, ContributionEmailKind, MemberType} from "@/services/api"
import {aContributionPeriod} from "../../helpers/apiFixtures"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  findContributionPeriods: vi.fn(),
  previewBulkContributionEmail: vi.fn(),
  readContributionEmail: vi.fn(),
  sendPaymentEmails: vi.fn(),
}))
const {mockRoute, mockHandleNetworkError} = vi.hoisted(() => ({
  mockRoute: {params: {periodId: "2"}, query: {ids: "1,2,3"} as Record<string, string>},
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => mockRoute,
}))

vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockHandleNetworkError}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const row = (userId: number, fields: Record<string, unknown> = {}) => ({
  userId, name: `Member ${userId}`, memberType: MemberType.REGULAR, memberSince: "2020-01-01",
  disposition: BulkRowDisposition.INCLUDED, reason: null, defaultKind: ContributionEmailKind.REMINDER,
  feeType: BulkFeeType.FULL_YEAR_FEE, amount: 30, lastRemindedOn: null, lastNotifiedOn: null, ...fields,
})

const later = new Date(Date.now() + 10 * 86_400_000).toISOString().slice(0, 10)

describe("the payment reminders task", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(PaymentReminders)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.query = {ids: "1,2,3"}
    api.findContributionPeriods.mockResolvedValue({status: 200, data: [aContributionPeriod({id: 2, startDate: "2025-09-01", fullYearFee: 30, halfYearFee: 15, alumniFee: 5})]})
    api.previewBulkContributionEmail.mockResolvedValue({status: 200, data: {contributionPeriodId: 2, unknownUserIds: [], rows: [
      row(1, {lastRemindedOn: "2025-10-01", disposition: BulkRowDisposition.WARNING}),
      row(2, {defaultKind: ContributionEmailKind.INCASSO_NOTIFICATION}),
      row(3, {memberSince: "2025-10-01"}),
    ]}})
    api.readContributionEmail.mockResolvedValue({status: 200, data: {subject: "Please pay", html: "<p>pay</p>", recipientEmail: "a@x", recipientName: "A", kind: "REMINDER", feeType: "FULL_YEAR_FEE"}})
    api.sendPaymentEmails.mockResolvedValue({status: 200, data: {remindersSent: 2, incassoNotificationsSent: 0, notWrittenTo: 1}})
  })

  afterEach(() => {
    unmountAll(wrappers, "PaymentReminders")
  })

  it("walks who, fees and the check to a send, and nothing goes before it", async () => {
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="payment-reminders-left-out-2"]').text()).toContain("Pays by incasso")
    expect(wrapper.get('[data-testid="payment-reminders-before-1"]').text()).toContain("Reminded before on 01/10/2025")
    expect(wrapper.get('[data-testid="payment-reminders-row-3"]').text()).toContain("First payment email")

    await wrapper.get('[data-testid="payment-reminders-next"]').trigger("click")
    await settle()
    wrapper.findComponent({name: "SearchPicker"}).vm.$emit("pick", BulkFeeType.ALUMNI_FEE)
    await settle()
    expect(wrapper.get('[data-testid="payment-reminders-fees"]').text()).toContain("€ 5.00")

    await wrapper.get('[data-testid="payment-reminders-next"]').trigger("click")
    await settle()
    expect(wrapper.get('[data-testid="payment-reminders-send"]').attributes("disabled")).toBeDefined()
    wrapper.findComponent({name: "DateInput"}).vm.$emit("update:modelValue", later)
    await settle()

    await wrapper.get('[data-testid="payment-reminders-preview-1"]').trigger("click")
    await settle()
    expect(api.readContributionEmail).toHaveBeenCalledWith({query: {
      kind: ContributionEmailKind.REMINDER, contributionPeriodId: 2, userId: 1, date: later, feeType: BulkFeeType.ALUMNI_FEE,
    }})
    expect(api.sendPaymentEmails).not.toHaveBeenCalled()
    wrapper.findComponent({name: "EmailPreviewDialog"}).vm.$emit("update:modelValue", false)

    await wrapper.get('[data-testid="payment-reminders-send"]').trigger("click")
    await settle()
    expect(api.sendPaymentEmails).toHaveBeenCalledWith({body: {
      contributionPeriodId: 2, userIds: [1, 3], forciblyIncludedUserIds: [1], kindOverrides: {},
      paymentDueDate: later, feeTypeOverrides: {1: BulkFeeType.ALUMNI_FEE},
    }})
    expect(wrapper.get('[data-testid="payment-reminders-sent"]').text()).toContain("2 reminders sent")
  })

  it("goes back a step, drops who is unticked, and says why a send was refused", async () => {
    api.sendPaymentEmails.mockResolvedValue({status: 400, error: {detail: "A payment due date must be after today."}})
    api.readContributionEmail.mockResolvedValue({status: 500, error: {}})
    const wrapper = await mount()

    await wrapper.get('[data-testid="payment-reminders-tick-3"]').setValue(false)
    await wrapper.get('[data-testid="payment-reminders-next"]').trigger("click")
    await wrapper.get('[data-testid="payment-reminders-previous"]').trigger("click")
    await wrapper.get('[data-testid="payment-reminders-next"]').trigger("click")
    await wrapper.get('[data-testid="payment-reminders-next"]').trigger("click")
    await settle()
    wrapper.findComponent({name: "DateInput"}).vm.$emit("update:modelValue", later)
    await settle()
    await wrapper.get('[data-testid="payment-reminders-preview-1"]').trigger("click")
    await settle()
    await wrapper.get('[data-testid="payment-reminders-send"]').trigger("click")
    await settle()

    expect(api.sendPaymentEmails).toHaveBeenCalledWith({body: expect.objectContaining({userIds: [1], feeTypeOverrides: {}})})
    expect(wrapper.get('[data-testid="payment-reminders-failure"]').text()).toBe("A payment due date must be after today.")
  })

  it("says so when nobody is selected, and when the selection could not be read", async () => {
    mockRoute.query = {}
    expect((await mount()).find('[data-testid="payment-reminders-empty"]').exists()).toBe(true)

    mockRoute.query = {ids: "1"}
    api.findContributionPeriods.mockRejectedValue(new Error("offline"))
    await mount()
    expect(mockHandleNetworkError).toHaveBeenCalled()
  })
})
