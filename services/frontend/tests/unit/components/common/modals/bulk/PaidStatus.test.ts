import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import PaidStatusDialog from "@/components/common/modals/bulk/PaidStatusDialog.vue"
import {MemberType} from "@/services/api"
import {settle} from "../../../../helpers/testUtils"
import {
  alreadyPaidTarget,
  honoraryTarget,
  target,
} from "../../../../helpers/bulkFixtures"

// Mock the API calls
const {mockMarkPaid, mockMarkUnpaid} = vi.hoisted(() => ({
  mockMarkPaid: vi.fn(),
  mockMarkUnpaid: vi.fn(),
}))
vi.mock("@/services/api/blueshell/sdk.gen", () => ({
  markPaid: mockMarkPaid,
  markUnpaid: mockMarkUnpaid,
}))

/** Unpaid regular member (incasso: true mirrors the original local fixture default). */
function unpaidRegularTarget(userId: number) {
  return target(userId, {
    mostRecentMembership: {
      type: MemberType.REGULAR,
      startDate: "2024-01-01",
      endDate: null,
      incasso: true,
    },
  })
}

describe("PaidStatusDialog (Mark as paid)", () => {
  it("renders the dialog with paid title and confirm button", () => {
    const wrapper = mount(PaidStatusDialog, {
      props: {
        modelValue: true,
        targetState: "paid",
        targets: [unpaidRegularTarget(1)],
        contributionPeriodId: 1,
      },
    })

    expect(wrapper.find('[data-testid="bulk-action-dialog"]').exists()).toBe(true)
    expect(wrapper.text()).toContain("Mark as paid")
  })

  it("shows preview table with single unpaid member marked INCLUDED", async () => {
    const wrapper = mount(PaidStatusDialog, {
      props: {
        modelValue: true,
        targetState: "paid",
        targets: [unpaidRegularTarget(1)],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-1"]')
    expect(dispositionChip.exists()).toBe(true)
    expect(dispositionChip.text()).toContain("Included")
  })

  it("marks already-paid member as SKIPPED with ALREADY_PAID note", async () => {
    const wrapper = mount(PaidStatusDialog, {
      props: {
        modelValue: true,
        targetState: "paid",
        targets: [alreadyPaidTarget(2)],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-2"]')
    expect(dispositionChip.text()).toContain("Skipped")

    const noteCell = wrapper.find('[data-testid="bulk-preview-note-2"]')
    expect(noteCell.text()).toContain("Already paid")
  })

  it("marks honorary member as SKIPPED with HONORARY note", async () => {
    const wrapper = mount(PaidStatusDialog, {
      props: {
        modelValue: true,
        targetState: "paid",
        targets: [honoraryTarget(3)],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-3"]')
    expect(dispositionChip.text()).toContain("Skipped")

    const noteCell = wrapper.find('[data-testid="bulk-preview-note-3"]')
    expect(noteCell.text()).toContain("Honorary")
  })

  it("displays member type and member-since date in preview", async () => {
    const wrapper = mount(PaidStatusDialog, {
      props: {
        modelValue: true,
        targetState: "paid",
        targets: [
          target(1, {
            mostRecentMembership: {
              type: MemberType.ALUMNI,
              startDate: "2024-09-15",
              endDate: null,
              incasso: true,
            },
          }),
        ],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const typeCell = wrapper.text()
    expect(typeCell).toContain("Alumni")

    const memberSinceCell = wrapper.find('[data-testid="bulk-preview-member-since-1"]')
    expect(memberSinceCell.text()).toContain("15/09/2024")
  })

  it("shows counts summary with included and skipped", async () => {
    const wrapper = mount(PaidStatusDialog, {
      props: {
        modelValue: true,
        targetState: "paid",
        targets: [
          unpaidRegularTarget(1),
          alreadyPaidTarget(2),
          honoraryTarget(3),
        ],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const countsText = wrapper.find('[data-testid="bulk-action-counts"]').text()
    expect(countsText).toContain("3 selected")
    expect(countsText).toContain("1 will apply")
    expect(countsText).toContain("2 skipped")
  })

  it("closes dialog when modelValue becomes false", async () => {
    const wrapper = mount(PaidStatusDialog, {
      props: {
        modelValue: true,
        targetState: "paid",
        targets: [unpaidRegularTarget(1)],
        contributionPeriodId: 1,
      },
    })

    expect(wrapper.find('[data-testid="bulk-preview-row-1"]').exists()).toBe(true)

    await wrapper.setProps({modelValue: false})
    await settle()

    expect(wrapper.find('[data-testid="bulk-preview-row-1"]').exists()).toBe(false)
  })

  it("updates preview rows when targets prop changes", async () => {
    const wrapper = mount(PaidStatusDialog, {
      props: {
        modelValue: true,
        targetState: "paid",
        targets: [unpaidRegularTarget(1)],
        contributionPeriodId: 1,
      },
    })

    await settle()

    await wrapper.setProps({
      targets: [unpaidRegularTarget(1), alreadyPaidTarget(2)],
    })

    await settle()

    expect(wrapper.find('[data-testid="bulk-preview-row-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="bulk-preview-row-2"]').exists()).toBe(true)
  })

  it("handles edge case: empty targets list", async () => {
    const wrapper = mount(PaidStatusDialog, {
      props: {
        modelValue: true,
        targetState: "paid",
        targets: [],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const countsText = wrapper.find('[data-testid="bulk-action-counts"]').text()
    expect(countsText).toContain("0 selected")
  })

  it("handles edge case: user with no membership", async () => {
    const wrapper = mount(PaidStatusDialog, {
      props: {
        modelValue: true,
        targetState: "paid",
        targets: [
          target(1, {
            mostRecentMembership: null,
          }),
        ],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const memberSinceCell = wrapper.find('[data-testid="bulk-preview-member-since-1"]')
    expect(memberSinceCell.text()).toBe("—")
  })
})

describe("PaidStatusDialog (Mark as unpaid)", () => {
  it("renders the dialog with unpaid title and confirm button", () => {
    const wrapper = mount(PaidStatusDialog, {
      props: {
        modelValue: true,
        targetState: "unpaid",
        targets: [alreadyPaidTarget(1)],
        contributionPeriodId: 1,
      },
    })

    expect(wrapper.find('[data-testid="bulk-action-dialog"]').exists()).toBe(true)
    expect(wrapper.text()).toContain("Mark as unpaid")
  })

  it("marks paid member as INCLUDED", async () => {
    const wrapper = mount(PaidStatusDialog, {
      props: {
        modelValue: true,
        targetState: "unpaid",
        targets: [alreadyPaidTarget(1)],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-1"]')
    expect(dispositionChip.text()).toContain("Included")
  })

  it("marks unpaid member as SKIPPED with NOT_PAID note", async () => {
    const wrapper = mount(PaidStatusDialog, {
      props: {
        modelValue: true,
        targetState: "unpaid",
        targets: [unpaidRegularTarget(2)],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-2"]')
    expect(dispositionChip.text()).toContain("Skipped")

    const noteCell = wrapper.find('[data-testid="bulk-preview-note-2"]')
    expect(noteCell.text()).toContain("Not paid")
  })

  it("marks honorary member as SKIPPED with HONORARY note", async () => {
    const wrapper = mount(PaidStatusDialog, {
      props: {
        modelValue: true,
        targetState: "unpaid",
        targets: [honoraryTarget(3)],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const dispositionChip = wrapper.find('[data-testid="bulk-preview-disposition-3"]')
    expect(dispositionChip.text()).toContain("Skipped")

    const noteCell = wrapper.find('[data-testid="bulk-preview-note-3"]')
    expect(noteCell.text()).toContain("Honorary")
  })

  it("displays counts summary with paid and unpaid", async () => {
    const wrapper = mount(PaidStatusDialog, {
      props: {
        modelValue: true,
        targetState: "unpaid",
        targets: [
          alreadyPaidTarget(1),
          unpaidRegularTarget(2),
          honoraryTarget(3),
        ],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const countsText = wrapper.find('[data-testid="bulk-action-counts"]').text()
    expect(countsText).toContain("3 selected")
    expect(countsText).toContain("1 will apply")
    expect(countsText).toContain("2 skipped")
  })

  it("populates member type and member-since", async () => {
    const wrapper = mount(PaidStatusDialog, {
      props: {
        modelValue: true,
        targetState: "unpaid",
        targets: [
          target(1, {
            mostRecentMembership: {
              type: MemberType.ALUMNI,
              startDate: "2023-06-01",
              endDate: null,
              incasso: false,
            },
            mostRecentContribution: {paid: true},
          }),
        ],
        contributionPeriodId: 1,
      },
    })

    await settle()

    const typeText = wrapper.text()
    expect(typeText).toContain("Alumni")

    const memberSinceCell = wrapper.find('[data-testid="bulk-preview-member-since-1"]')
    expect(memberSinceCell.text()).toContain("01/06/2023")
  })

  it("handles mixed roster: paid, unpaid, honorary", async () => {
    const wrapper = mount(PaidStatusDialog, {
      props: {
        modelValue: true,
        targetState: "unpaid",
        targets: [
          alreadyPaidTarget(1),
          unpaidRegularTarget(2),
          honoraryTarget(3),
        ],
        contributionPeriodId: 1,
      },
    })

    await settle()

    expect(wrapper.find('[data-testid="bulk-preview-disposition-1"]').text()).toContain("Included")
    expect(wrapper.find('[data-testid="bulk-preview-disposition-2"]').text()).toContain("Skipped")
    expect(wrapper.find('[data-testid="bulk-preview-disposition-3"]').text()).toContain("Skipped")
  })
})

describe("PaidStatusDialog (a refused selection)", () => {
  const refusal = (errors: unknown[]) => ({
    data: undefined,
    response: {status: 409},
    error: {status: 409, detail: "The selection no longer matches the current data.", errors},
  })

  async function confirmWith(response: unknown) {
    mockMarkPaid.mockResolvedValue(response)
    const wrapper = mount(PaidStatusDialog, {
      props: {
        modelValue: true,
        targetState: "paid",
        targets: [unpaidRegularTarget(1)],
        contributionPeriodId: 1,
      },
    })
    await settle()
    await wrapper.find('[data-testid="bulk-action-confirm-btn"]').trigger("click")
    await settle()
    return wrapper
  }

  it("shows the reason and states that nothing was changed", async () => {
    const wrapper = await confirmWith(
      refusal([
        {
          field: "userIds",
          code: "DeletedUserIds",
          message: "1 of the selected users have been deleted.",
          values: [1],
        },
      ]),
    )

    const alert = wrapper.find('[data-testid="bulk-paid-rejection"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain("Nothing was changed")
    expect(alert.text()).toContain("have been deleted")
  })

  it("names the refused rows rather than only their ids", async () => {
    const wrapper = await confirmWith(
      refusal([{field: "userIds", code: "HonoraryUserIds", message: "Honorary.", values: [1]}]),
    )

    const text = wrapper.find('[data-testid="bulk-paid-rejection"]').text()
    expect(text).toContain("User 1")
    expect(text).not.toContain("#1")
  })

  it("asks the page to reload when the table is out of date", async () => {
    const wrapper = await confirmWith(
      refusal([{field: "userIds", code: "UnknownUserIds", message: "Gone.", values: [1]}]),
    )

    expect(wrapper.emitted("stale")).toHaveLength(1)
  })

  it("does not ask for a reload when only the choice was wrong", async () => {
    const wrapper = await confirmWith(
      refusal([{field: "userIds", code: "HonoraryUserIds", message: "Honorary.", values: [1]}]),
    )

    expect(wrapper.emitted("stale")).toBeUndefined()
  })

  it("stays open and does not report success", async () => {
    const wrapper = await confirmWith(
      refusal([{field: "userIds", code: "DeletedUserIds", message: "Gone.", values: [1]}]),
    )

    expect(wrapper.emitted("done")).toBeUndefined()
    expect(wrapper.emitted("update:modelValue")).toBeUndefined()
  })

  it("clears the reason when the dialog is reopened", async () => {
    const wrapper = await confirmWith(
      refusal([{field: "userIds", code: "DeletedUserIds", message: "Gone.", values: [1]}]),
    )
    expect(wrapper.find('[data-testid="bulk-paid-rejection"]').exists()).toBe(true)

    await wrapper.setProps({modelValue: false})
    await settle()
    await wrapper.setProps({modelValue: true})
    await settle()

    expect(wrapper.find('[data-testid="bulk-paid-rejection"]').exists()).toBe(false)
  })
})
