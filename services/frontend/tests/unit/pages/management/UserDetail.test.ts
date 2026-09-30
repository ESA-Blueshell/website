import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import UserDetail from "@/pages/management/UserDetail.vue"
import {BulkFeeType, ContributionEmailKind, MemberType} from "@/services/api"
import {aMembership, aUser} from "../../helpers/apiFixtures"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  findUserById: vi.fn(),
  findMemberships: vi.fn(),
  findMemberContributions: vi.fn(),
  createContribution: vi.fn(),
  deleteContribution: vi.fn(),
}))
const {mockRoute} = vi.hoisted(() => ({mockRoute: {params: {id: "7", tab: ""} as Record<string, string>}}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => mockRoute,
}))

vi.mock("vuex", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vuex")>()),
  useStore: () => ({getters: {isAdmin: false}}),
}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const period = (periodId: number, paid: boolean) => ({
  periodId, startDate: `202${periodId}-09-01`, endDate: `202${periodId + 1}-08-31`,
  feeType: BulkFeeType.FULL_YEAR_FEE, fee: 30, paid, paidAt: paid ? "2025-10-01T10:00:00Z" : null,
  lastEmailAt: paid ? null : "2025-09-20T10:00:00Z", lastEmailKind: paid ? null : ContributionEmailKind.REMINDER,
})

describe("one user's page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async (tab = "") => {
    mockRoute.params = {id: "7", tab}
    const wrapper = mountInApp(UserDetail)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.findUserById.mockResolvedValue({status: 200, data: aUser({id: 7, fullName: "Ann Vos", username: "ann", phoneNumber: "0612", discordId: "1", discord: "annv", roles: ["MEMBER"]})})
    api.findMemberships.mockResolvedValue({status: 200, data: [aMembership({id: 3, userId: 7, startDate: "2023-09-01", endDate: null, incasso: true, memberType: MemberType.REGULAR})]})
    api.findMemberContributions.mockResolvedValue({status: 200, data: [period(5, false), period(4, true)]})
    api.createContribution.mockResolvedValue({status: 201, data: {}})
    api.deleteContribution.mockResolvedValue({status: 204, data: undefined})
  })

  afterEach(() => {
    unmountAll(wrappers, "UserDetail")
  })

  it("sums up membership, contributions, profile, account and roles on the overview", async () => {
    const wrapper = await mount()

    const overview = wrapper.get('[data-testid="user-overview"]').text()
    expect(overview).toContain("Member since 2023-09-01")
    expect(overview).toContain("Pays by incasso")
    expect(overview).toContain("not paid")
    expect(overview).toContain("Discord: annv")
    expect(overview).toContain("No two-factor")
    expect(wrapper.get('[data-testid="user-tab-overview"]').attributes("aria-current")).toBe("page")
    expect(wrapper.get('[data-testid="user-tab-membership"]').attributes("to")).toBe("/management/users/7/membership")
  })

  it("opens the tab its address names", async () => {
    const wrapper = await mount("membership")

    expect(wrapper.get('[data-testid="user-incasso"]').text()).toBe("Pays by incasso")
    expect(wrapper.findComponent({name: "MembershipPanel"}).exists()).toBe(true)
    wrapper.findComponent({name: "MembershipPanel"}).vm.$emit("changed")
    await settle()
    expect(api.findMemberContributions).toHaveBeenCalledTimes(2)
  })

  it("records or withdraws a payment, and says so on the row", async () => {
    const wrapper = await mount("contributions")

    expect(wrapper.get('[data-testid="user-period-5"]').text()).toContain("Contribution reminder")
    expect(wrapper.get('[data-testid="user-period-4"]').text()).toContain("Paid on")
    await wrapper.get('[data-testid="user-period-toggle-5"]').trigger("click")
    await settle()
    expect(api.createContribution).toHaveBeenCalledWith({body: {userId: 7, contributionPeriodId: 5}})
    expect(wrapper.get('[data-testid="user-period-said-5"]').text()).toBe("Payment recorded.")

    await wrapper.get('[data-testid="user-period-toggle-4"]').trigger("click")
    await settle()
    expect(api.deleteContribution).toHaveBeenCalledWith({path: {userId: 7, contributionPeriodId: 4}})
    expect(wrapper.get('[data-testid="user-period-said-4"]').text()).toBe("Payment withdrawn.")
  })

  it("says why a payment could not be recorded", async () => {
    api.createContribution.mockResolvedValue({status: 409, error: {detail: "Already paid."}})
    const wrapper = await mount("contributions")

    await wrapper.get('[data-testid="user-period-toggle-5"]').trigger("click")
    await settle()

    expect(wrapper.get('[data-testid="user-period-said-5"]').text()).toBe("Already paid.")
  })

  it("reads a person who was never a member, and an honorary one, plainly", async () => {
    api.findMemberships.mockResolvedValue({status: 200, data: []})
    api.findMemberContributions.mockResolvedValue({status: 200, data: [{...period(5, false), feeType: null, fee: null, lastEmailAt: null, lastEmailKind: null}]})
    api.findUserById.mockResolvedValue({status: 200, data: aUser({id: 7, discordId: null, addressId: 3, locked: true, twoFactorOn: true, awaitingReenrolment: true})})
    const overview = (await mount()).get('[data-testid="user-overview"]').text()
    expect(overview).toContain("Never a member")
    expect(overview).toContain("No Discord linked")
    expect(overview).toContain("Locked")
    expect(overview).toContain("Waiting to set up again")

    const contributions = await mount("contributions")
    expect(contributions.get('[data-testid="user-period-5"]').text()).toContain("Owes nothing")
    expect(contributions.get('[data-testid="user-period-5"]').text()).toContain("No payment email yet")
  })

  it("says there is nobody when the person cannot be read", async () => {
    api.findUserById.mockResolvedValue({status: 404, error: {detail: "Not found"}})
    api.findMemberContributions.mockResolvedValue({status: 200, data: []})
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="user-detail-missing"]').text()).toBe("There is nobody with number 7.")
  })
})
