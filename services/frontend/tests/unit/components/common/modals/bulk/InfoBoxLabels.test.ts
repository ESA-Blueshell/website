import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import ReminderDialog from "@/components/common/modals/bulk/ReminderDialog.vue"
import type {BulkTarget} from "@/utils/bulkTarget"
import {MemberType, type ContributionPeriodResponse} from "@/services/api"

// Mock the API calls the dialog fires on open (reminder lookup for the
// "Last reminded at" column); the labels under test render synchronously.
const {mockFindContributionReminders} = vi.hoisted(() => ({
  mockFindContributionReminders: vi.fn(),
}))
vi.mock("@/services/api/blueshell/sdk.gen", () => ({
  executeBulkReminder: vi.fn(),
  findContributionReminders: mockFindContributionReminders,
  previewReminder: vi.fn(),
}))

beforeEach(() => {
  mockFindContributionReminders.mockResolvedValue({data: []})
})

const SERVER_TODAY = "2026-07-01"

function period(): ContributionPeriodResponse {
  return {
    id: 1,
    startDate: "2026-01-09",
    endDate: "2026-12-23",
    fullYearFee: 45,
    halfYearFee: 25,
    alumniFee: 10,
  } as ContributionPeriodResponse
}

const target = {
  userId: 1,
  fullName: "Test User",
  email: "t@example.com",
  memberSince: "2024-01-01",
  highestRole: null,
  mostRecentMembership: {type: MemberType.REGULAR, startDate: "2024-01-01", endDate: null, incasso: false},
  mostRecentContribution: {paid: false},
} as unknown as BulkTarget

describe("bulk dialog info-box labels", () => {
  it("renders the Contribution period box (via the conditional #info-box slot) and the Summary box", () => {
    const wrapper = mount(ReminderDialog, {
      props: {
        modelValue: true,
        targets: [target],
        period: period(),
        serverToday: SERVER_TODAY,
        latestPeriod: period(),
      },
    })
    const text = wrapper.text()
    expect(wrapper.find('[data-testid="bulk-period-info"]').exists()).toBe(true)
    expect(text).toContain("Contribution period")
    expect(text).toContain("Summary")
  })
})
