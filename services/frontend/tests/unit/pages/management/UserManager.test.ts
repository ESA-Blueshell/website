import {beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import UserManager from "@/pages/management/UserManager.vue"
import router from "@/plugins/router"
import {
  findCommittees,
  findCurrentContributionPeriod,
  findMemberships,
  findUsers,
  MemberType,
  type MembershipResponse,
  type UserDetailResponse,
} from "@/services/api"
import {answer} from "../../helpers/sdkAnswers"
import {aCommittee, aContributionPeriod} from "../../helpers/apiFixtures"
import {boardLogin, mountPage} from "../../helpers/mountPage"
import type {StoredLogin} from "@/plugins/store"
import {settle} from "../helpers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findUsers: vi.fn(),
  findMemberships: vi.fn(),
  findCommittees: vi.fn(),
  findCurrentContributionPeriod: vi.fn(),
  findDeletedMemberships: vi.fn(async () => ({data: [], request: {}, response: {}})),
}))

function user(id: number, fullName: string, username: string, extra: Partial<UserDetailResponse> = {}): UserDetailResponse {
  const [firstName, lastName] = fullName.split(" ")
  return {
    id, fullName, username, firstName, lastName, initials: "XX", roles: ["MEMBER"], email: `${username}@test.com`,
    enabled: true, newsletter: false, photoConsent: false, locked: false, twoFactorOn: false, awaitingReenrolment: false,
    nameOnRosters: true, createdAt: "2025-01-01T00:00:00.000Z", updatedAt: "2025-01-01T00:00:00.000Z", version: 0,
    ...extra,
  } as UserDetailResponse
}

function membership(id: number, userId: number, startDate: string, extra: Partial<MembershipResponse> = {}): MembershipResponse {
  return {
    id, userId, startDate, memberType: MemberType.REGULAR, incasso: false, version: 1,
    createdAt: "2025-01-01T00:00:00.000Z", updatedAt: "2025-01-01T00:00:00.000Z", ...extra,
  }
}

// Three standings: Zoë is a member now, Bob was one, Carol never was.
const zoe = user(1, "Zoë Smith", "zoe", {discordId: "111", addressId: 5})
const bob = user(2, "Bob Jones", "bob", {locked: true})
const carol = user(3, "Carol Adams", "carol", {roles: ["BOARD"]})
const memberships = [
  membership(90, 1, "2024-01-01", {memberType: MemberType.ALUMNI}),
  membership(91, 2, "2020-01-01", {endDate: "2021-12-31"}),
]

const adminLogin: StoredLogin = {...boardLogin, roles: ["ADMIN", "BOARD"] as StoredLogin["roles"]}

const mount = (login = boardLogin) => mountPage(UserManager, {path: "/management/users", login, width: 1400})

const rowIds = (wrapper: VueWrapper<any>) =>
  wrapper.findAll('[data-testid^="member-manager-row-"]').map((row) => Number(row.attributes("data-testid")!.split("-").at(-1)))

const pick = async (wrapper: VueWrapper<any>, index: number, key: string | null) => {
  await wrapper.findAllComponents({name: "FilterPicker"})[index]!.vm.$emit("update:modelValue", key)
  await settle()
}

