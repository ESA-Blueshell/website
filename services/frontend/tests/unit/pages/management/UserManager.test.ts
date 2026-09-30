import {beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import UserManager from "@/pages/management/UserManager.vue"
import {
  deleteUserById,
  findAllAddresses,
  findCommittees,
  findMemberships,
  findUserById,
  findUsers,
  MemberType,
  type MembershipResponse,
  type UserDetailResponse,
} from "@/services/api"
import {answer, emptyAnswer, refusal} from "../../helpers/sdkAnswers"
import {aCommittee} from "../../helpers/apiFixtures"
import {boardLogin, mountPage} from "../../helpers/mountPage"
import type {StoredLogin} from "@/plugins/store"
import {settle} from "../helpers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findUsers: vi.fn(),
  findUserById: vi.fn(),
  findMemberships: vi.fn(),
  findAllAddresses: vi.fn(),
  findCommittees: vi.fn(),
  deleteUserById: vi.fn(),
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

/** Opens a row's actions and chooses one; the menu is a portal, so its items are found as components. */
async function act(wrapper: VueWrapper<any>, id: number, testid: string) {
  const find = () => wrapper.findAllComponents({name: "DropdownMenuItem"}).find((one) => one.attributes("data-testid") === `${testid}-${id}`)
  // A select emitted by hand leaves the menu open, so the next choice finds it still there.
  if (!find()) {
    await wrapper.get(`[data-testid="member-manager-actions-${id}"]`).trigger("click")
    await settle()
  }
  const item = find()
  if (!item) throw new Error(`no ${testid} for ${id}`)
  item.vm.$emit("select", new Event("select"))
  await settle()
}

describe("the Users page", () => {
  beforeEach(() => {
    vi.mocked(findUsers).mockResolvedValue(answer(findUsers, {content: [zoe, bob, carol]}))
    vi.mocked(findUserById).mockResolvedValue(answer(findUserById, zoe))
    vi.mocked(findMemberships).mockResolvedValue(answer(findMemberships, memberships))
    vi.mocked(findAllAddresses).mockResolvedValue(answer(findAllAddresses, [
      {id: 5, street: "Hallenweg", houseNumber: "5", city: "Enschede", userId: 1, version: 0, createdAt: "", updatedAt: ""},
    ]))
    vi.mocked(findCommittees).mockResolvedValue(answer(findCommittees, [
      aCommittee({id: 4, name: "Sitecie", members: [{committeeId: 4, userId: 3, createdAt: "", updatedAt: "", version: 0}]}),
    ]))
    vi.mocked(deleteUserById).mockResolvedValue(emptyAnswer(deleteUserById))
  })

  it("lists everyone by name, says where each stands and why they need a look", async () => {
    const wrapper = await mount()

    expect(rowIds(wrapper)).toEqual([2, 3, 1])
    expect(wrapper.get('[data-testid="member-manager-status-1"]').text()).toContain("Member")
    expect(wrapper.get('[data-testid="member-manager-status-1"]').text()).toContain("alumni")
    expect(wrapper.get('[data-testid="member-manager-status-2"]').text()).toContain("Former member")
    expect(wrapper.get('[data-testid="member-manager-status-3"]').text()).toContain("Never a member")
    expect(wrapper.get('[data-testid="member-manager-needs-2"]').text()).toContain("Locked")
    expect(wrapper.get('[data-testid="member-manager-needs-3"]').text()).toContain("Role waits on two-factor")
    expect(wrapper.get('[data-testid="member-manager-count"]').text()).toBe("3 people")
  })

  it("finds Zoë by typing zoe, and people by their address, committee or role", async () => {
    const wrapper = await mount()
    const search = wrapper.get('[data-testid="member-manager-search-input"]')

    await search.setValue("zoe")
    expect(rowIds(wrapper)).toEqual([1])
    expect(wrapper.get('[data-testid="member-manager-count"]').text()).toBe("1 of 3 people")
    await search.setValue("hallenweg")
    expect(rowIds(wrapper)).toEqual([1])
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
    await wrapper.get('[data-testid="member-manager-header-member-since"]').trigger("click")
    await settle()
    expect(rowIds(wrapper)).toEqual([2, 1, 3])
  })

  it("starts or ends the membership of the people selected", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="member-manager-checkbox-2"]').trigger("change")
    await settle()
    expect(wrapper.get('[data-testid="member-manager-selection"]').text()).toContain("1 selected")
    await wrapper.get('[data-testid="bulk-action-start-membership"]').trigger("click")
    await settle()
    expect(wrapper.findComponent({name: "MembershipStatusDialog"}).exists()).toBe(true)
    wrapper.findComponent({name: "MembershipStatusDialog"}).vm.$emit("update:modelValue", false)
    await settle()
    expect(wrapper.findComponent({name: "MembershipStatusDialog"}).exists()).toBe(false)

    await wrapper.get('[data-testid="bulk-action-end-membership"]').trigger("click")
    await settle()
    wrapper.findComponent({name: "MembershipStatusDialog"}).vm.$emit("done")
    await settle()
    expect(wrapper.find('[data-testid="member-manager-selection"]').exists()).toBe(false)
  })

  it("reads the account afresh before offering to edit it", async () => {
    const wrapper = await mount()

    await act(wrapper, 1, "member-manager-edit-profile-btn")

    expect(findUserById).toHaveBeenCalledWith({path: {userId: 1}})
    expect(wrapper.find('[data-testid="member-manager-edit-profile-dialog"]').exists()).toBe(true)
  })

  it("opens a member's memberships, account security and, for an admin, roles", async () => {
    const wrapper = await mount(adminLogin)

    await act(wrapper, 1, "member-manager-manage-membership-btn")
    expect(wrapper.find('[data-testid="manage-membership-dialog"]').exists()).toBe(true)
    await act(wrapper, 1, "member-manager-account-security-btn")
    expect(wrapper.findComponent({name: "AccountSecurityDialog"}).exists()).toBe(true)
    await act(wrapper, 1, "member-manager-edit-roles-btn")
    expect(wrapper.findComponent({name: "UserRolesDialog"}).exists()).toBe(true)
    wrapper.findComponent({name: "UserRolesDialog"}).vm.$emit("changed", {userId: 1, roles: ["ADMIN"]})
    await settle()
    expect(wrapper.get('[data-testid="member-manager-needs-1"]').text()).toContain("Role waits on two-factor")
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

  it("deletes an account once the deletion is confirmed, and takes its row off the list", async () => {
    const wrapper = await mount()

    await act(wrapper, 2, "member-manager-delete-btn")
    await wrapper.get('[data-testid="deletion-confirmation-confirm-btn"]').trigger("click")
    await settle()

    expect(deleteUserById).toHaveBeenCalledWith({path: {userId: 2}, throwOnError: true})
    expect(rowIds(wrapper)).toEqual([3, 1])
  })

  it("keeps the account on the list when the api refuses to delete it", async () => {
    vi.mocked(deleteUserById).mockRejectedValue(refusal(deleteUserById, null, 409))
    const wrapper = await mount()

    await act(wrapper, 2, "member-manager-delete-btn")
    await wrapper.get('[data-testid="deletion-confirmation-confirm-btn"]').trigger("click")
    await settle()

    expect(rowIds(wrapper)).toEqual([2, 3, 1])
  })

  it("says so when nobody matches, and when the people could not be read", async () => {
    vi.mocked(findUsers).mockRejectedValue(new Error("offline"))
    const wrapper = await mount()

    expect(wrapper.find('[data-testid="member-manager-empty"]').exists()).toBe(true)
  })
})
