import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import OwnContributions from "@/components/account/OwnContributions.vue"
import {BulkFeeType} from "@/services/api"
import {sortByEveryHead} from "../../helpers/testUtils"

const api = vi.hoisted(() => ({findOwnContributions: vi.fn()}))
vi.mock("@/services/api", async (importOriginal) => ({...(await importOriginal<typeof import("@/services/api")>()), ...api}))
vi.mock("vue-router", async (importOriginal) => ({...(await importOriginal<typeof import("vue-router")>()), useRouter: () => ({push: vi.fn()})}))

describe("a member's own contributions", () => {
  beforeEach(() => vi.clearAllMocks())

  it("lists each period with its fee and whether it is paid", async () => {
    api.findOwnContributions.mockResolvedValue({status: 200, data: [
      {periodId: 3, startDate: "2026-09-01", endDate: "2027-08-31", feeType: BulkFeeType.FULL_YEAR_FEE, fee: 30, paid: false},
      {periodId: 2, startDate: "2025-09-01", endDate: "2026-08-31", feeType: BulkFeeType.HALF_YEAR_FEE, fee: 15, paid: true, paidAt: "2025-10-02T10:00:00Z"},
      {periodId: 1, startDate: "2024-01-01", endDate: "2024-12-31", paid: false},
    ]})
    const wrapper = mount(OwnContributions)
    expect(wrapper.find('[data-testid="own-contributions"]').exists()).toBe(false)
    await flushPromises()

    const row = (id: number) => wrapper.get(`[data-testid="own-contribution-${id}"]`).text()
    expect(row(3)).toContain("2026-2027")
    expect(row(3)).toContain("Full-year fee")
    expect(row(3)).toContain("€ 30.00")
    expect(row(3)).toContain("Not paid yet")
    expect(row(2)).toContain("Paid 2 Oct 2025")
    expect(row(1)).toContain("2024")
    expect(row(1)).toContain("Nothing to pay")
    expect(row(1)).not.toContain("Not paid yet")
    expect(await sortByEveryHead(wrapper)).toBe(4)
  })

  it("says so when there are none, or when they cannot be read", async () => {
    api.findOwnContributions.mockResolvedValue({status: 200, data: []})
    const none = mount(OwnContributions)
    await flushPromises()
    expect(none.get('[data-testid="own-contributions-none"]').text()).toContain("no contributions yet")

    api.findOwnContributions.mockResolvedValue({status: 500, error: {}})
    const unread = mount(OwnContributions)
    await flushPromises()
    expect(unread.find('[data-testid="own-contributions-none"]').exists()).toBe(true)
  })
})