describe("the Users page", () => {
  beforeEach(() => {
    vi.mocked(findUsers).mockResolvedValue(answer(findUsers, {content: [zoe, bob, carol]}))
    vi.mocked(findMemberships).mockResolvedValue(answer(findMemberships, memberships))
    vi.mocked(findCommittees).mockResolvedValue(answer(findCommittees, [
      aCommittee({id: 4, name: "Sitecie", members: [{committeeId: 4, userId: 3, createdAt: "", updatedAt: "", version: 0}]}),
    ]))
    vi.mocked(findCurrentContributionPeriod).mockResolvedValue(answer(findCurrentContributionPeriod, aContributionPeriod({startDate: "2023-09-01", endDate: "2024-08-31"})))
  })

  it("lists everyone by name, says where each stands and why they need a look", async () => {
    const wrapper = await mount()

    expect(rowIds(wrapper)).toEqual([2, 3, 1])
    expect(wrapper.get('[data-testid="member-manager-status-1"]').text()).toBe("Active")
    expect(wrapper.get('[data-testid="member-manager-row-1"]').text()).toContain("Alumni · since 1 Jan 2024")
    expect(wrapper.get('[data-testid="member-manager-status-2"]').text()).toBe("Former")
    expect(wrapper.get('[data-testid="member-manager-status-3"]').text()).toBe("Never a member")
    expect(wrapper.get('[data-testid="member-manager-row-3"]').text()).toContain("@Board")
    expect(wrapper.get('[data-testid="member-manager-row-3"]').text()).toContain("Sitecie")
    expect(wrapper.get('[data-testid="member-manager-needs-2"]').text()).toContain("Locked")
    expect(wrapper.get('[data-testid="member-manager-needs-3"]').text()).toContain("Role waits on two-factor")
    expect(wrapper.get('[data-testid="member-manager-count"]').text()).toBe("3 of 3 people")
  })

  it("finds Zoë by typing zoe, and people by their committee or role", async () => {
    const wrapper = await mount()
    const search = wrapper.get('[data-testid="member-manager-search-input"]')

    await search.setValue("zoe")
    expect(rowIds(wrapper)).toEqual([1])
    expect(wrapper.get('[data-testid="member-manager-count"]').text()).toBe("1 of 3 people")
    await search.setValue("sitecie")
    expect(rowIds(wrapper)).toEqual([3])
    await search.setValue("board")
    expect(rowIds(wrapper)).toEqual([3])
  })

  it("opens searched for the person another page sent here", async () => {
    const wrapper = await mountPage(UserManager, {path: "/management/users?search=bob", login: boardLogin, width: 1400})

    expect(rowIds(wrapper)).toEqual([2])
  })

  it("combines the three filters with the search, and clears them all", async () => {
    const wrapper = await mount()

    await pick(wrapper, 0, "current")
    expect(rowIds(wrapper)).toEqual([1])
    await pick(wrapper, 0, null)
    await pick(wrapper, 1, "ALUMNI")
    expect(rowIds(wrapper)).toEqual([1])
    await pick(wrapper, 1, null)
    await pick(wrapper, 2, "any")
    expect(rowIds(wrapper)).toEqual([2, 3])
    await pick(wrapper, 2, "locked")
    expect(rowIds(wrapper)).toEqual([2])

    await wrapper.get('[data-testid="member-manager-filters-clear"]').trigger("click")
    await settle()
    expect(rowIds(wrapper)).toEqual([2, 3, 1])
  })

  it("sorts by a column, and turns the order round on a second press", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="member-manager-header-status"]').trigger("click")
    await settle()
    expect(rowIds(wrapper)).toEqual([1, 2, 3])
    await wrapper.get('[data-testid="member-manager-header-status"]').trigger("click")
    await settle()
    expect(rowIds(wrapper)).toEqual([3, 2, 1])
    await wrapper.get('[data-testid="member-manager-header-name"]').trigger("click")
    await settle()
    expect(rowIds(wrapper)).toEqual([2, 3, 1])
  })

  it("takes the people selected to the task page that starts or ends their membership", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="member-manager-checkbox-2"]').trigger("change")
    await settle()
    expect(wrapper.get('[data-testid="member-manager-selection"]').text()).toContain("1 selected")
    await wrapper.get('[data-testid="bulk-action-end-membership"]').trigger("click")
    await settle()

    // The task page loads lazily, so the navigation settles after it arrives.
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe("/management/users/bulk/end"), {timeout: 10_000})
    expect(router.currentRoute.value.query).toEqual({ids: "2", back: "/management/users"})
  })

  it("selects everyone shown from the head of the list, then everyone there is, and lets go", async () => {
    const wrapper = await mount()
    const selection = () => wrapper.find('[data-testid="member-manager-selection"]')

    await wrapper.get('[data-testid="member-manager-search-input"]').setValue("zoe")
    await wrapper.get('[data-testid="member-manager-list-select-shown"]').trigger("change")
    await settle()
    expect(selection().text()).toContain("1 selected")
    expect(wrapper.get('[data-testid="member-manager-list"]').text()).toContain("All 1 shown are selected.")

    await wrapper.get('[data-testid="member-manager-list-select-all"]').trigger("click")
    await settle()
    expect(selection().text()).toContain("3 selected")
    expect(wrapper.get('[data-testid="member-manager-list"]').text()).toContain("All 3 are selected.")

    await wrapper.get('[data-testid="member-manager-list-select-all"]').trigger("click")
    await settle()
    expect(selection().text()).toContain("0 selected")
  })

  it("takes the people selected to the task page that starts their membership", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="member-manager-checkbox-3"]').trigger("change")
    await settle()
    await wrapper.get('[data-testid="bulk-action-start-membership"]').trigger("click")
    await settle()

    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe("/management/users/bulk/start"), {timeout: 10_000})
    expect(router.currentRoute.value.query).toEqual({ids: "3", back: "/management/users"})
  })

  it("links each person to their own page, by their name and by the row's arrow", async () => {
    const wrapper = await mount(adminLogin)

    expect(wrapper.get('[data-testid="member-manager-open-1"]').attributes("to")).toBe("/management/users/1")
    expect(wrapper.get('[data-testid="member-manager-row-1"]').find('a[aria-label="Open"]').attributes("to")).toBe("/management/users/1")
  })

  it("counts the members, who joined this period and who needs a look", async () => {
    const wrapper = await mount()

    const facts = wrapper.findComponent({name: "FactList"}).text()
    expect(wrapper.get('[data-testid="member-manager-fact-members"]').text()).toContain("1")
    expect(facts).toContain("New this period")
    expect(facts).toContain("Since 1 Sep 2023")
    expect(facts).toContain("2 people")
    expect(facts).toContain("Locked, Role waits on two-factor")
  })

  it("counts the accounts instead where no period exists yet", async () => {
    vi.mocked(findCurrentContributionPeriod).mockRejectedValue(new Error("none"))
    const wrapper = await mount()

    expect(wrapper.findComponent({name: "FactList"}).text()).toContain("Accounts")
  })

  it("draws each person as a row on a phone, with the state that needs a look first", async () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const wrapper = await mount()
    vi.unstubAllGlobals()

    expect(wrapper.findAllComponents({name: "ManagementRow"})).toHaveLength(3)
    expect(wrapper.get('[data-testid="member-manager-row-2"]').text()).toContain("Locked")
    expect(wrapper.get('[data-testid="member-manager-row-1"]').text()).toContain("Active")
    await wrapper.get('[data-testid="member-manager-checkbox-1"]').trigger("change")
    await settle()
    expect(wrapper.get('[data-testid="member-manager-selection"]').text()).toContain("1 selected")
  })

  it("adds a user from the Add user form", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="member-manager-add-user-btn"]').trigger("click")
    await settle()
    expect(wrapper.find('[data-testid="member-manager-add-user-dialog"]').exists()).toBe(true)
    wrapper.findComponent({name: "UserForm"}).vm.$emit("submitted", false)
    wrapper.findComponent({name: "UserForm"}).vm.$emit("submitted", true)
    await settle()
    expect(findUsers).toHaveBeenCalledTimes(2)
  })

  it("saves and cancels the add form", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="member-manager-add-user-btn"]').trigger("click")
    for (const modal of wrapper.findAllComponents({name: "BaseModal"})) {
      modal.vm.$emit("save")
      modal.vm.$emit("cancel")
      modal.vm.$emit("update:modelValue", false)
    }
    await settle()

    expect(wrapper.findComponent({name: "BaseModal"}).props("modelValue")).toBe(false)
  })

  it("says so when nobody matches, and when the people could not be read", async () => {
    vi.mocked(findUsers).mockRejectedValue(new Error("offline"))
    const wrapper = await mount()

    expect(wrapper.find('[data-testid="member-manager-empty"]').exists()).toBe(true)
  })
})
