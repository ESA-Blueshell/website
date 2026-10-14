import {describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import OwnContributions from "@/components/account/OwnContributions.vue"
import {BulkFeeType} from "@/services/api"
import {mountInApp, sortByEveryHead} from "../../pages/helpers"

const api = vi.hoisted(() => ({findOwnContributions: vi.fn()}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const period = (periodId: number, fields: Record<string, unknown> = {}) => ({
  periodId, startDate: `202${periodId}-09-01`, endDate: `202${periodId + 1}-08-31`,
  feeType: BulkFeeType.FULL_YEAR_FEE, fee: 30, paid: false, paidAt: null, lastEmailAt: null, lastEmailKind: null, ...fields,
})

describe("the reader's own contributions", () => {
  it("lists each period with its fee and whether it is paid, and owes nothing for an honorary one", async () => {
    api.findOwnContributions.mockResolvedValue({status: 200, data: [
      period(5, {paid: true, paidAt: "2025-10-01T10:00:00Z"}),
      period(4),
      period(3, {feeType: null, fee: null}),
    ]})
    const wrapper = mountInApp(OwnContributions)
    await flushPromises()

    expect(wrapper.get('[data-testid="own-contribution-5"]').text()).toContain("Paid 1 Oct 2025")
    expect(wrapper.get('[data-testid="own-contribution-5"]').text()).toContain("€ 30.00")
    expect(wrapper.get('[data-testid="own-contribution-4"]').text()).toContain("Not paid yet")
    expect(wrapper.get('[data-testid="own-contribution-3"]').text()).toContain("Nothing to pay")
    expect(await sortByEveryHead(wrapper)).toBe(4)
  })

  it("says when there are none yet, and when they could not be read", async () => {
    api.findOwnContributions.mockResolvedValue({status: 200, data: []})
    const none = mountInApp(OwnContributions)
    await flushPromises()
    expect(none.find('[data-testid="own-contributions-none"]').exists()).toBe(true)

    api.findOwnContributions.mockResolvedValue({status: 500, error: {}})
    const unread = mountInApp(OwnContributions)
    await flushPromises()
    expect(unread.find('[data-testid="own-contributions-failed"]').exists()).toBe(true)
    expect(unread.find('[data-testid="own-contributions-none"]').exists()).toBe(false)
  })
})
