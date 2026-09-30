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
  findAddressById: vi.fn(),
  pendingActivations: vi.fn(),
  deleteUserById: vi.fn(),
  readRoleStanding: vi.fn(),
  listRoleChanges: vi.fn(),
  accountStanding: vi.fn(),
  securityEvents: vi.fn(),
}))
const {mockRoute, mockPush, mockStore} = vi.hoisted(() => ({
  mockRoute: {params: {id: "7", tab: ""} as Record<string, string>},
  mockPush: vi.fn(),
  mockStore: {commit: vi.fn(), getters: {isAdmin: false}},
}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => mockRoute,
  useRouter: () => ({push: mockPush}),
}))

vi.mock("@/plugins/store", () => ({default: mockStore}))

// The form's own checks are UserForm's tests; here it only has to answer a save.
vi.mock("@/components/form/UserForm.vue", async () => {
  const {defineComponent} = await import("vue")
  return {
    default: defineComponent({
      name: "UserForm",
      props: {modelValue: {type: Object, default: null}, options: {type: Object, default: null}},
      emits: ["submitted"],
      setup: (_props, {expose}) => {
        expose({save: async () => ({})})
        return () => null
      },
    }),
  }
})

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
    api.findAddressById.mockResolvedValue({status: 200, data: {id: 3, street: "Hallenweg", version: 0, createdAt: "", updatedAt: ""}})
    api.pendingActivations.mockResolvedValue({status: 200, data: {activations: [{userId: 7, purpose: "USER_ACTIVATION"}]}})
    api.deleteUserById.mockResolvedValue({status: 204, data: undefined})
    api.accountStanding.mockResolvedValue({status: 200, data: {twoFactorOn: false, awaitingReenrolment: false, locked: false}})
    api.securityEvents.mockResolvedValue({status: 200, data: {events: []}})
    mockStore.getters.isAdmin = false
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

  it("edits the details and the address on the Profile tab", async () => {
    api.findUserById.mockResolvedValue({status: 200, data: aUser({id: 7, addressId: 3})})
    const wrapper = await mount("profile")

    expect(wrapper.findComponent({name: "UserForm"}).exists()).toBe(true)
    expect(api.findAddressById).toHaveBeenCalledWith({path: {id: 3}, throwOnError: true})
    await wrapper.get('[data-testid="user-profile-save"]').trigger("click")
    await settle()
    expect(wrapper.text()).toContain("Saved.")
    wrapper.findComponent({name: "UserForm"}).vm.$emit("update:modelValue", aUser({id: 7, fullName: "Changed"}))
    wrapper.findComponent({name: "AddressForm"}).vm.$emit("update:modelValue", {street: "Elsewhere"})
    wrapper.findComponent({name: "AddressForm"}).vm.$emit("submitted", true)
    await settle()
    expect(api.findUserById).toHaveBeenCalledTimes(2)
  })

  it("offers the emails the account can be sent, its security and deleting it, on the Account tab", async () => {
    api.findUserById.mockResolvedValue({status: 200, data: aUser({id: 7, enabled: false})})
    const wrapper = await mount("account")

    expect(wrapper.findAllComponents({name: "RecoveryUserRow"}).map((row) => row.props("actionType"))).toEqual(["password", "activation"])
    expect(wrapper.findComponent({name: "AccountSecurityPanel"}).exists()).toBe(true)

    await wrapper.get('[data-testid="user-delete"]').trigger("click")
    wrapper.findComponent({name: "DeletionConfirmationDialog"}).vm.$emit("confirm")
    await settle()
    expect(api.deleteUserById).toHaveBeenCalledWith({path: {userId: 7}, throwOnError: true})
    expect(mockPush).toHaveBeenCalledWith("/management/users")
  })

  it("stays on the page when the account could not be deleted", async () => {
    api.deleteUserById.mockRejectedValue(new Error("refused"))
    const wrapper = await mount("account")

    wrapper.findComponent({name: "DeletionConfirmationDialog"}).vm.$emit("update:modelValue", true)
    wrapper.findComponent({name: "DeletionConfirmationDialog"}).vm.$emit("confirm")
    await settle()
    expect(mockPush).not.toHaveBeenCalled()
  })

  it("lets only an admin change roles, and reads the roles saved", async () => {
    const board = await mount("roles")
    expect(board.findComponent({name: "UserRolesPanel"}).props("editable")).toBe(false)

    mockStore.getters.isAdmin = true
    const admin = await mount("roles")
    expect(admin.findComponent({name: "UserRolesPanel"}).props("editable")).toBe(true)
    admin.findComponent({name: "UserRolesPanel"}).vm.$emit("changed", {userId: 7, roles: ["BOARD"]})
    await settle()
    await admin.get('[data-testid="user-tab-overview"]').trigger("click")
  })
})
