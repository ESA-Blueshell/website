import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import FeeCycleDialog from "@/components/common/modals/bulk/FeeCycleDialog.vue"
import {
  BulkFeeType,
  BulkRowDisposition,
  BulkRowReason,
  FeeCycleGroup,
  MemberType,
  type ContributionPeriodResponse,
  type FeeCycleRowResponse,
} from "@/services/api"
import {settle} from "../../../../helpers/testUtils"

const {mockPreviewFeeCycle, mockSendFeeCycle} = vi.hoisted(() => ({
  mockPreviewFeeCycle: vi.fn(),
  mockSendFeeCycle: vi.fn(),
}))
vi.mock("@/services/api/blueshell/sdk.gen", () => ({
  previewFeeCycle: mockPreviewFeeCycle,
  sendFeeCycle: mockSendFeeCycle,
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

function apiRow(overrides: Partial<FeeCycleRowResponse> = {}): FeeCycleRowResponse {
  return {
    userId: 1,
    name: "Ann Regular",
    memberType: MemberType.REGULAR,
    memberSince: "2025-09-01",
    group: FeeCycleGroup.TRANSFER,
    disposition: BulkRowDisposition.INCLUDED,
    reason: null,
    feeType: BulkFeeType.FULL_YEAR_FEE,
    amount: 45,
    lastAskedOn: null,
    ...overrides,
  }
}

function givenCycle(rows: FeeCycleRowResponse[]) {
  mockPreviewFeeCycle.mockResolvedValue({data: {contributionPeriodId: period.id, rows}})
}

async function openDialog(rows: FeeCycleRowResponse[]) {
  givenCycle(rows)
  const wrapper = mount(FeeCycleDialog, {props: {modelValue: true, period}})
  await settle()
  return wrapper
}

describe("FeeCycleDialog", () => {
  it("reads the cycle from the api for the selected period", async () => {
    await openDialog([apiRow()])

    expect(mockPreviewFeeCycle).toHaveBeenCalledWith({query: {contributionPeriodId: 3}})
  })

  it("shows both sides of the partition and counts each", async () => {
    const wrapper = await openDialog([
      apiRow({userId: 1, group: FeeCycleGroup.TRANSFER}),
      apiRow({userId: 2, name: "Ben Debit", group: FeeCycleGroup.DIRECT_DEBIT}),
    ])

    expect(wrapper.find('[data-testid="fee-cycle-group-1"]').text()).toBe("Transfer")
    expect(wrapper.find('[data-testid="fee-cycle-group-2"]').text()).toBe("Direct debit")
    expect(wrapper.find('[data-testid="fee-cycle-count-transfer"]').text()).toContain("1 by transfer")
    expect(wrapper.find('[data-testid="fee-cycle-count-direct-debit"]').text()).toContain("1 by direct debit")
  })

  it("shows the amount the api resolved for each row", async () => {
    const wrapper = await openDialog([apiRow({userId: 1, feeType: BulkFeeType.HALF_YEAR_FEE, amount: 25})])

    expect(wrapper.find('[data-testid="fee-cycle-amount-1"]').text()).toContain("25.00")
  })

  // Their absence from the send is visible rather than silent.
  it("shows an excluded member with the reason, and offers no fee type for them", async () => {
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

    expect(wrapper.find('[data-testid="bulk-preview-disposition-4"]').text()).toContain("Excluded")
    expect(wrapper.find('[data-testid="bulk-preview-note-4"]').text()).toContain("Honorary")
    expect(wrapper.find('[data-testid="fee-cycle-feetype-4"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="fee-cycle-feetype-fixed-4"]').exists()).toBe(true)
  })

  it("says when a member was last asked, and never when they have not been", async () => {
    const wrapper = await openDialog([
      apiRow({userId: 1, lastAskedOn: "2026-03-04"}),
      apiRow({userId: 2, name: "Ben New", lastAskedOn: null}),
    ])

    expect(wrapper.find('[data-testid="fee-cycle-last-asked-1"]').text()).toBe("04/03/2026")
    expect(wrapper.find('[data-testid="fee-cycle-last-asked-2"]').text()).toBe("Never")
  })

  it("re-prices a row when its fee type changes, without asking the api again", async () => {
    const wrapper = await openDialog([apiRow({userId: 1, feeType: BulkFeeType.FULL_YEAR_FEE, amount: 45})])
    expect(wrapper.find('[data-testid="fee-cycle-amount-1"]').text()).toContain("45.00")

    wrapper.vm.feeTypeSelections[1] = BulkFeeType.ALUMNI_FEE
    await settle()

    expect(wrapper.find('[data-testid="fee-cycle-amount-1"]').text()).toContain("10.00")
    expect(mockPreviewFeeCycle).toHaveBeenCalledTimes(1)
  })

  it("sends both dates and only the fee types that were changed", async () => {
    mockSendFeeCycle.mockResolvedValue({
      data: {paymentRequestsQueued: 1, preNotificationsQueued: 1, excluded: 0},
    })
    const wrapper = await openDialog([
      apiRow({userId: 1, feeType: BulkFeeType.FULL_YEAR_FEE}),
      apiRow({userId: 2, name: "Ben Debit", group: FeeCycleGroup.DIRECT_DEBIT}),
    ])

    wrapper.vm.paymentDueDate = "2026-04-01"
    wrapper.vm.debitDate = "2026-04-15"
    wrapper.vm.feeTypeSelections[2] = BulkFeeType.ALUMNI_FEE
    await settle()
    await wrapper.find('[data-testid="bulk-action-confirm-btn"]').trigger("click")
    await settle()

    expect(mockSendFeeCycle).toHaveBeenCalledWith({
      body: {
        contributionPeriodId: 3,
        paymentDueDate: "2026-04-01",
        debitDate: "2026-04-15",
        feeTypeOverrides: {"2": BulkFeeType.ALUMNI_FEE},
      },
    })
  })

  /**
   * The generated client hands a refusal back rather than throwing, so a try/catch would
   * report a send that wrote nothing as a success.
   */
  it("reports a refused send rather than closing on it", async () => {
    mockSendFeeCycle.mockResolvedValue({
      response: {status: 409},
      error: {
        errors: [
          {
            code: "NonRecipientFeeTypeUserIds",
            field: "feeTypeOverrides",
            message: "1 of the fee types name members this cycle does not write to.",
            values: [4],
          },
        ],
      },
    })
    const wrapper = await openDialog([apiRow({userId: 1})])

    wrapper.vm.paymentDueDate = "2026-04-01"
    wrapper.vm.debitDate = "2026-04-15"
    await settle()
    await wrapper.find('[data-testid="bulk-action-confirm-btn"]').trigger("click")
    await settle()

    const refusal = wrapper.find('[data-testid="fee-cycle-rejection"]')
    expect(refusal.exists()).toBe(true)
    expect(refusal.text()).toContain("Nothing was sent")
    expect(wrapper.emitted("done")).toBeUndefined()
  })

  it("says so when the cycle cannot be read", async () => {
    mockPreviewFeeCycle.mockResolvedValue({data: undefined})
    const wrapper = mount(FeeCycleDialog, {props: {modelValue: true, period}})
    await settle()

    expect(wrapper.find('[data-testid="fee-cycle-load-error"]').exists()).toBe(true)
  })

  it("asks the api for nothing when no period is selected", async () => {
    mount(FeeCycleDialog, {props: {modelValue: true, period: null}})
    await settle()

    expect(mockPreviewFeeCycle).not.toHaveBeenCalled()
  })
})
