import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import CurrentMembershipType from "@/components/account/CurrentMembershipType.vue"
import {MemberType} from "@/services/api"
import {aMembership} from "../../helpers/apiFixtures"
import {settle} from "../../helpers/testUtils"

const api = vi.hoisted(() => ({findOwnMemberships: vi.fn()}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

describe("the reader's membership type on the account page", () => {
  beforeEach(() => vi.clearAllMocks())

  it("names the type of the running membership and the day it started", async () => {
    api.findOwnMemberships.mockResolvedValue({status: 200, data: [
      aMembership({id: 1, startDate: "2018-09-01", endDate: "2019-09-01"}),
      aMembership({
        id: 2, startDate: "2022-09-01", memberType: MemberType.ALUMNI,
        types: [{id: 20, type: MemberType.REGULAR, since: "2022-09-01"}, {id: 21, type: MemberType.ALUMNI, since: "2025-09-01"}],
      }),
    ]})
    const wrapper = mount(CurrentMembershipType)

    await vi.waitFor(() => expect(wrapper.get('[data-testid="current-membership-type"]').text()).toBe("Alumni since 1 Sep 2025"))
  })

  it("names a regular or honorary member as such", async () => {
    api.findOwnMemberships.mockResolvedValue({status: 200, data: [aMembership({startDate: "2024-09-01"})]})
    const regular = mount(CurrentMembershipType)
    await vi.waitFor(() => expect(regular.text()).toBe("Regular member since 1 Sep 2024"))

    api.findOwnMemberships.mockResolvedValue({status: 200, data: [aMembership({startDate: "2024-09-01", memberType: MemberType.HONORARY})]})
    const honorary = mount(CurrentMembershipType)
    await vi.waitFor(() => expect(honorary.text()).toBe("Honorary member since 1 Sep 2024"))
  })

  it("says nothing without a running membership", async () => {
    api.findOwnMemberships.mockResolvedValue({status: 200, data: [aMembership({endDate: "2025-01-01"})]})
    const wrapper = mount(CurrentMembershipType)
    await settle()

    expect(wrapper.find('[data-testid="current-membership-type"]').exists()).toBe(false)
  })
})
