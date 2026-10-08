import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import PendingMembership from "@/components/account/PendingMembership.vue"
import {BulkFeeType} from "@/services/api"
import {settle} from "../../helpers/testUtils"

const api = vi.hoisted(() => ({findOwnFirstContribution: vi.fn()}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

describe("a pending membership on the account page", () => {
  beforeEach(() => vi.clearAllMocks())

  it("names the first contribution that makes it active", async () => {
    api.findOwnFirstContribution.mockResolvedValue({status: 200, data: {
      membershipStartDate: "2026-09-10", periodId: 4, periodStartDate: "2026-09-01", periodEndDate: "2027-08-31", feeType: BulkFeeType.FULL_YEAR_FEE, amount: 30,
    }})
    const wrapper = mount(PendingMembership)
    await vi.waitFor(() => expect(wrapper.get('[data-testid="pending-membership"]').text()).toContain("€ 30.00, the full-year fee for 2026-2027"))
  })

  it("says it is pending without a fee before any period exists, and nothing for a member", async () => {
    api.findOwnFirstContribution.mockResolvedValue({status: 200, data: {membershipStartDate: "2026-09-10"}})
    const pending = mount(PendingMembership)
    await vi.waitFor(() => expect(pending.get('[data-testid="pending-membership"]').text()).toContain("first contribution is paid."))

    api.findOwnFirstContribution.mockResolvedValue({status: 204, data: undefined})
    const member = mount(PendingMembership)
    await vi.waitFor(() => expect(api.findOwnFirstContribution).toHaveBeenCalledTimes(2))
    await settle()
    expect(member.find('[data-testid="pending-membership"]').exists()).toBe(false)
  })
})
